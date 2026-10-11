import os
import json
import hmac
import hashlib
import httpx
from datetime import datetime, timedelta, timezone

import jwt
from fastapi import Depends, FastAPI, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, Field
from pwdlib import PasswordHash
from sqlalchemy import DateTime, ForeignKey, Integer, String, Text, create_engine, select, text
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker

DATABASE_URL = os.getenv("DATABASE_URL", "postgresql+psycopg://ndrimeda:local-dev-change-me@db:5432/ndrimeda")
if DATABASE_URL.startswith("postgres://"):
    DATABASE_URL = "postgresql+psycopg://" + DATABASE_URL[len("postgres://"):]
elif DATABASE_URL.startswith("postgresql://"):
    DATABASE_URL = "postgresql+psycopg://" + DATABASE_URL[len("postgresql://"):]
JWT_SECRET = os.getenv("JWT_SECRET", "dev-only-change-this-secret-before-deployment")
engine = create_engine(DATABASE_URL, pool_pre_ping=True)
SessionLocal = sessionmaker(bind=engine, expire_on_commit=False)
hasher = PasswordHash.recommended()
bearer = HTTPBearer(auto_error=False)
app = FastAPI(title="NDRIMEDA Relay API", version="0.2.0")

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
    recipient_username: Mapped[str | None] = mapped_column(String(300), nullable=True, index=True)
    sender_label: Mapped[str | None] = mapped_column(String(300), nullable=True)
    text: Mapped[str] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), index=True)

Base.metadata.create_all(bind=engine)
# Upgrade databases created by older beta versions without deleting existing messages.
with engine.begin() as connection:
    connection.execute(text("ALTER TABLE messages ADD COLUMN IF NOT EXISTS recipient_username VARCHAR(300)"))
    connection.execute(text("ALTER TABLE messages ALTER COLUMN recipient_username TYPE VARCHAR(300)"))
    connection.execute(text("ALTER TABLE messages ADD COLUMN IF NOT EXISTS sender_label VARCHAR(300)"))
    connection.execute(text("CREATE INDEX IF NOT EXISTS ix_messages_recipient_username ON messages (recipient_username)"))

class Credentials(BaseModel):
    username: str = Field(min_length=3, max_length=32, pattern=r"^[A-Za-z0-9_.-]+$")
    password: str = Field(min_length=8, max_length=128)

class MessageIn(BaseModel):
    text: str = Field(min_length=1, max_length=4000)
    recipient_username: str | None = Field(default=None, min_length=3, max_length=300)

class FederatedMessage(BaseModel):
    source_node: str = Field(min_length=1, max_length=253)
    sender_username: str = Field(min_length=3, max_length=32, pattern=r"^[A-Za-z0-9_.-]+$")
    recipient_username: str = Field(min_length=3, max_length=32, pattern=r"^[A-Za-z0-9_.-]+$")
    text: str = Field(min_length=1, max_length=4000)
    signature: str = Field(min_length=64, max_length=64)

# JSON map of peer hostname -> pairwise shared secret.
def federation_peers() -> dict[str, str]:
    try:
        raw = json.loads(os.getenv("FEDERATION_PEERS", "{}"))
        return {str(k).lower().strip().rstrip("/"): str(v) for k, v in raw.items() if v}
    except Exception:
        return {}

def normalize_host(value: str) -> str:
    value = value.strip().lower().rstrip("/")
    if value.startswith("https://"):
        value = value[8:]
    return value

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
    return {"status": "ok", "service": "ndrimeda-relay", "features": ["account-auth", "direct-inbox"]}

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
    if user is None or not hasher.verify(user.password_hash, data.password):
        raise HTTPException(status_code=401, detail="Incorrect username or password")
    return issue_token(user)

@app.get("/messages")
def list_messages(limit: int = 50, user: User = Depends(signed_in), db: Session = Depends(get_db)):
    limit = max(1, min(limit, 100))
    rows = db.execute(select(Message, User.username).join(User, Message.user_id == User.id)
        .where(Message.user_id == user.id).order_by(Message.id.desc()).limit(limit)).all()
    rows.reverse()
    return [{"id": m.id, "username": name, "recipient": m.recipient_username,
             "text": m.text, "created_at": m.created_at.isoformat()} for m, name in rows]

