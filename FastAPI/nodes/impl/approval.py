import time

from langgraph.types import interrupt

from nodes.base import BaseNode, NodeContext, NodeResult


class ApprovalNode(BaseNode):
    node_type = "approval"
    name = "결재 요청"
    category = "human"
    description = "결재를 올리고 완료될 때까지 대기"
    config_fields = [
        {"key": "form_code", "label": "결재 양식", "type": "select",
         "options": ["경비 품의서", "휴가 신청서"], "required": True},
    ]
    branches = ["승인", "반려"]
    output_vars = [
        {"key": "approval_id", "label": "결재 번호", "type": "text"},
        {"key": "comment", "label": "결재 의견", "type": "text"},
    ]

    def run(self, ctx: NodeContext) -> NodeResult:
        time.sleep(1)
        approval_id = f"TEMP-{ctx.run_id}-{ctx.node_id}"
        print("결재 상신", approval_id)

        decision = interrupt({"approval_id": approval_id, "form_code": ctx.config["form_code"]})

        branch = "승인" if decision.get("approved") else "반려"

        return NodeResult(
            output={"approval_id": approval_id, "comment": decision.get("comment")},
            branch=branch,
        )