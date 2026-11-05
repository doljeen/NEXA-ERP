"""5-6 승격 게이트: 도전자를 현재 챔피언과 같은 test로 비교해 규칙을 모두 통과하면 승격한다.

  python -X utf8 -m mlops.promote --challenger mlops/data/programs/miprov2-xxx.json
  python -X utf8 -m mlops.promote --challenger ... --dry-run     # 판정만 하고 승격은 안 함

승격되면
  1) MLflow Model Registry: receipt-extractor 새 버전 등록 + 별칭 champion
  2) 서빙용 파일 내보내기: ai/programs/receipt.json + receipt.meta.json → 커밋하면 CI/CD로 배포
떨어지면: 등록·내보내기 없이 MLflow에 판정 기록만 남김 (왜 떨어졌는지 나중에 볼 수 있게)
"""
import argparse
import json
from datetime import datetime
from pathlib import Path

from ai.modules.receipt import PROGRAM_DIR
from mlops.common import DATA_DIR, FIELDS, dataset_info, load_program, load_records, setup_mlflow
from mlops.evaluate import breakdown, log_to_mlflow, print_report, run_eval, summarize

MODEL_NAME = "receipt-extractor"          # MLflow Model Registry 이름

# ---------- 승격 규칙 (test 기준) ----------
MIN_GAIN = 0.02            # 전부 정답률 최소 +2%p (100장 기준 2장 이상 더 맞혀야 함)
MAX_FIELD_DROP = 0.01      # 필드별 정답률은 최대 1%p(1장)까지만 하락 허용 → 한 필드를 희생한 개선 방지
MAX_LATENCY_RATIO = 1.5    # 예시가 붙으면 프롬프트가 길어져 느려짐 → 1.5배까지만 허용
EPS = 1e-9                 # 소수 계산 오차 보정 (0.81 - 0.79 = 0.0199999... 같은 경우)


def current_champion() -> tuple[str, Path | None]:
    """지금 서빙 중인 챔피언: ai/programs/receipt.json이 있으면 그것, 없으면 기본 프로그램(base)"""
    path = PROGRAM_DIR / "receipt.json"
    meta_path = PROGRAM_DIR / "receipt.meta.json"
    if not path.exists():
        return "receipt@base", None
    meta = json.loads(meta_path.read_text(encoding="utf-8")) if meta_path.exists() else {}
    return meta.get("label", "receipt@champion"), path


def gate(champ: dict, chal: dict) -> tuple[bool, list[tuple[str, bool, str]]]:
    """승격 규칙 검사. (통과 여부, [(규칙 이름, 통과, 설명), ...])"""
    checks = []
    gain = chal["acc_all"] - champ["acc_all"]
    checks.append((f"전부 정답 +{MIN_GAIN:.0%}p 이상", gain >= MIN_GAIN - EPS,
                   f"{champ['acc_all']:.1%} → {chal['acc_all']:.1%} ({gain:+.1%})"))
    for k in FIELDS:
        drop = champ[f"acc_{k}"] - chal[f"acc_{k}"]
        checks.append((f"{k} 하락 {MAX_FIELD_DROP:.0%}p 이내", drop <= MAX_FIELD_DROP + EPS,
                       f"{champ[f'acc_{k}']:.1%} → {chal[f'acc_{k}']:.1%}"))
    checks.append(("실행 실패 증가 없음", chal["error_rate"] <= champ["error_rate"] + EPS,
                   f"{champ['error_rate']:.1%} → {chal['error_rate']:.1%}"))
    ratio = chal["avg_ms"] / max(champ["avg_ms"], 1)
    checks.append((f"지연 {MAX_LATENCY_RATIO}배 이내", ratio <= MAX_LATENCY_RATIO,
                   f"{champ['avg_ms']}ms → {chal['avg_ms']}ms (x{ratio:.2f})"))
    return all(ok for _, ok, _ in checks), checks


