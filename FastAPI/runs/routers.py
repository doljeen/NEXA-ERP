from fastapi import APIRouter, BackgroundTasks, Depends, File, UploadFile
from sqlalchemy.orm import Session

from core.auth import AuthUser, get_current_user, get_user_id
from core.database import get_db
from core.response import ApiResult, ok
from core.exceptions import BusinessException, ErrorCode
from engine.runner import execute_run, resume_run
from runs import service
from runs.schemas import (
    ResumeRequest, 
    RunCreated, 
    RunCreateRequest, 
    RunDetail,
    RunFileResponse, 
    RunStepResponse, 
    RunSummary,
)

router = APIRouter(tags=["실행"])


@router.post("/workflows/{workflow_id}/runs", response_model=ApiResult[RunCreated], status_code=202)
def create_run(
    workflow_id: int,
    req: RunCreateRequest,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
    user: AuthUser = Depends(get_current_user),
):
    run = service.create_run(db, workflow_id, req, str(user.employee_id))
    background_tasks.add_task(execute_run, run.id, user.token)
    return ok({"run_id": run.id, "status": run.status})


@router.post("/workflows/{workflow_id}/test-runs", response_model=ApiResult[RunCreated], status_code=202)
def create_test_run(
    workflow_id: int,
    req: RunCreateRequest,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
    user: AuthUser = Depends(get_current_user),
):
    run = service.create_test_run(db, workflow_id, req, str(user.employee_id))
    background_tasks.add_task(execute_run, run.id, user.token)
    return ok({"run_id": run.id, "status": run.status})


@router.post("/runs/files", response_model=ApiResult[RunFileResponse], status_code=201)
def upload_file(
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
    user_id: str | None = Depends(get_user_id),
):
    run_file = service.upload_file(db, file, user_id)
    return ok(RunFileResponse.model_validate(run_file))


@router.get("/runs/files/{file_id}", response_model=ApiResult[RunFileResponse])
def get_file(file_id: int, db: Session = Depends(get_db)):
    run_file = service.get_file(db, file_id)
    return ok(RunFileResponse.model_validate(run_file))


@router.delete("/runs/files/{file_id}", response_model=ApiResult[None])
def delete_file(file_id: int, db: Session = Depends(get_db)):
    service.delete_file(db, file_id)
    return ok()


@router.get("/runs/me", response_model=ApiResult[list[RunSummary]])
def list_my_run(
    status: str | None = None,
    db: Session = Depends(get_db),
    user_id: str | None = Depends(get_user_id),
):
    runs = service.list_my_runs(db, user_id, status)
    return ok([RunSummary.model_validate(r) for r in runs])


@router.get("/runs/me/pending", response_model=ApiResult[list[RunSummary]])
def list_my_pending(
    db: Session = Depends(get_db),
    user_id: str | None = Depends(get_user_id), 
):
    runs = service.list_my_pending(db, user_id)
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


@router.post("/runs/{run_id}/resume", response_model=ApiResult[RunCreated], status_code=202)
def resume(
    run_id: int,
    req: ResumeRequest,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
    user: AuthUser = Depends(get_current_user),
):
    run = service.get_waiting_run(db, run_id)
    # 재개한 사람의 토큰으로 결재가 상신되므로 요청자 본인만 재개 가능 (관리자도 불가: 기안자가 바뀜)
    if run.requested_by != str(user.employee_id):
        raise BusinessException(ErrorCode.RUN_FORBIDDEN)
    background_tasks.add_task(resume_run, run.id, req.model_dump(), user.token)
    return ok({"run_id": run.id, "status": run.status})