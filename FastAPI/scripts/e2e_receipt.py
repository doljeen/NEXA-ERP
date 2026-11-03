"""영수증 워크플로우 E2E 테스트 (개발용, 커밋 안 함).

흐름: OCR → 항목 추출 → AI 교차 검증 → (사람 확인) → [--approval] 사원 조회 → 결재 상신

토큰: 환경변수 TOKEN(실행), RESUME_TOKEN(재개, 없으면 TOKEN)
      둘 다 없으면 서버의 개발용 우회(AUTH_DEV_EMPLOYEE_ID)로 동작

사용 예:
  python scripts/e2e_receipt.py ~/Downloads/receipt2.jpg --mode auto
  TOKEN=$TOKEN_104 python scripts/e2e_receipt.py ~/Downloads/receipt2.jpg --mode auto --approval
  TOKEN=$TOKEN_104 python scripts/e2e_receipt.py ~/Downloads/receipt1.jpg --mode human --approval --set total=17900
  TOKEN=$TOKEN_104 RESUME_TOKEN=$TOKEN_105 python scripts/e2e_receipt.py ~/Downloads/receipt1.jpg --mode human --approval
"""
import argparse
import json
import os
import time
from pathlib import Path

import httpx

BASE = "http://localhost:8086/api/v1/ai"
DONE = {"SUCCEEDED", "FAILED", "CANCELED"}


def build_graph(mode: str, approval: bool = False) -> dict:
    # auto: GMS 교차 검증 / human: 기준을 일부러 높여서 무조건 사람 확인으로
    if mode == "auto":
        verify_config = {"use_gms": "사용", "min_confidence": 0.8}
    else:
        verify_config = {"use_gms": "사용 안 함", "min_confidence": 0.999}

    def node(nid, ntype, x, config=None):
        return {"id": nid, "type": ntype, "position": {"x": x, "y": 0}, "config": config or {}}

    def edge(eid, src, dst, branch=None):
        return {"id": eid, "source": src, "target": dst, "branch": branch}

    nodes = [
        node("n1", "ocr_extract", 0),
        node("n2", "structured_extract", 250, {"doc_type": "영수증"}),
        node("n3", "llm_verify", 500, verify_config),
        node("n4", "human_confirm", 750),
    ]
    edges = [
        # OCR·추출은 성공이든 확인 필요든 다음으로 넘김 (판단은 검증 노드가 함)
        edge("e1", "n1", "n2", "성공"),
        edge("e2", "n1", "n2", "확인 필요"),
        edge("e3", "n2", "n3", "성공"),
        edge("e4", "n2", "n3", "확인 필요"),
        edge("e5", "n3", "n4", "사람 확인"),
    ]
    if approval:
        # 자동 확정이든 사람이 확인했든 → 사원 조회 → 결재 상신
        nodes += [
            node("n5", "employee_lookup", 1000),
            node("n6", "approval_draft", 1250,
                 {"form_type": "일반 기안", "title": "[경비] {store} {total:,}원 ({requester})"}),
        ]
        edges += [
            edge("e6", "n3", "n5", "자동 확정"),
            edge("e7", "n4", "n5", "확인"),
            edge("e8", "n5", "n6"),
        ]
    return {"nodes": nodes, "edges": edges}


def data(res: httpx.Response):
    """ApiResult 껍데기를 벗겨서 data만 꺼냄. 실패면 에러를 그대로 보여줌."""
    body = res.json()
    if not body.get("success"):
        raise SystemExit(f"[{res.status_code}] {json.dumps(body, ensure_ascii=False)}")
    return body["data"]


def wait_run(client: httpx.Client, run_id: int, timeout: float = 180) -> dict:
    """끝나거나(SUCCEEDED/FAILED) 사람을 기다릴 때(WAITING)까지 1초마다 확인"""
    start = time.time()
    while time.time() - start < timeout:
        run = data(client.get(f"/runs/{run_id}"))
        if run["status"] in DONE or run["status"] == "WAITING":
            return run
        time.sleep(1)
    raise SystemExit(f"시간 초과: run {run_id}가 {timeout}초 안에 끝나지 않음")


