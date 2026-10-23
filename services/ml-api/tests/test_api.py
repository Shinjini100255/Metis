import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError
from app.main import app
from app.schemas.prediction import PredictionRequestV1
from app.services.features import daily_returns
from app.services.prediction import predict, ModelUnavailableError

client = TestClient(app)


def test_health():
    assert client.get("/health").json()["status"] == "UP"


def test_registry_reports_no_model():
    assert client.get("/models").json() == []
    status = client.get("/model-status").json()
    assert status["available"] is False
    assert status["model_version"] is None
    assert status["status"] == "NOT_TRAINED"


def test_schema_rejects_invalid_ticker_and_extra_fields():
    with pytest.raises(ValidationError):
        PredictionRequestV1(symbol="../../secrets", target_date="2026-01-01", model_version="v1")
    with pytest.raises(ValidationError):
        PredictionRequestV1(symbol="AAPL", target_date="2026-01-01", model_version="v1", arbitrary=True)


def test_predict_fails_without_a_model():
    with pytest.raises(ModelUnavailableError):
        predict(None)


def test_features():
    assert daily_returns([100, 110, 99]) == pytest.approx([0.1, -0.1])
    with pytest.raises(ValueError):
        daily_returns([0, 100])


def test_unknown_route_has_no_trace():
    response = client.get("/missing")
    assert response.status_code == 404
    assert "traceback" not in response.text.lower()


def test_openapi_available():
    assert "/model-status" in client.get("/openapi.json").json()["paths"]


def test_model_error_handler_returns_explicit_503():
    @app.get("/test-only-unavailable", include_in_schema=False)
    def unavailable():
        raise ModelUnavailableError("internal artifact path must not leak")
    response = client.get("/test-only-unavailable")
    assert response.status_code == 503
    assert response.json()["code"] == "MODEL_UNAVAILABLE"
    assert "artifact" not in response.text


def test_unexpected_exception_does_not_leak_details():
    @app.get("/test-only-error", include_in_schema=False)
    def error():
        raise RuntimeError("private token")
    safe_client = TestClient(app, raise_server_exceptions=False)
    response = safe_client.get("/test-only-error")
    assert response.status_code == 500
    assert "private" not in response.text
