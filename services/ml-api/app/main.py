from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from app.api.routes import router
from app.core.config import settings
from app.services.prediction import ModelUnavailableError

from contextlib import asynccontextmanager
from app.services.jobs import recover

@asynccontextmanager
async def lifespan(_app):
    recover()
    yield

app = FastAPI(lifespan=lifespan,title="METIS Model Service", version=settings.version, debug=False)
app.include_router(router)


@app.exception_handler(ModelUnavailableError)
async def model_unavailable(_request: Request, _exc: ModelUnavailableError):
    return JSONResponse(status_code=503, content={"code": "MODEL_UNAVAILABLE", "message": "No trained model is loaded."})


@app.exception_handler(Exception)
async def internal_error(_request: Request, _exc: Exception):
    return JSONResponse(status_code=500, content={"code": "INTERNAL_ERROR", "message": "The service could not complete the request."})


from app.core.errors import DataProblem

@app.exception_handler(DataProblem)
async def data_problem(_request: Request, exc: DataProblem):
    return JSONResponse(status_code=exc.status, content={"code": exc.code, "message": exc.message})
