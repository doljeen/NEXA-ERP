from fastapi import APIRouter

from runs import service

router = APIRouter(prefix="/runs", tags=["실행"])

