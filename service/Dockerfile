# Build from the repository root: docker build -f service/Dockerfile -t folio-api .
FROM python:3.13-slim
WORKDIR /app
COPY service/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt
COPY service /app/service
COPY android/api-contracts/prompts /app/android/api-contracts/prompts
RUN useradd --create-home --uid 10001 folio && mkdir -p /app/data && chown 10001:10001 /app/data
USER folio
VOLUME ["/app/data"]
EXPOSE 8000
CMD ["uvicorn", "service.app:create_app", "--factory", "--host", "0.0.0.0", "--port", "8000", "--workers", "1", "--no-access-log"]
