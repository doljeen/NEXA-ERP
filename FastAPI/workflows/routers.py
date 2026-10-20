from fastapi import FastAPI

from workflows import service

router = APIRouter(prefix="/api/v1/workflows", tags=["워크플로우"])