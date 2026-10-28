from fastapi import APIRouter, BackgroundTasks, Depends
from sqlalchemy.orm import Session

from core.auth import get_user_id
from core.database import get_db
from core.response import ApiResult, ok
from engine.runner import execute_run
from runs import service
from runs.schemas import RunCreated, RunCreateRequest, RunDetail, RunStepResponse, RunSummary


router = APIRouter(tags=["실행"])


@router.post("/workflows/{workflow_id}/runs", response_model=ApiResult[RunCreated], status_code=202)
def create_run(
    workflow_id: int,
    req: RunCreateRequest,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
    user_id: str | None = Depends(get_user_id),
):
    run = service.create_run(db, workflow_id, req, user_id)
    background_tasks.add_task(execute_run, run.id)
    return ok({"run_id": run.id, "status": run.status})


@router.post("/workflows/{workflow_id}/test-runs", response_model=ApiResult[RunCreated], status_code=202)
def create_test_run(
    workflow_id: int,
    req: RunCreateRequest,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
    user_id: str | None = Depends(get_user_id),
):
    run = service.create_test_run(db, workflow_id, req, user_id)
    background_tasks.add_task(execute_run, run.id)
    return ok({"run_id": run.id, "status": run.status})


@router.get("/runs/me", response_model=ApiResult[list[RunSummary]])
def list_my_run(
    status: str | None = None,
    db: Session = Depends(get_db),
    user_id: str | None = Depends(get_user_id),
):
    runs = service.list_my_runs(db, user_id, status)
    return ok([RunSummary.model_validate(r) for r in runs])


@router.get("/runs/{run_id}", response_model=ApiResult[RunDetail])
def get_run(run_id: int, db: Session = Depends(get_db)):
    run = service.get_run(db, run_id)
    return ok(RunDetail.model_validate(run))


@router.get("/runs/{run_id}/steps", response_model=ApiResult[list[RunStepResponse]])
def list_steps(run_id: int, db: Session = Depends(get_db)):
    steps = service.list_steps(db, run_id)
    return ok([RunStepResponse.model_validate(s) for s in steps])


@router.post("/runs/{run_id}/cancel", response_model=ApiResult[RunDetail])
def cancel_run(run_id: int, db: Session = Depends(get_db)):
    run = service.cancel_run(db, run_id)
    return ok(RunDetail.model_validate(run))