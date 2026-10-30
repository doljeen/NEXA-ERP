import time

from nodes.base import BaseNode, NodeContext, NodeResult


class OcrExtractNode(BaseNode):
    node_type = "ocr_extract"
    name = "서류 항목 추출"
    category = "ai"
    description = "OCR + LLM으로 가맹점·금액·날짜 추출"
    config_fields = [
        {"key": "fields", "label": "추출할 항목", "type": "text", "required": True},
    ]
    branches = ["성공", "확인 필요"]
    output_vars = [
        {"key": "items", "label": "추출 결과", "type": "object"},
        {"key": "confidence", "label": "신뢰도", "type": "number"},
    ]


    def run(self, ctx: NodeContext) -> NodeResult:
        time.sleep(1)
        fields = [f.strip() for f in ctx.config["fields"].split(",")]
        items = {f: f"{f} 샘플값" for f in fields}
        confidence = 0.97
        branch = "성공" if confidence >= 0.8 else "확인 필요"
        return NodeResult(output={"items": items, "confidence": confidence}, branch=branch)