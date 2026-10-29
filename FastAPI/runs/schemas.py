from datetime import datetime

from core.schemas import CamelModel


class RunCreateRequest(CamelModel):
    inputs: dict = {}


class ResumeRequest(CamelModel):
    approved: bool
    comment: str | None = None


class RunCreated(CamelModel):
    run_id: int
    status: str


class RunSummary(CamelModel):
    id: int
    workflow_id: int
    version: int | None
    is_test: bool
    status: str
    current_node_id: str | None
    requested_by: str | None
    created_at: datetime
    started_at: datetime | None
    finished_at: datetime | None


class RunDetail(RunSummary):
    inputs: dict
    outputs: dict | None
    error: str | None


class RunStepResponse(CamelModel):
    id: int
    node_id: str
    node_type: str
    status: str
    branch: str | None
    output: dict | None
    error: str | None
    started_at: datetime
    finished_at: datetime | None
