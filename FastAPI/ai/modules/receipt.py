"""영수증 항목 추출 DSPy 모듈."""
import dspy
from pydantic import BaseModel, Field

from ai.dspy_lm import run_with_fallback
from ai.dspy_lm import get_gms_lm


class Receipt(BaseModel):
    """영수증에서 추출할 항목."""
    store: str = Field(description="상호명 (지점명 포함)")
    total: int = Field(description="실제로 결제된 최종 금액 (할인·포인트 차감 후), 원 단위 숫자만")
    date: str = Field(description="거래 날짜, YYYY-MM-DD 형식")


class ExtractReceipt(dspy.Signature):
    """영수증 OCR 텍스트에서 상호명, 합계 금액, 거래 날짜를 추출한다."""

    text: str = dspy.InputField(desc="영수증 OCR 텍스트")
    receipt: Receipt = dspy.OutputField()


class ReceiptExtractor(dspy.Module):
    """영수증 추출 모듈. 최적화(compile)와 저장·불러오기의 단위."""

    def __init__(self):
        super().__init__()
        self.extract = dspy.Predict(ExtractReceipt)  # 바로 답하기 방식

    def forward(self, text: str):
        return self.extract(text=text)


# 모듈은 한 번만 만들어 재사용 (5단계에서는 여기서 최적화된 버전을 load)
_extractor = ReceiptExtractor()


def extract_receipt(text: str) -> Receipt:
    """영수증 텍스트 → Receipt. 로컬 LLM 실패 시 GMS로 대체."""
    result = run_with_fallback(_extractor, text=text)
    return result.receipt


def extract_receipt_with_gms(text: str) -> Receipt:
    """검증용: GMS로 독립 추출 (로컬 결과와 비교하기 위함, 대체 실행 없음)."""
    with dspy.context(lm=get_gms_lm()):
        return _extractor(text=text).receipt