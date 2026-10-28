import copy

import workflows.models  # noqa: F401  run 테이블이 workflow 테이블을 찾을 수 있게
from sqlalchemy import select

from core.database import SessionLocal
from engine.runner import execute_run
from runs.models import Run
from workflows.models import Workflow


def create_run(graph: dict) -> int:
    with SessionLocal() as db:
        workflow = db.scalars(select(Workflow)).first()
        run = Run(workflow_id=workflow.id, graph=graph, inputs={"file_ids": [1, 2]})
        db.add(run)
        db.commit()
        return run.id


def show(run_id: int) -> None:
    with SessionLocal() as db:
        run = db.get(Run, run_id)
        print(f"\n[run {run.id}] 상태={run.status}, 마지막 노드={run.current_node_id}, 에러={run.error}")
        for s in run.steps:
            print(f"  {s.node_id} ({s.node_type}) {s.status} 분기={s.branch} 에러={s.error}")


graph = {
    "nodes": [
        {"id": "n1", "type": "file_upload", "config": {"accept": "이미지"}},
        {"id": "n2", "type": "ocr_extract", "config": {"fields": "가맹점,금액,날짜"}},
        {"id": "n3", "type": "rag_search", "config": {"doc_set": "경비 규정"}},
        {"id": "n4", "type": "approval", "config": {"form_code": "경비 품의서"}},
    ],
    "edges": [
        {"id": "e1", "source": "n1", "target": "n2"},
        {"id": "e2", "source": "n2", "target": "n3", "branch": "성공"},
        {"id": "e3", "source": "n3", "target": "n4", "branch": "적합"},
    ],
}

with SessionLocal() as db:
    if db.scalars(select(Workflow)).first() is None:
        db.add(Workflow(name="테스트 워크플로우"))
        db.commit()

# 1) 정상 실행
run_id = create_run(graph)
execute_run(run_id)
show(run_id)

# 2) 실패 실행: 규정 검색 노드의 설정을 비워서 에러 유도
broken = copy.deepcopy(graph)
for n in broken["nodes"]:
    if n["type"] == "rag_search":
        n["config"] = {}
run_id = create_run(broken)
execute_run(run_id)
show(run_id)