def wait_resumed(client: httpx.Client, run_id: int, timeout: float = 10) -> None:
    """resume은 202를 먼저 주고 백그라운드에서 돌아서, 직후엔 아직 WAITING일 수 있음 → 벗어날 때까지 대기"""
    start = time.time()
    while time.time() - start < timeout:
        if data(client.get(f"/runs/{run_id}"))["status"] != "WAITING":
            return
        time.sleep(0.5)


def print_steps(client: httpx.Client, run_id: int) -> list[dict]:
    steps = data(client.get(f"/runs/{run_id}/steps"))
    print(f"\n--- run {run_id} 단계 ---")
    for s in steps:
        print(f"[{s['nodeId']}] {s['nodeType']:<20} {s['status']:<10} 분기={s['branch']}")
        if s.get("error"):
            print(f"    에러: {s['error']}")
        if s.get("output"):
            print("    " + json.dumps(s["output"], ensure_ascii=False)[:300])
    return steps


def main():
    p = argparse.ArgumentParser()
    p.add_argument("image", type=Path)
    p.add_argument("--mode", choices=["auto", "human"], default="auto")
    p.add_argument("--reject", action="store_true", help="사람 확인에서 반려")
    p.add_argument("--set", action="append", default=[], help="사람이 고친 값 key=value (여러 번 가능)")
    p.add_argument("--approval", action="store_true", help="사원 조회 + 결재 상신까지")
    args = p.parse_args()

    # 토큰: TOKEN(실행), RESUME_TOKEN(재개, 없으면 TOKEN) / 둘 다 없으면 개발용 우회
    token = os.environ.get("TOKEN")
    resume_token = os.environ.get("RESUME_TOKEN") or token
    headers = {"Authorization": f"Bearer {token}"} if token else {}

    with httpx.Client(base_url=BASE, timeout=30, headers=headers) as client:
        # 1) 워크플로우 만들고 그래프 저장
        wf = data(client.post("/workflows", json={"name": f"영수증 E2E ({args.mode})"}))
        wf_id = wf["id"]
        data(client.put(f"/workflows/{wf_id}/graph", json=build_graph(args.mode, args.approval)))
        check = data(client.post(f"/workflows/{wf_id}/validate"))
        print(f"워크플로우 {wf_id} 검사 결과: {json.dumps(check, ensure_ascii=False)}")

        # 2) 파일 업로드
        with args.image.open("rb") as f:
            up = data(client.post("/runs/files", files={"file": (args.image.name, f, "image/jpeg")}))
        print(f"파일 업로드: id={up['id']} {up['originalName']} ({up['size']} bytes)")

        # 3) 테스트 실행
        created = data(client.post(f"/workflows/{wf_id}/test-runs", json={"fileIds": [up["id"]]}))
        run_id = created["runId"]
        print(f"실행 시작: run {run_id}")

        run = wait_run(client, run_id)
        print(f"상태: {run['status']} (현재 노드: {run.get('currentNodeId')})")
        steps = print_steps(client, run_id)

        # 4) 사람 확인에서 멈췄으면 재개
        if run["status"] == "WAITING":
            waiting = next((s["output"] for s in steps if s["status"] == "WAITING"), {}) or {}
            print("\n사람에게 보여줄 내용:")
            print(json.dumps(waiting.get("waiting_for"), ensure_ascii=False, indent=2))

            edits = dict(kv.split("=", 1) for kv in args.set)
            for k, v in edits.items():   # 숫자 필드는 int로
                if v.isdigit():
                    edits[k] = int(v)
            body = {"approved": not args.reject, "comment": "E2E 테스트", "data": edits or None}
            print(f"\nresume 보냄: {json.dumps(body, ensure_ascii=False)}")
            resume_headers = {"Authorization": f"Bearer {resume_token}"} if resume_token else {}
            data(client.post(f"/runs/{run_id}/resume", json=body, headers=resume_headers))

            wait_resumed(client, run_id)   # 재개가 실제로 시작될 때까지 기다림
            run = wait_run(client, run_id)
            print(f"재개 후 상태: {run['status']}")
            print_steps(client, run_id)

        if run.get("error"):
            print(f"\n실행 에러: {run['error']}")


if __name__ == "__main__":
    main()