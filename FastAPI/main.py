from contextlib import asynccontextmanager

import py_eureka_client.eureka_client as eureka_client
from fastapi import Depends, FastAPI

import runs.models
import workflows.models  # noqa: F401  테이블 등록용
from core.auth import get_current_user
from core.config import settings
from core.database import Base, engine
from engine.checkpointer import close_checkpointer, init_checkpointer
from core.exceptions import register_exception_handlers
from nodes.routers import router as nodes_router
from runs.routers import router as runs_router
from workflows.routers import router as workflows_router
from ai.routers import router as ai_router

API_PREFIX = "/api/v1/ai"
SERVICE_NAME = "ai-service"


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 서버 켤 때
    Base.metadata.create_all(engine)
    init_checkpointer()
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
    # 서버 끌 때
    close_checkpointer()
    if settings.eureka_server_url:
        await eureka_client.stop_async()


app = FastAPI(title="업무 자동화", lifespan=lifespan)

register_exception_handlers(app)

# /api/v1/ai/** 는 전부 로그인 필요 (/health 는 헬스체크용으로 열어둠)
AUTH = [Depends(get_current_user)]
app.include_router(nodes_router, prefix=API_PREFIX, dependencies=AUTH)
app.include_router(workflows_router, prefix=API_PREFIX, dependencies=AUTH)
app.include_router(runs_router, prefix=API_PREFIX, dependencies=AUTH)

# 개발용 LLM 테스트 API는 설정으로 켰을 때만 등록
if settings.llm_test_api_enabled:
    app.include_router(ai_router, prefix=API_PREFIX, dependencies=AUTH)

@app.get("/health")
def health():
    return {"status": "ok"}