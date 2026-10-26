from core.exceptions import BusinessException, ErrorCode
from nodes.catalog import CATEGORIES, NODES


def get_categories() -> list[dict]:
    return CATEGORIES


def search_nodes(category: str | None, q: str | None) -> list[dict]:
    result = NODES

    if category:
        result = [n for n in result if n["category"] == category]

    if q:
        result = [n for n in result if q in n["name"] or q in n["description"]]

    return result


def get_node(node_type: str) -> dict | None:
    for node in NODES:
        if node["node_type"] == node_type:
            return node
    return BusinessException(ErrorCode.NODE_NOT_FOUND)