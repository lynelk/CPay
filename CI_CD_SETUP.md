# CI/CD Setup Guide

This guide explains the automated Docker build and deployment pipeline for CPay.

## Overview

The CI/CD pipeline is organized into three GitHub Actions workflows:

1. **docker-build.yml** - Automatically builds and pushes images to GitHub Container Registry
2. **docker-build-pr.yml** - Validates Docker builds on pull requests
3. **deploy-kubernetes.yml** - Deploys to Kubernetes clusters (staging/production)

## Workflows

### 1. Docker Build & Push (`docker-build.yml`)

**Triggers:**
- Push to `main`, `develop`, or `release/**` branches
- Only runs when backend, frontend, compose, or nginx files change

**What it does:**
1. Builds backend Docker image (multi-stage, Alpine-based)
2. Builds frontend Docker image (Node.js + Vite)
3. Builds nginx reverse proxy image
4. Pushes all images to GitHub Container Registry (GHCR)
5. Scans images for vulnerabilities using Trivy
6. Tags images with:
   - Branch name (e.g., `main`, `develop`)
   - Git SHA
   - Semantic version tags (if pushing to main)
   - `latest` tag (for main branch only)

**Image Locations:**
```
ghcr.io/your-org/cpay/backend:latest
ghcr.io/your-org/cpay/frontend:latest
ghcr.io/your-org/cpay/nginx:latest
```

**Artifacts:**
- Trivy vulnerability scans (uploaded to GitHub Security)
- Built images available for deployment

### 2. Pull Request Docker Build (`docker-build-pr.yml`)

**Triggers:**
- Pull request to `main` or `develop`
- Only runs when backend, frontend, or compose files change

**What it does:**
1. Builds backend image (no push)
2. Builds frontend image (no push)
3. Validates `docker-compose.yml` configuration
4. Starts full stack locally (`mysql`, `backend`, `frontend`)
5. Runs health checks on all services
6. Posts build status comment on the PR

**Benefits:**
- Catches build errors before merge
- Validates full stack integration
- Ensures compose configuration is valid

### 3. Kubernetes Deployment (`deploy-kubernetes.yml`)

**Triggers:**
- Manual workflow dispatch (workflow_dispatch)
- Push to `main` branch (optional, can be customized)

**What it does:**
1. Creates/updates MySQL Deployment + PVC
2. Creates/updates Backend Deployment + Service
3. Creates/updates Frontend Deployment + Service
4. Creates/updates Ingress (with TLS via cert-manager)
5. Waits for rollout completion
6. Runs smoke tests (health checks)
7. Notifies on success/failure

**Deployment Strategies:**
- Rolling updates (maxSurge: 1, maxUnavailable: 0)
- Health checks (liveness + readiness probes)
- Resource limits (memory + CPU)
- Horizontal Pod Autoscaling (optional)

---

## Setup Instructions

### 1. Configure GitHub Secrets

Add these secrets to your repository (Settings → Secrets and variables → Actions):

#### For Docker Build (automatic)
No additional secrets needed — uses `GITHUB_TOKEN` automatically.

#### For Kubernetes Deployment
```
KUBE_CONFIG              # Base64-encoded kubeconfig file
MYSQL_ROOT_PASSWORD      # MySQL root password
MYSQL_PASSWORD           # MySQL 'cpay' user password
```

**Generate kubeconfig (base64):**
```bash
# If using EKS
aws eks update-kubeconfig --name my-cluster --region us-east-1
cat ~/.kube/config | base64

# If using GKE
gcloud container clusters get-credentials my-cluster --zone us-central1-a
cat ~/.kube/config | base64

# Generic
cat ~/.kube/config | base64
```

### 2. Configure GitHub Variables

Add these variables (Settings → Secrets and variables → Variables):

#### For PR Checks
None required — defaults used.

#### For Kubernetes Deployment
```
KUBE_NAMESPACE              # Kubernetes namespace (default: cpay)
BACKEND_REPLICAS            # Number of backend replicas (default: 2)
FRONTEND_REPLICAS           # Number of frontend replicas (default: 2)
CORS_ALLOWED_ORIGINS        # Comma-separated origins (e.g., https://cpay.example.com)
APP_BASE_URL                # Backend URL (e.g., https://cpay.example.com)
API_BASE_URL                # Frontend API URL (e.g., https://cpay.example.com/api)
INGRESS_HOST                # Domain name (e.g., cpay.example.com)
```

### 3. Create App Secrets in Kubernetes

After first deployment, create the app secrets:

```bash
kubectl create secret generic app-secrets \
  --from-literal=actuator-username=actuator \
  --from-literal=actuator-password='change-me-strong-password' \
  --from-literal=admin-api-username=admin \
  --from-literal=admin-api-password='change-me-strong-password' \
  --from-literal=callback-signing-secret='change-me-random-secret' \
  --from-literal=merchant-channel-encryption-key='32-byte-key-exactly-1234567890ab' \
  --from-literal=cpay-key-encryption-key='32-byte-key-exactly-1234567890ab' \
  -n cpay
```

