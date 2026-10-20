from fastapi import APIRouter, HTTPException

from nodes import service
from nodes.schemas import CategoryResponse, NodeSummary, NodeDetail

router = APIRouter(prefix="/api/v1/nodes", tags=["노드"])


@router.get("/categories", response_model=list[CategoryResponse])
def get_categories():
    return service.get_categories()


@router.get("", response_model=list[NodeSummary])
def search_nodes(category: str | None = None, q: str | None = None):
    return service.search_nodes(category, q)


@router.get("/{node_type}", response_model=NodeDetail)
def get_node(node_type: str):
    node = service.get_node(node_type)
    if node is None:
        raise HTTPException(status_code=404, detail="노드를 찾을 수 없습니다.")
    return node


