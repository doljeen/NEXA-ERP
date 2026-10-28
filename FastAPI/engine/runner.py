import logging

from sqlalchemy import func

from core.database import SessionLocal
from engine.builder import StepRecorder, build_graph
from runs.models import Run, RunStatus, RunStep


log = logging.getLogger(__name__)


class RunCanceled(Exception):
    pass


class DbRecorder(StepRecorder):
    def __init__(self, run_id: int):
        self.run_id = run_id
        self.step_ids: dict[str, int] = {}

    def start(self, node_id: str, node_type: str) -> None:
        with SessionLocal() as db:
            run = db.get(Run, self.run_id)
            if run.status == RunStatus.CANCELED:
                raise RunCanceled()

            step = RunStep(run_id=self.run_id, node_id=node_id, node_type=node_type)
            db.add(step)
            run.current_node_id = node_id
            db.commit()
            self.step_ids[node_id] = step.id

    def finish(self, node_id: str, output: dict, branch: str | None) -> None:
        with SessionLocal() as db:
            step = db.get(RunStep, self.step_ids[node_id])
            step.status = RunStatus.SUCCEEDED
            step.output = output
            step.branch = branch
            step.finished_at = func.now()
            db.commit()

    def fail(self, node_id: str, error: Exception) -> None:
        with SessionLocal() as db:
            step = db.get(RunStep, self.step_ids[node_id])
            step.status = RunStatus.FAILED
            step.error = str(error)
            step.finished_at = func.now()
            db.commit()


def execute_run(run_id: int) -> None:
    with SessionLocal() as db:
        run = db.get(Run, run_id)
        if run is None or run.status != RunStatus.QUEUED:
            return
        graph_json = run.graph
        inputs = run.inputs
        run.status = RunStatus.RUNNING
        run.started_at = func.now()
        db.commit()

        try:
            graph = build_graph(graph_json, DbRecorder(run_id))
            result = graph.invoke({"inputs": inputs, "outputs": {}, "branches": {}})
        except RunCanceled:
            return
        except Exception as e:
            log.exception("실행 실패 run_id=%s", run_id)
            _finish(run_id, RunStatus.FAILED, error=str(e))
            return

        _finish(run_id, RunStatus.SUCCEEDED, outputs=result["outputs"])


def _finish(run_id: int, status: RunStatus, outputs: dict | None = None, error: str | None = None) -> None:
    with SessionLocal() as db:
        run = db.get(Run, run_id)
        if run.status == RunStatus.CANCELED:
            return
        run.status = status
        run.outputs = outputs
        run.error = error
        run.finished_at = func.now()
        db.commit()