FROM python:3.11-slim

WORKDIR /app

RUN pip install --no-cache-dir fastapi uvicorn[standard] websockets pydantic

COPY mock_server.py .

ENV PORT=8765

CMD ["uvicorn", "mock_server:app", "--host", "0.0.0.0", "--port", "8765"]
