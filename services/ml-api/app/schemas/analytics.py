from datetime import date
from decimal import Decimal
from typing import Literal, Annotated
from pydantic import BaseModel, ConfigDict, Field

class Bar(BaseModel):
    model_config = ConfigDict(extra="ignore", allow_inf_nan=False)
    trading_date: date
    open: Decimal = Field(gt=0, max_digits=20, decimal_places=6)
    high: Decimal = Field(gt=0, max_digits=20, decimal_places=6)
    low: Decimal = Field(gt=0, max_digits=20, decimal_places=6)
    close: Decimal = Field(gt=0, max_digits=20, decimal_places=6)
    volume: int = Field(ge=0, le=9_000_000_000_000_000)

class HistoryRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    asset_id: int = Field(gt=0)
    symbol: str = Field(pattern=r"^[A-Z][A-Z0-9.\-]{0,14}$")
    provider: Literal["Alpha Vantage", "Local CSV", "TEST_FIXTURE"] = "Alpha Vantage"
    price_basis: Literal["UNADJUSTED"] = "UNADJUSTED"
    development_only: bool = False
    bars: list[dict] = Field(max_length=5000)

class TrainingConfig(BaseModel):
    model_config = ConfigDict(extra="forbid")
    folds: int = Field(default=3, ge=2, le=4)
    train_window: int = Field(default=160, ge=80, le=1500)
    test_window: int = Field(default=20, ge=10, le=100)
    holdout_size: int = Field(default=40, ge=20, le=200)
    seed: int = Field(default=17, ge=0, le=1_000_000)
    trees: int = Field(default=40, ge=10, le=80)
    max_depth: int = Field(default=4, ge=2, le=6)

class TrainingRequest(HistoryRequest):
    job_id: str = Field(pattern=r"^[0-9a-f]{8}-[0-9a-f-]{27}$")
    configuration: TrainingConfig = Field(default_factory=TrainingConfig)

class InferenceRequest(HistoryRequest):
    artifact_id: str = Field(pattern=r"^[0-9a-f]{8}-[0-9a-f-]{27}$")


from datetime import datetime
from typing import Any
from uuid import UUID

class TrainingJobResponse(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    job_id: UUID
    status: Literal["QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "TIMED_OUT", "INTERRUPTED"]
    started_at: datetime
    completed_at: datetime | None = None
    development_only: bool
    message: str
    code: str | None = None
    result: dict[str, Any] | None = None

class InferenceResponse(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    artifact_id: UUID
    task: Literal["classification", "regression"]
    predicted_value: float
    probability: float | None = Field(default=None, ge=0, le=1)
    predicted_direction: Literal[0, 1]
    predicted_return: float | None = None
    feature_date: date
    feature_timestamp: datetime
    created_at: datetime
    target_date: date
    target_horizon: Literal["NEXT_XNYS_SESSION"]
    data_age_seconds: int = Field(ge=0)
    status: Literal["FORWARD", "DEVELOPMENT_ONLY"]
    development_only: bool
    evaluation: dict[str, Any]