@app.get("/inbox")
def inbox(limit: int = 100, user: User = Depends(signed_in), db: Session = Depends(get_db)):
    limit = max(1, min(limit, 100))
    rows = db.execute(select(Message, User.username).join(User, Message.user_id == User.id)
        .where(Message.recipient_username == user.username)
        .order_by(Message.id.desc()).limit(limit)).all()
    rows.reverse()
    return [{"id": m.id, "sender": m.sender_label or name, "recipient": user.username,
             "text": m.text, "created_at": m.created_at.isoformat()} for m, name in rows]

@app.post("/messages", status_code=201)
async def send_message(data: MessageIn, user: User = Depends(signed_in), db: Session = Depends(get_db)):
    body = data.text.strip()
    if not body:
        raise HTTPException(status_code=422, detail="Message cannot be empty")
    recipient = data.recipient_username.strip() if data.recipient_username else None
    if recipient and "@" in recipient:
        remote_user, remote_host = recipient.split("@", 1)
        remote_host = normalize_host(remote_host)
        if not remote_user or not remote_host or not all(c.isalnum() or c in "_.-" for c in remote_user):
            raise HTTPException(status_code=422, detail="Use address username@node.example")
        secret = federation_peers().get(remote_host)
        if not secret:
            raise HTTPException(status_code=400, detail="This node is not configured as a federation peer. Both nodes must trust each other.")
        source_node = normalize_host(os.getenv("NODE_HOST", ""))
        if not source_node:
            raise HTTPException(status_code=503, detail="NODE_HOST is not configured on this node")
        signature_data = f"{source_node}\n{user.username}\n{remote_user}\n{body}".encode()
        signature = hmac.new(secret.encode(), signature_data, hashlib.sha256).hexdigest()
        try:
            async with httpx.AsyncClient(timeout=12.0, follow_redirects=False) as client:
                response = await client.post(
                    f"https://{remote_host}/federation/inbox",
                    json={"source_node": source_node, "sender_username": user.username,
                          "recipient_username": remote_user, "text": body, "signature": signature},
                )
            if response.status_code not in (200, 201):
                detail = "Remote node rejected delivery"
                try: detail = response.json().get("detail", detail)
                except Exception: pass
                raise HTTPException(status_code=502, detail=detail)
        except HTTPException:
            raise
        except Exception:
            raise HTTPException(status_code=502, detail="Could not reach the recipient node")
        item = Message(user_id=user.id, recipient_username=recipient, text=body)
        db.add(item); db.commit(); db.refresh(item)
        return {"id": item.id, "username": user.username, "recipient": recipient,
                "text": item.text, "created_at": item.created_at.isoformat(), "federated": True}
    if recipient and recipient.casefold() == user.username.casefold():
        raise HTTPException(status_code=400, detail="Cannot send a direct message to yourself")
    if recipient and db.scalar(select(User).where(User.username == recipient)) is None:
        raise HTTPException(status_code=404, detail="Recipient account not found on this relay")
    item = Message(user_id=user.id, recipient_username=recipient, text=body)
    db.add(item)
    db.commit()
    db.refresh(item)
    return {"id": item.id, "username": user.username, "recipient": recipient,
            "text": item.text, "created_at": item.created_at.isoformat()}

 
@app.post("/federation/inbox", status_code=201)
def federation_inbox(data: FederatedMessage, db: Session = Depends(get_db)):
    source = normalize_host(data.source_node)
    secret = federation_peers().get(source)
    if not secret:
        raise HTTPException(status_code=403, detail="Source node is not trusted by this relay")
    expected = hmac.new(secret.encode(),
        f"{source}\n{data.sender_username}\n{data.recipient_username}\n{data.text.strip()}".encode(),
        hashlib.sha256).hexdigest()
    if not hmac.compare_digest(expected, data.signature):
        raise HTTPException(status_code=403, detail="Invalid federation signature")
    recipient = db.scalar(select(User).where(User.username == data.recipient_username))
    if recipient is None:
        raise HTTPException(status_code=404, detail="Recipient account not found on this relay")
    item = Message(user_id=recipient.id, recipient_username=recipient.username,
                   sender_label=f"{data.sender_username}@{source}", text=data.text.strip())
    db.add(item); db.commit(); db.refresh(item)
    return {"status": "delivered", "id": item.id}
