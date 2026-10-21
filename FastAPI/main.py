from contextlib import asynccontextmanager

import py_eureka_client.eureka_client as eureka_client

from fastapi import FastAPI

from core.config import settings
from core.exceptions import register_exception_handlers
from nodes.routers import router as nodes_router

API_PREFIX = "/api/v1/ai"
SERVICE_NAME = "ai-service"


async def lifespan(app: FastAPI):
    if settings.eureka_server_url:
        options = {}
        if settings.instance_host:
            options["instance_host"] = settings.instance_host
        await eureka_client.init_async(
            eureka_server=settings.eureka_server_url,
            app_name=SERVICE_NAME,
            instance_port=settings.instance_port,
            **options,
        )
    yield
    if settings.eureka_server_url:
        await eureka_client.stop_async()

app = FastAPI( title="업무 자동화")

register_exception_handlers(app)

app.include_router(nodes_router, prefix=API_PREFIX)

@app.get("/health")
def health():
    return {"status": "ok"}
