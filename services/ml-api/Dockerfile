FROM python:3.12-slim AS build
WORKDIR /build
COPY requirements.txt requirements.lock.txt ./
RUN pip wheel --no-cache-dir --wheel-dir /wheels -r requirements.txt -c requirements.lock.txt
FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY --from=build /wheels /wheels
COPY requirements.txt requirements.lock.txt ./
RUN pip install --no-cache-dir --no-index --find-links=/wheels -r requirements.txt -c requirements.lock.txt && rm -r /wheels && useradd --create-home --uid 10001 metis
RUN mkdir -p /var/lib/metis/artifacts && chown -R metis:metis /var/lib/metis
ENV METIS_ARTIFACT_DIR=/var/lib/metis/artifacts
COPY app app
USER metis
EXPOSE 8000
CMD ["uvicorn","app.main:app","--host","0.0.0.0","--port","8000","--workers","1","--no-access-log"]
