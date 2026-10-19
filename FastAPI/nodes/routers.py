from fastapi import APIRouter

from nodes import service

router = APIRouter(prefix="/api/v1/nodes", tags=["노드"])


@router.get("/categories")
def get_categories():
    return service.get_categories()