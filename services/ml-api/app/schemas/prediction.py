from datetime import date, datetime
from decimal import Decimal
from typing import Literal
from pydantic import BaseModel, Field, ConfigDict


class PredictionRequestV1(BaseModel):
    model_config = ConfigDict(extra="forbid")
    schema_version: Literal["1"] = "1"
    symbol: str = Field(pattern=r"^[A-Z][A-Z0-9.-]{0,14}$")
    target_date: date
    model_version: str = Field(min_length=1, max_length=64)


class PredictionResponseV1(BaseModel):
    schema_version: Literal["1"] = "1"
    symbol: str
    target_date: date
    model_version: str
    predicted_value: Decimal
    probability: float | None = Field(default=None, ge=0, le=1)
    created_at: datetime


class ModelStatus(BaseModel):
    available: bool = False
    status: Literal["NOT_TRAINED", "READY", "ARTIFACT_UNAVAILABLE"] = "NOT_TRAINED"
    model_version: str | None = None
    message: str = "No trained model is registered. Import sufficient local history and start training explicitly."
