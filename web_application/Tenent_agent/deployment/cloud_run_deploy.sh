#!/usr/bin/env bash
# TenantShield — Cloud Run Deployment
# =====================================
# Deploys all three production services to Google Cloud Run.
#
# Usage:
#   export GCP_PROJECT=your-project-id
#   export GOOGLE_API_KEY=your-gemini-api-key
#   bash deployment/cloud_run_deploy.sh

set -euo pipefail

PROJECT="${GCP_PROJECT:?Set GCP_PROJECT}"
REGION="${GCP_REGION:-us-east1}"

echo "=== TenantShield Cloud Run Deployment ==="
echo "Project: $PROJECT | Region: $REGION"

# 1. Data Agent
echo -e "\n[1/3] Deploying data-agent..."
gcloud run deploy tenantshield-data \
    --source . \
    --project "$PROJECT" --region "$REGION" \
    --allow-unauthenticated \
    --set-env-vars "GEOCLIENT_APP_ID=${GEOCLIENT_APP_ID:-},GEOCLIENT_APP_KEY=${GEOCLIENT_APP_KEY:-}" \
    --memory 512Mi --timeout 60s --min-instances 0 --max-instances 3

DATA_URL=$(gcloud run services describe tenantshield-data \
    --project "$PROJECT" --region "$REGION" --format 'value(status.url)')

# 2. Complaint Agent
echo -e "\n[2/3] Deploying complaint-agent..."
cd complaint_agent
gcloud run deploy tenantshield-complaint \
    --source . \
    --project "$PROJECT" --region "$REGION" \
    --allow-unauthenticated \
    --memory 256Mi --timeout 30s --min-instances 0 --max-instances 3
cd ..

COMPLAINT_URL=$(gcloud run services describe tenantshield-complaint \
    --project "$PROJECT" --region "$REGION" --format 'value(status.url)')

# 3. Orchestrator
echo -e "\n[3/3] Deploying orchestrator..."
cd orchestrator
gcloud run deploy tenantshield-orchestrator \
    --source . \
    --project "$PROJECT" --region "$REGION" \
    --allow-unauthenticated \
    --set-env-vars "DATA_AGENT_URL=${DATA_URL},COMPLAINT_AGENT_URL=${COMPLAINT_URL},GOOGLE_API_KEY=${GOOGLE_API_KEY:-}" \
    --memory 512Mi --timeout 300s --min-instances 0 --max-instances 5 \
    --session-affinity
cd ..

ORCH_URL=$(gcloud run services describe tenantshield-orchestrator \
    --project "$PROJECT" --region "$REGION" --format 'value(status.url)')

echo -e "\n=== Deployment Complete ==="
echo "Data Agent:      $DATA_URL"
echo "Complaint Agent: $COMPLAINT_URL"
echo "Orchestrator:    $ORCH_URL"
echo ""
echo "Android WebSocket URL (put in WebSocketManager.java):"
echo "  ${ORCH_URL/https/wss}/ws/inspect"
