from nodes.base import BaseNode
from nodes.impl.approval import ApprovalNode
from nodes.impl.file_upload import FileUploadNode
from nodes.impl.human_confirm import HumanConfirmNode
from nodes.impl.llm_verify import LlmVerifyNode
from nodes.impl.ocr_extract import OcrExtractNode
from nodes.impl.rag_search import RagSearchNode
from nodes.impl.structured_extract import StructuredExtractNode
from nodes.impl.approval_draft import ApprovalDraftNode
from nodes.impl.employee_lookup import EmployeeLookupNode


CATEGORIES = [
    {"code": "input", "name": "입력"},
    {"code": "ai", "name": "AI"},
    {"code": "rule", "name": "규칙·분기"},
    {"code": "human", "name": "사람 확인"},
    {"code": "output", "name": "문서·알림"},
]


# 노드 목록 (팔레트에 이 순서로 보임)
_NODE_CLASSES: list[type[BaseNode]] = [
    FileUploadNode,
    EmployeeLookupNode,      # 사원 조회 (user-service)
    OcrExtractNode,          # OCR 글자 인식
    StructuredExtractNode,   # 항목 추출 (DSPy)
    LlmVerifyNode,           # AI 교차 검증 (GMS)
    RagSearchNode,
    HumanConfirmNode,        # 사람 확인 (interrupt)
    ApprovalNode,
    ApprovalDraftNode,       # 결재 상신 (approval-service)
]


NODE_REGISTRY: dict[str, BaseNode] = {}


for node_class in _NODE_CLASSES:
    node = node_class()
    if node.node_type in NODE_REGISTRY:
        raise ValueError(f"노드 종류가 중복됩니다: {node.node_type}")
    NODE_REGISTRY[node.node_type] = node


def get_node_impl(node_type: str) -> BaseNode | None:
    return NODE_REGISTRY.get(node_type)


def catalog() -> list[dict]:
    return [node.to_catalog() for node in NODE_REGISTRY.values()]