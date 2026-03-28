FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY complaint_agent.py .
COPY hmc_citations.json .

ENV PORT=8080

CMD ["uvicorn", "complaint_agent:app", "--host", "0.0.0.0", "--port", "8080"]
