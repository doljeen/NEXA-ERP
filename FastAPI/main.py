from fastapi import FastAPI

from nodes.routers import router as nodes_router

app = FastAPI( title="업무 자동화")

app.include_router(nodes_router)

@app.get("/health")
def health():
    return {"status": "ok"}