---

## Usage

### Automatic Builds

Simply push to `main` or `develop`:
```bash
git commit -m "Update backend"
git push origin main
```

Images are automatically built and pushed to GHCR.

### Manual Deployment

1. Go to **Actions** → **Deploy to Production**
2. Click **Run workflow**
3. Select environment (staging/production)
4. Optionally override image tags
5. Click **Run workflow**

Or use GitHub CLI:
```bash
gh workflow run deploy-kubernetes.yml \
  -f environment=production
```

### Local Testing with Compose

Before pushing, test locally:
```bash
docker compose up --build

# In another terminal
curl http://localhost:3000           # Frontend
curl http://localhost:8081/actuator/health  # Backend
```

---

## Image Tagging Strategy

Images are tagged automatically based on the git ref:

| Trigger | Backend Tag | Frontend Tag |
|---------|-------------|--------------|
| `main` branch | `ghcr.io/.../backend:main` `ghcr.io/.../backend:latest` `ghcr.io/.../backend:sha-abc123` | Same for frontend |
| `develop` branch | `ghcr.io/.../backend:develop` `ghcr.io/.../backend:sha-def456` | Same for frontend |
| Tag `v1.2.3` | `ghcr.io/.../backend:v1.2.3` `ghcr.io/.../backend:1.2` `ghcr.io/.../backend:latest` | Same for frontend |

Use these tags in your deployments or compose files:
```yaml
services:
  backend:
    image: ghcr.io/your-org/cpay/backend:v1.2.3
  frontend:
    image: ghcr.io/your-org/cpay/frontend:v1.2.3
```

---

## Troubleshooting

### Images not pushing to GHCR

1. Check that GITHUB_TOKEN has `packages: write` permission (automatic in workflows)
2. Verify `docker-build.yml` trigger conditions
3. Check Actions → Workflows logs for errors

### Kubernetes deployment fails

1. Verify kubeconfig secret is valid: `echo $KUBE_CONFIG | base64 -d | kubectl --kubeconfig=- cluster-info`
2. Check namespace exists: `kubectl get namespace cpay`
3. Verify image pull secret was created: `kubectl get secret ghcr-secret -n cpay`
4. Check pod logs: `kubectl logs -n cpay deployment/backend`

### Health checks failing

1. Check backend logs: `kubectl logs -n cpay deployment/backend`
2. Verify MySQL is running: `kubectl get pod -n cpay -l app=mysql`
3. Check database connection: `kubectl exec -n cpay <backend-pod> -- curl localhost:8081/actuator/health`

### Stuck rollout

```bash
# Check rollout status
kubectl rollout status deployment/backend -n cpay

# Restart deployment
kubectl rollout restart deployment/backend -n cpay

# Check events
kubectl describe deployment backend -n cpay
```

---

## Advanced Configuration

### Custom Registries

To push to Docker Hub, ECR, or private registry instead of GHCR:

Edit `docker-build.yml`:
```yaml
env:
  REGISTRY: docker.io  # or 123456.dkr.ecr.us-east-1.amazonaws.com
  IMAGE_NAME: your-org/cpay
```

Add appropriate secrets for authentication.

### Autoscaling

Enable HPA in `deploy-kubernetes.yml`:
```yaml
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: backend-hpa
  namespace: cpay
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: backend
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

### Slack/Email Notifications

Add notification step to workflows:
```yaml
- name: Notify Slack
  if: always()
  uses: slackapi/slack-github-action@v1
  with:
    webhook-url: ${{ secrets.SLACK_WEBHOOK_URL }}
    payload: |
      {
        "text": "CPay build: ${{ job.status }}"
      }
```

---

## Security Best Practices

✅ **Enabled:**
- Image scanning with Trivy
- Non-root users in all images
- Secret management via GitHub Secrets
- RBAC in Kubernetes (namespace-scoped)
- Network policies (can be added)
- Pod security policies (can be added)

⚠️ **To add:**
- Image signing (cosign)
- SBOM generation (CycloneDX)
- Runtime security (Falco)
- Network policies for pod-to-pod communication
- Pod security standards enforcement

---

## Rollback Strategy

If deployment fails, rollback automatically:
```bash
kubectl rollout undo deployment/backend -n cpay
kubectl rollout undo deployment/frontend -n cpay
```

Or via GitHub Actions by deploying a previous image tag:
```
gh workflow run deploy-kubernetes.yml \
  -f image-backend=ghcr.io/.../backend:v1.2.2 \
  -f image-frontend=ghcr.io/.../frontend:v1.2.2
```

---

## Next Steps

1. Configure GitHub Secrets (KUBE_CONFIG, MySQL passwords)
2. Configure GitHub Variables (namespace, replicas, domain)
3. Push to `main` branch to trigger first build
4. Check Actions tab for build status
5. Run smoke tests locally: `docker compose up`
6. Deploy to staging: **Actions** → **Deploy to Production** → staging
7. Test, then promote to production

