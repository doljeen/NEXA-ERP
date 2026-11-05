"""5-7 골든셋: 사람이 확정한 결과(run_feedback, label_source=human)를 평가셋으로 내보낸다.

합성 데이터 점수는 '우리가 만든 함정'에 대한 점수라 실제 영수증 성능과 다를 수 있다.
그래서 사람이 직접 확정(수정 또는 그대로 승인)한 결과만 모아 '진짜 정답' 평가셋을 만든다.
llm_agree(로컬·GMS 일치 자동 확정)는 넣지 않는다 → 두 모델이 같이 틀린 걸 정답으로 굳히면 안 되니까.

  python -X utf8 -m mlops.export_golden
  python -X utf8 -m mlops.evaluate --data mlops/data/golden --split test --name base

결과:
  mlops/data/golden/ocr.jsonl              ← evaluate가 그대로 읽는 형식 (split=test)
  mlops/data/golden/ocr_corrections.jsonl  ← 사람이 상호명을 고친 사례 = OCR 인식 모델 파인튜닝 후보
"""
import argparse
import json
from collections import Counter
from pathlib import Path

from sqlalchemy import select

from core.database import SessionLocal
from runs.models import RunFeedback, RunStep

OUT_DIR = Path("mlops/data/golden")       # mlops/data 아래라 git에 안 올라감 (실제 업무 데이터)
MIN_FOR_GATE = 30                          # 이보다 적으면 판정용이 아니라 참고 지표로만


def step_output(db, run_id: int, node_type: str) -> dict:
    """같은 실행에서 해당 종류 노드가 낸 마지막 출력 (없으면 빈 dict)"""
    out = db.execute(
        select(RunStep.output)
        .where(RunStep.run_id == run_id, RunStep.node_type == node_type, RunStep.output.isnot(None))
        .order_by(RunStep.id.desc())
        .limit(1)
    ).scalar_one_or_none()
    return out or {}


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--doc-type", default="영수증")
    p.add_argument("--out", type=Path, default=OUT_DIR)
    args = p.parse_args()

    records, corrections, skipped = [], [], 0
    with SessionLocal() as db:
        feedbacks = db.execute(
            select(RunFeedback)
            .where(RunFeedback.label_source == "human", RunFeedback.doc_type == args.doc_type)
            .order_by(RunFeedback.id)
        ).scalars().all()

        for fb in feedbacks:
            ocr = step_output(db, fb.run_id, "ocr_extract")
            if not ocr.get("text"):            # 입력 텍스트가 없으면 다시 돌려볼 수 없음
                skipped += 1
                continue
            label = {k: fb.final_output.get(k) for k in ("store", "total", "date")}
            records.append({
                "id": f"g{fb.id:05d}",
                "split": "test",
                "text": ocr["text"],
                "confidence": ocr.get("confidence"),
                "label": label,
                # 합성 데이터의 meta(사진 여부·함정 등)는 실제 데이터엔 없음 → 출처 정보만
                "meta": {"source": "human", "run_id": fb.run_id, "model_version": fb.model_version,
                         "edited_fields": fb.edited_fields or [], "traps": []},
            })
            # 사람이 상호명을 고쳤다 = OCR이 틀렸을 가능성 → 원본 파일과 함께 파인튜닝 후보로
            if "store" in (fb.edited_fields or []):
                corrections.append({
                    "run_id": fb.run_id,
                    "files": step_output(db, fb.run_id, "file_upload").get("files", []),
                    "model_store": fb.model_output.get("store"),
                    "correct_store": label["store"],
                })

    args.out.mkdir(parents=True, exist_ok=True)
    for name, rows in (("ocr.jsonl", records), ("ocr_corrections.jsonl", corrections)):
        with (args.out / name).open("w", encoding="utf-8") as f:
            for r in rows:
                f.write(json.dumps(r, ensure_ascii=False) + "\n")

    versions = Counter(r["meta"]["model_version"] for r in records)
    edited = sum(1 for r in records if r["meta"]["edited_fields"])
    print(f"골든셋 {len(records)}장 (사람이 고친 것 {edited}장, 그대로 승인 {len(records) - edited}장, 텍스트 없어 제외 {skipped}장)")
    print(f"모델 버전별: {dict(versions)}")
    print(f"OCR 파인튜닝 후보 (상호명 수정): {len(corrections)}건")
    if len(records) < MIN_FOR_GATE:
        print(f"⚠️ {MIN_FOR_GATE}장 미만 → 승격 판정에는 아직 쓰지 말고 참고 지표로만")
    print(f"저장: {args.out}")


if __name__ == "__main__":
    main()