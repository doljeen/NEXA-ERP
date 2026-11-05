"""5-5 DSPy 최적화: train으로 지시문·예시 후보를 만들고 dev 점수로 골라 도전자(challenger) 프로그램을 만든다.

  python -X utf8 -m mlops.optimize                   # MIPROv2 light (기본)
  python -X utf8 -m mlops.optimize --auto medium     # 후보를 더 많이 탐색 (더 오래 걸림)

결과: mlops/data/programs/miprov2-날짜-시각.json + MLflow run (stage=optimize)
test는 여기서 절대 쓰지 않는다 → 5-6 승격 판정용으로 아껴둠 (dev에 맞춰진 점수로 승격하면 안 되니까)
"""
import argparse
import logging
import time
from pathlib import Path

import dspy
from dspy.teleprompt import MIPROv2

from core.config import settings
from mlops.common import DATA_DIR, dspy_metric, load_program, load_records, require_local_lm, to_examples
from mlops.evaluate import breakdown, log_to_mlflow, print_report, run_eval, summarize

logging.getLogger("LiteLLM").setLevel(logging.WARNING)
OUT_DIR = Path("mlops/data/programs")       # mlops/data 아래라 git에 안 올라감 (원본은 MLflow artifact)


def local_lm(temperature: float, max_tokens: int = 1024) -> dspy.LM:
    """최적화 전용 로컬 LM.
    운영용(get_local_lm)과 다른 점: 재시도 2회 → 수백 번 호출하다 한 번 끊겨도 전체가 죽지 않게"""
    return dspy.LM(
        model=f"openai/{settings.local_llm_model}",
        api_base=settings.local_llm_base_url,
        api_key=settings.local_llm_api_key,
        temperature=temperature,
        max_tokens=max_tokens,
        timeout=settings.local_llm_timeout,
        num_retries=2,
        cache=False,
        extra_body={"chat_template_kwargs": {"enable_thinking": False}},
    )


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--data", type=Path, default=DATA_DIR)
    p.add_argument("--auto", choices=["light", "medium", "heavy"], default="light", help="탐색량 (light ≈ 10~20분)")
    p.add_argument("--demos", type=int, default=3, help="프롬프트에 넣을 예시 최대 개수 (수집 예시·정답 예시 각각)")
    p.add_argument("--threads", type=int, default=4)
    p.add_argument("--seed", type=int, default=42)
    args = p.parse_args()

    require_local_lm()                                     # 로컬 LLM 설정 확인
    train = to_examples(load_records("train", args.data))
    dev_records = load_records("dev", args.data)
    dev = to_examples(dev_records)

    task_lm = local_lm(0.0)                                # 실제 추출: 항상 같은 답
    prompt_lm = local_lm(0.7, max_tokens=2048)             # 지시문 후보 작성: 다양하게 쓰도록 온도 올림
    dspy.configure(lm=task_lm)

    optimizer = MIPROv2(
        metric=dspy_metric,
        prompt_model=prompt_lm,
        task_model=task_lm,
        auto=args.auto,
        max_bootstrapped_demos=args.demos,                 # 모델이 직접 맞힌 답 중에서 고른 예시
        max_labeled_demos=args.demos,                      # 정답 라벨을 그대로 쓴 예시 (오타 교정을 보여줄 수 있음)
        num_threads=args.threads,
        seed=args.seed,
    )
    print(f"MIPROv2({args.auto}) 시작: train {len(train)}장으로 후보 생성, dev {len(dev)}장으로 채점")
    start = time.time()
    optimized = optimizer.compile(load_program(), trainset=train, valset=dev)
    minutes = (time.time() - start) / 60

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    name = f"miprov2-{time.strftime('%Y%m%d-%H%M')}"
    path = OUT_DIR / f"{name}.json"
    optimized.save(str(path))

    print(f"\n최적화 완료 ({minutes:.1f}분) → {path}")
    print("[선택된 지시문]")
    print(optimized.extract.signature.instructions)
    print(f"[예시 개수] {len(optimized.extract.demos)}")

    # 도전자를 dev로 다시 채점 → base와 같은 형식으로 비교 (평가는 운영과 같은 조건: 재시도 0, 온도 0)
    rows = run_eval(optimized, dev_records, args.threads)
    s, bd = summarize(rows), breakdown(rows)
    print_report(name, "dev", args.data.name, s, bd, rows)

    tags = {
        "stage": "optimize", "optimizer": "MIPROv2", "auto": args.auto,
        "max_demos": str(args.demos), "seed": str(args.seed),
        "train_n": str(len(train)), "opt_minutes": f"{minutes:.1f}",
    }
    run_id = log_to_mlflow(name, "dev", args.data, optimized, str(path), s, bd, rows, args.threads, tags=tags)
    print(f"\nMLflow 기록 완료: run_id={run_id}")


if __name__ == "__main__":
    main()