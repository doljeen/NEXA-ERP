import uuid
from datetime import datetime
from enum import Enum

from sqlalchemy import Boolean, ForeignKey, String, Text, func
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from core.database import Base


class RunStatus(str, Enum):
    QUEUED = "QUEUED"
    RUNNING = "RUNNING"
    WAITING = "WAITING"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"
    CANCELED = "CANCELED"


FINISHED_STATUSES = {RunStatus.SUCCEEDED, RunStatus.FAILED, RunStatus.CANCELED}


def new_thread_id() -> str:
    return uuid.uuid4().hex


class Run(Base):
    # 테이블 기본 정보
    __tablename__ = "run"
    id: Mapped[int] = mapped_column(primary_key=True)
    workflow_id: Mapped[int] = mapped_column(ForeignKey("workflow.id"))
    version: Mapped[int | None]
    is_test: Mapped[bool] = mapped_column(Boolean, default=False)
    graph: Mapped[dict] = mapped_column(JSONB)
    inputs: Mapped[dict] = mapped_column(JSONB, default=dict)

    # Run 테이블 진행상태
    status: Mapped[str] = mapped_column(String(20), default=RunStatus.QUEUED)
    thread_id: Mapped[str] = mapped_column(String(64), unique=True, default=new_thread_id)
    current_node_id: Mapped[str | None] = mapped_column(String(50))
    outputs: Mapped[dict | None] = mapped_column(JSONB)
    error: Mapped[str | None] = mapped_column(Text)

    # Run 테이블 관계 관련
    requested_by: Mapped[str | None] = mapped_column(String(50))
    created_at: Mapped[datetime] = mapped_column(server_default=func.now())
    started_at: Mapped[datetime | None]
    finished_at: Mapped[datetime | None]

    steps: Mapped[list["RunStep"]] = relationship(
        back_populates="run",
        cascade="all, delete-orphan",
        order_by="RunStep.id",
    )


class RunStep(Base):
    __tablename__ = "run_step"

    id: Mapped[int] = mapped_column(primary_key=True)
    run_id: Mapped[int] = mapped_column(ForeignKey("run.id", ondelete="CASCADE"))
    node_id: Mapped[str] = mapped_column(String(50))
    node_type: Mapped[str] = mapped_column(String(50))
    status: Mapped[str] = mapped_column(String(20), default=RunStatus.RUNNING)
    branch: Mapped[str | None] = mapped_column(String(50))
    output: Mapped[dict | None] = mapped_column(JSONB)
    error: Mapped[str | None] = mapped_column(Text)
    started_at: Mapped[datetime] = mapped_column(server_default=func.now())
    finished_at: Mapped[datetime | None]

    run: Mapped["Run"] = relationship(back_populates="steps")