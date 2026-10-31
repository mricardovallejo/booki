#!/usr/bin/env bash
# One-time: create the limited identity the ops agent runs as.
#
#   GCP_PROJECT_ID=booki-xxxxxx ./setup_service_account.sh
#
# Run it logged in as yourself (gcloud auth login). It creates:
#   * custom role `bookiOpsAgent`: read + update the Cloud Run service only
#     (no create, no delete, no IAM, no secrets);
#   * service account `booki-ops-agent` with that role + log/metric viewer;
#   * permission for YOU to impersonate it, so there is no key file to leak.
# Safe to re-run.
set -euo pipefail

PROJECT="${GCP_PROJECT_ID:?set GCP_PROJECT_ID}"
REGION="${GCP_REGION:-us-east1}"
SERVICE="${CLOUD_RUN_SERVICE:-booki-backend}"
SA_NAME="booki-ops-agent"
SA_EMAIL="${SA_NAME}@${PROJECT}.iam.gserviceaccount.com"
ROLE_ID="bookiOpsAgent"
ME="$(gcloud config get-value account 2>/dev/null)"

echo "Project: $PROJECT  Region: $REGION  Service: $SERVICE  You: $ME"

gcloud services enable iamcredentials.googleapis.com logging.googleapis.com --project "$PROJECT"

PERMS="run.services.get,run.services.list,run.services.update,\
run.revisions.get,run.revisions.list,\
run.configurations.get,run.configurations.list,\
run.routes.get,run.routes.list,run.locations.list"

if gcloud iam roles describe "$ROLE_ID" --project "$PROJECT" >/dev/null 2>&1; then
  gcloud iam roles update "$ROLE_ID" --project "$PROJECT" --permissions "$PERMS" --quiet
else
  gcloud iam roles create "$ROLE_ID" --project "$PROJECT" \
    --title "BooKI ops agent" \
    --description "Read and update the BooKI Cloud Run service; no create/delete" \
    --permissions "$PERMS" --stage GA
fi

gcloud iam service-accounts describe "$SA_EMAIL" --project "$PROJECT" >/dev/null 2>&1 || \
  gcloud iam service-accounts create "$SA_NAME" --project "$PROJECT" \
    --display-name "BooKI ops agent (limited)"

for ROLE in "projects/${PROJECT}/roles/${ROLE_ID}" roles/logging.viewer roles/monitoring.viewer; do
  gcloud projects add-iam-policy-binding "$PROJECT" \
    --member "serviceAccount:${SA_EMAIL}" --role "$ROLE" --condition=None --quiet >/dev/null
done

# Creating a revision (restart / instance limits) requires "act as" the runtime SA.
RUNTIME_SA="$(gcloud run services describe "$SERVICE" --region "$REGION" --project "$PROJECT" \
  --format='value(spec.template.spec.serviceAccountName)')"
if [ -z "$RUNTIME_SA" ]; then
  NUM="$(gcloud projects describe "$PROJECT" --format='value(projectNumber)')"
  RUNTIME_SA="${NUM}-compute@developer.gserviceaccount.com"
fi
gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA" --project "$PROJECT" \
  --member "serviceAccount:${SA_EMAIL}" --role roles/iam.serviceAccountUser --quiet >/dev/null

gcloud iam service-accounts add-iam-policy-binding "$SA_EMAIL" --project "$PROJECT" \
  --member "user:${ME}" --role roles/iam.serviceAccountTokenCreator --quiet >/dev/null

echo
echo "Done. Put this in ops-agent/.env:"
echo "  OPS_SERVICE_ACCOUNT=${SA_EMAIL}"
echo "IAM changes can take 1-2 minutes to apply."
