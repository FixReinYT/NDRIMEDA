from fastapi import FastAPI
app = FastAPI(title="NDRIMEDA API")
@app.get("/health")
def health():
    return {"status": "ok"}
