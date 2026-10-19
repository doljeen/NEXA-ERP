from fastapi import FastAPI

from sqlalchemy import text
from core.database import engine

with engine.connect() as conn:
    print(conn.execute(text("SELECT 1")).scalar())

# from nodes.routers import router as nodes_router

# app = FastAPI( title="업무 자동화")

# app.include_router(nodes_router)

# @app.get("/health")
# def health():
#     return {"status": "ok"}
