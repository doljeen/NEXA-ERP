from fastapi import APIRouter

from workflows import service

router = APIRouter(prefix="/workflows", tags=["워크플로우"])