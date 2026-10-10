import os
from datetime import datetime, timedelta, timezone

import jwt
from fastapi import Depends, FastAPI, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, Field
from pwdlib import PasswordHash
from sqlalchemy import DateTime, ForeignKey, Integer, String, Text, create_engine, select
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker

DATABASE_URL = os.getenv("DATABASE_URL", "postgresql+psycopg://ndrimeda:local-dev-change-me@db:5432/ndrimeda")
# Neon and many managed PostgreSQL providers supply postgresql:// URLs.
# Force the installed psycopg 3 driver instead of SQLAlchemy's psycopg2 default.
if DATABASE_URL.startswith("postgres://"):
    DATABASE_URL = "postgresql+psycopg://" + DATABASE_URL[len("postgres://"):]
elif DATABASE_URL.startswith("postgresql://"):
    DATABASE_URL = "postgresql+psycopg://" + DATABASE_URL[len("postgresql://"):]
JWT_SECRET = os.getenv("JWT_SECRET", "dev-only-change-this-secret-before-deployment")
engine = create_engine(DATABASE_URL, pool_pre_ping=True)
SessionLocal = sessionmaker(bind=engine, expire_on_commit=False)
hasher = PasswordHash.recommended()
bearer = HTTPBearer(auto_error=False)
app = FastAPI(title="NDRIMEDA API", version="0.1.0")

class Base(DeclarativeBase):
    pass

class User(Base):
    __tablename__ = "users"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    username: Mapped[str] = mapped_column(String(32), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(255))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc))

class Message(Base):
    __tablename__ = "messages"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    text: Mapped[str] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), index=True)

Base.metadata.create_all(bind=engine)

class Credentials(BaseModel):
    username: str = Field(min_length=3, max_length=32, pattern=r"^[A-Za-z0-9_.-]+$")
    password: str = Field(min_length=8, max_length=128)

class MessageIn(BaseModel):
    text: str = Field(min_length=1, max_length=4000)

def get_db():
    with SessionLocal() as db:
        yield db

def issue_token(user: User):
    now = datetime.now(timezone.utc)
    token = jwt.encode({"sub": str(user.id), "exp": now + timedelta(days=7)}, JWT_SECRET, algorithm="HS256")
    return {"access_token": token, "token_type": "bearer", "user": {"id": user.id, "username": user.username}}

def signed_in(credentials: HTTPAuthorizationCredentials | None = Depends(bearer), db: Session = Depends(get_db)):
    if not credentials:
        raise HTTPException(status_code=401, detail="Bearer token required")
    try:
        data = jwt.decode(credentials.credentials, JWT_SECRET, algorithms=["HS256"])
        user = db.get(User, int(data["sub"]))
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid or expired token")
    if user is None:
        raise HTTPException(status_code=401, detail="Account not found")
    return user

@app.get("/health")
def health():
    return {"status": "ok", "service": "ndrimeda-api"}

@app.post("/auth/register", status_code=201)
def register(data: Credentials, db: Session = Depends(get_db)):
    if db.scalar(select(User).where(User.username == data.username)):
        raise HTTPException(status_code=409, detail="Username is already taken")
    user = User(username=data.username, password_hash=hasher.hash(data.password))
    db.add(user)
    try:
        db.commit()
        db.refresh(user)
    except Exception:
        db.rollback()
        raise HTTPException(status_code=409, detail="Could not create account")
    return issue_token(user)

@app.post("/auth/login")
def login(data: Credentials, db: Session = Depends(get_db)):
    user = db.scalar(select(User).where(User.username == data.username))
    if user is None or not hasher.verify(data.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Incorrect username or password")
    return issue_token(user)

@app.get("/messages")
def list_messages(limit: int = 50, user: User = Depends(signed_in), db: Session = Depends(get_db)):
    limit = max(1, min(limit, 100))
    rows = db.execute(select(Message, User.username).join(User, Message.user_id == User.id).order_by(Message.id.desc()).limit(limit)).all()
    rows.reverse()
    return [{"id": m.id, "username": name, "text": m.text, "created_at": m.created_at.isoformat()} for m, name in rows]

@app.post("/messages", status_code=201)
def send_message(data: MessageIn, user: User = Depends(signed_in), db: Session = Depends(get_db)):
    body = data.text.strip()
    if not body:
        raise HTTPException(status_code=422, detail="Message cannot be empty")
    item = Message(user_id=user.id, text=body)
    db.add(item)
    db.commit()
    db.refresh(item)
    return {"id": item.id, "username": user.username, "text": item.text, "created_at": item.created_at.isoformat()}