def register_champion(run_id: str) -> int:
    """도전자 run의 program 아티팩트를 Registry 새 버전으로 등록하고 champion 별칭을 옮긴다"""
    from mlflow import MlflowClient
    from mlflow.exceptions import MlflowException

    client = MlflowClient()
    try:
        client.get_registered_model(MODEL_NAME)
    except MlflowException:                               # 처음 승격이면 모델 이름부터 만듦
        client.create_registered_model(MODEL_NAME, description="영수증 항목 추출 DSPy 프로그램 (지시문 + 예시)")
    run = client.get_run(run_id)
    mv = client.create_model_version(MODEL_NAME, source=f"{run.info.artifact_uri}/program", run_id=run_id)
    client.set_registered_model_alias(MODEL_NAME, "champion", mv.version)   # 별칭은 한 버전에만 붙음 → 이전 챔피언에서 자동으로 떨어짐
    return int(mv.version)


def export_for_serving(program, version: int, run_id: str, ds: dict, s: dict, previous: str):
    """B안: 서빙은 MLflow에 실시간으로 붙지 않고, Git에 포함된 이 파일을 읽는다"""
    PROGRAM_DIR.mkdir(parents=True, exist_ok=True)
    program.save(str(PROGRAM_DIR / "receipt.json"))
    meta = {
        "model": MODEL_NAME,
        "version": version,
        "label": f"receipt@v{version}",                  # structured_extract가 model_version으로 기록하는 값
        "previous": previous,
        "run_id": run_id,
        **ds,
        "test_acc_all": s["acc_all"],
        "promoted_at": datetime.now().isoformat(timespec="seconds"),
    }
    (PROGRAM_DIR / "receipt.meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--challenger", required=True, help="도전자 프로그램 JSON (5-5 결과)")
    p.add_argument("--name", help="도전자 이름 (기본: 파일 이름)")
    p.add_argument("--data", type=Path, default=DATA_DIR)
    p.add_argument("--split", default="test", help="판정용 split (최적화에 안 쓴 데이터여야 함)")
    p.add_argument("--threads", type=int, default=4)
    p.add_argument("--dry-run", action="store_true", help="판정만, 등록·내보내기 안 함")
    args = p.parse_args()

    records = load_records(args.split, args.data)
    champ_label, champ_path = current_champion()
    champion = load_program(champ_path)
    challenger = load_program(args.challenger)
    chal_name = args.name or Path(args.challenger).stem

    # 같은 시점·같은 조건으로 둘 다 다시 채점 (예전 점수를 가져다 쓰면 vLLM 상태 차이가 섞일 수 있음)
    print(f"[1/2] 챔피언 {champ_label} 평가 ({args.split} {len(records)}장)")
    champ_rows = run_eval(champion, records, args.threads)
    print(f"[2/2] 도전자 {chal_name} 평가")
    chal_rows = run_eval(challenger, records, args.threads)
    cs, hs = summarize(champ_rows), summarize(chal_rows)

    passed, checks = gate(cs, hs)
    print_report(chal_name, args.split, args.data.name, hs, breakdown(chal_rows), chal_rows)
    print(f"\n=== 승격 판정: {champ_label} vs {chal_name} ===")
    for rule, ok, detail in checks:
        print(f"  {'✅' if ok else '❌'} {rule:20} {detail}")
    decision = ("promoted" if not args.dry_run else "pass(dry-run)") if passed else "rejected"
    print(f"결과: {decision}")

    # 기록: 챔피언·도전자 평가를 각각 run으로, 도전자 run에 판정 결과를 태그로
    common = {"stage": "promote", "gate_split": args.split}
    champ_run = log_to_mlflow(champ_label, args.split, args.data, champion, str(champ_path or "(base)"),
                              cs, breakdown(champ_rows), champ_rows, args.threads,
                              tags={**common, "role": "champion"})
    chal_run = log_to_mlflow(chal_name, args.split, args.data, challenger, args.challenger,
                             hs, breakdown(chal_rows), chal_rows, args.threads,
                             tags={**common, "role": "challenger", "decision": decision,
                                   "compared_with": champ_run,
                                   "gain_acc_all": f"{hs['acc_all'] - cs['acc_all']:+.4f}"})

    if passed and not args.dry_run:
        setup_mlflow()
        version = register_champion(chal_run)
        export_for_serving(challenger, version, chal_run, dataset_info(args.data), hs, champ_label)
        print(f"\n승격 완료: {MODEL_NAME} v{version} (champion) → {PROGRAM_DIR}/receipt.json")
        print("다음: FastAPI 재시작으로 확인 → git add ai/programs → 커밋 → 배포")


if __name__ == "__main__":
    main()