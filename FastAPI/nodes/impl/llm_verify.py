"""자동 검증 노드: 규칙 + OCR 신뢰도 + GMS 독립 추출 비교로 자동 확정 여부 판단."""
from ai.llm import gms_status
from ai.modules.receipt import extract_receipt_with_gms
from core.database import SessionLocal
from nodes.base import BaseNode, NodeContext, NodeResult
from runs.models import RunFeedback

# 문서 종류 → GMS 검증용 추출 함수
GMS_EXTRACTORS = {
    "영수증": extract_receipt_with_gms,
}


def _latest(outputs: dict, key: str) -> dict | None:
    """앞 노드 출력 중 key를 가진 가장 최근 출력."""
    for out in reversed(list(outputs.values())):
        if isinstance(out, dict) and key in out:
            return out
    return None


def _norm(value):
    """비교용 정규화: 문자열은 공백을 모두 제거 (OCR 띄어쓰기 차이 무시)."""
    return "".join(value.split()) if isinstance(value, str) else value


class LlmVerifyNode(BaseNode):
    node_type = "llm_verify"
    name = "자동 검증"
    category = "ai"
    description = "업무 규칙·OCR 신뢰도·GMS 교차 추출로 자동 확정, 애매하면 사람 확인으로"
    config_fields = [
        {"key": "use_gms", "label": "GMS 교차 검증", "type": "select",
         "options": ["사용", "사용 안 함"], "required": False},
        {"key": "min_confidence", "label": "최소 OCR 신뢰도", "type": "number", "required": False},
    ]
    branches = ["자동 확정", "사람 확인"]
    output_vars = [
        {"key": "fields", "label": "추출 결과", "type": "object"},
        {"key": "reasons", "label": "사람 확인 사유", "type": "list"},
        {"key": "gms_fields", "label": "GMS 추출 결과", "type": "object"},
    ]

    def run(self, ctx: NodeContext) -> NodeResult:
        extraction = _latest(ctx.outputs, "fields")
        if extraction is None:
            raise ValueError("검증할 추출 결과가 없습니다 (앞에 추출 노드가 필요합니다)")
        fields = extraction["fields"]
        doc_type = extraction.get("doc_type")
        reasons = []

        # ① 업무 규칙 (structured_extract가 검사한 결과)
        for name in extraction.get("problems", []):
            reasons.append(f"규칙 위반: {name}")

        # ② OCR 신뢰도
        ocr = _latest(ctx.outputs, "confidence")
        threshold = float(ctx.config.get("min_confidence") or 0.9)
        if ocr is not None and ocr["confidence"] < threshold:
            reasons.append(f"OCR 신뢰도 낮음: {ocr['confidence']}")

        # ③ GMS 독립 추출과 비교 (규칙에서 이미 걸렸으면 GMS 호출 생략 → 비용 절약)
        gms_fields = None
        use_gms = ctx.config.get("use_gms", "사용") != "사용 안 함"
        if use_gms and not reasons:
            if gms_status() != "configured" or doc_type not in GMS_EXTRACTORS:
                reasons.append("GMS 검증 불가")
            else:
                try:
                    gms_fields = GMS_EXTRACTORS[doc_type](ocr["text"] if ocr else "").model_dump()
                    diff = [k for k in fields if _norm(fields[k]) != _norm(gms_fields.get(k))]
                    reasons += [f"GMS와 불일치: {k}" for k in diff]
                except Exception as e:
                    reasons.append(f"GMS 검증 실패: {e.__class__.__name__}")

        out = {
            "fields": fields, "doc_type": doc_type,
            "model_version": extraction.get("model_version"),
            "problems": extraction.get("problems", []),
            "reasons": reasons, "gms_fields": gms_fields,
        }

        if reasons:
            return NodeResult(output=out, branch="사람 확인")

        # 모두 통과 → 자동 확정, 피드백 저장 (라벨 출처: 로컬·GMS 일치)
        with SessionLocal() as db:
            db.add(RunFeedback(
                run_id=ctx.run_id, node_id=ctx.node_id, doc_type=doc_type,
                model_version=extraction.get("model_version"),
                model_output=fields, final_output=fields, edited_fields=[],
                label_source="llm_agree",
            ))
            db.commit()
        return NodeResult(output=out, branch="자동 확정")