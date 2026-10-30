import time

from nodes.base import BaseNode, NodeContext, NodeResult


class RagSearchNode(BaseNode):
    node_type = "rag_search"
    name = "규정 근거 검색"
    category = "ai"
    description = "사내 규정에서 판단 근거를 찾음"
    config_fields = [
        {"key": "doc_set", "label": "규정 문서", "type": "select",
         "options": ["경비 규정", "휴가 규정"], "required": True},
    ]
    branches = ["적합", "부적합", "애매함"]
    output_vars = [
        {"key": "evidence", "label": "근거 조항", "type": "text"},
    ]

    def run(self, ctx: NodeContext) -> NodeResult:
        time.sleep(1)
        doc_set = ctx.config["doc_set"]
        evidence = f"[{doc_set}] 제5조: 1인당 식대는 3만원 이내로 한다."
        return NodeResult(output={"evidence": evidence}, branch="적합")

    