import copy

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from core.exceptions import BusinessException, ErrorCode
from runs.models import FINISHED_STATUSES, Run, RunStatus, RunStep
from runs.schemas import RunCreateRequest
from workflows.models import Workflow
from workflows.validator import validate_graph


def _get(db: Session, run_id: int) -> Run:
    run = db.get(Run, run_id)
    if run is None:
        raise BusinessException(ErrorCode.RUN_NOT_FOUND)
    return run


def _get_workflow(db: Session, workflow_id: int) -> Workflow:
    workflow = db.get(Workflow, workflow_id)
    if workflow is None:
        raise BusinessException(ErrorCode.WF_NOT_FOUND)
    return workflow


def _check_required_inputs(workflow: Workflow, inputs: dict) -> None:
    for field in workflow.input_form:
        if field["type"] == "file":
            continue
        if field.get("required") and not inputs.get(field["key"]):
            raise BusinessException(ErrorCode.RUN_MISSING_INPUT, f"'{field['label']}' 값이 비어 있습니다.")


def create_run(db: Session, workflow_id: int, req: RunCreateRequest, user_id: str | None) -> Run:
    workflow = _get_workflow(db, workflow_id)
    if workflow.deployed_version is None:
        raise BusinessException(ErrorCode.WF_NOT_DEPLOYED)

    version = next(v for v in workflow.versions if v.version == workflow.deployed_version)
    _check_required_inputs(workflow, req.inputs)

    run = Run(
        workflow_id=workflow.id,
        version=version.version,
        graph=copy.deepcopy(version.graph),
        inputs=req.inputs,
        requested_by=user_id,
    )
    db.add(run)
    db.commit()
    db.refresh(run)
    return run


def create_test_run(db: Session, workflow_id: int, req: RunCreateRequest, user_id: str | None) -> Run:
    workflow = _get_workflow(db, workflow_id)

    issues = validate_graph(workflow.draft_graph)
    if issues:
        raise BusinessException(ErrorCode.WF_INVALID_GRAPH, issues[0]["message"])

    run = Run(
        workflow_id=workflow.id,
        version=None,
        is_test=True,
        graph=copy.deepcopy(workflow.draft_graph),
        inputs=req.inputs,
        requested_by=user_id,
    )
    db.add(run)
    db.commit()
    db.refresh(run)
    return run


def get_run(db: Session, run_id: int) -> Run:
    return _get(db, run_id)


def list_steps(db: Session, run_id: int) -> list[RunStep]:
    return _get(db, run_id).steps


def list_my_runs(db: Session, user_id: str | None, status: str | None) -> list[Run]:
    stmt = select(Run).where(Run.requested_by == user_id).order_by(Run.id.desc()).limit(50)
    if status:
        stmt = stmt.where(Run.status == status)
    return list(db.scalars(stmt).all())


def cancel_run(db: Session, run_id: int) -> Run:
    run = _get(db, run_id)
    if run.status in FINISHED_STATUSES:
        raise BusinessException(ErrorCode.RUN_ALREADY_FINISHED)
    run.status = RunStatus.CANCELED
    run.finished_at = func.now()
    db.commit()
    db.refresh(run)
    return run