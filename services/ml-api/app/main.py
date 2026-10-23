from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from app.api.routes import router
from app.core.config import settings
from app.services.prediction import ModelUnavailableError

app = FastAPI(title="METIS Model Service", version=settings.version, debug=False)
app.include_router(router)


@app.exception_handler(ModelUnavailableError)
async def model_unavailable(_request: Request, _exc: ModelUnavailableError):
    return JSONResponse(status_code=503, content={"code": "MODEL_UNAVAILABLE", "message": "No trained model is loaded."})


@app.exception_handler(Exception)
async def internal_error(_request: Request, _exc: Exception):
    return JSONResponse(status_code=500, content={"code": "INTERNAL_ERROR", "message": "The service could not complete the request."})
