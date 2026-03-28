FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy the orchestrator package
COPY __init__.py orchestrator/
COPY server.py orchestrator/
COPY tools.py orchestrator/
COPY prompt.py orchestrator/
COPY static/ orchestrator/static/

ENV PORT=8080
ENV DATA_AGENT_URL=http://data-agent:8080
ENV COMPLAINT_AGENT_URL=http://complaint-agent:8080

CMD ["uvicorn", "orchestrator.server:app", "--host", "0.0.0.0", "--port", "8080"]
