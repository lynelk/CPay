# GitHub Actions Workflows - Quick Reference

## 📋 Workflows at a Glance

| Workflow | File | Trigger | Action |
|----------|------|---------|--------|
| **Docker Build & Push** | `docker-build.yml` | Push to main/develop/release/* | Build + push images to GHCR, scan with Trivy |
| **Docker Build (PR)** | `docker-build-pr.yml` | PR to main/develop | Build images, validate compose, test stack |
| **Deploy to Kubernetes** | `deploy-kubernetes.yml` | Manual or push to main | Deploy to K8s with rolling updates |

---

## 🚀 Quick Start

### 1. First Time Setup (5 minutes)

```bash
# Clone repo
git clone https://github.com/your-org/cpay.git
cd cpay

# Add GitHub secrets
gh secret set KUBE_CONFIG --body "$(base64 ~/.kube/config)"
gh secret set MYSQL_ROOT_PASSWORD --body "your-root-password"
gh secret set MYSQL_PASSWORD --body "your-cpay-password"

# Add GitHub variables
gh variable set KUBE_NAMESPACE --body "cpay"
gh variable set INGRESS_HOST --body "cpay.example.com"
gh variable set APP_BASE_URL --body "https://cpay.example.com"
```

### 2. Push to Trigger First Build

```bash
git add .
git commit -m "Initial containerization"
git push origin main
```

Check **Actions** tab in GitHub for build status.

### 3. Deploy to Staging

```bash
gh workflow run deploy-kubernetes.yml -f environment=staging
```

### 4. Verify Deployment

```bash
kubectl get pods -n cpay
kubectl logs -n cpay deployment/backend
```

---

## 📊 Workflow Status & History

Check workflow runs:
```bash
# List recent runs
gh run list --workflow=docker-build.yml

# Watch a specific run
gh run watch <run-id>

# View logs
gh run view <run-id> --log
```

---

## 🐳 Docker Images

### Push a Commit

```bash
git commit -m "Update"
git push origin main
```

**Result:** Images tagged as:
- `ghcr.io/your-org/cpay/backend:main`
- `ghcr.io/your-org/cpay/backend:latest`
- `ghcr.io/your-org/cpay/backend:sha-abc123`

### Pull & Test Locally

```bash
docker pull ghcr.io/your-org/cpay/backend:latest
docker run -it ghcr.io/your-org/cpay/backend:latest
```

### Push a Release

```bash
git tag v1.0.0
git push origin v1.0.0
```

**Result:** Images tagged as:
- `ghcr.io/your-org/cpay/backend:v1.0.0`
- `ghcr.io/your-org/cpay/backend:1.0`
- `ghcr.io/your-org/cpay/backend:latest`

---

## 🔄 Deployments

### Manual Deployment

```bash
# Deploy to staging
gh workflow run deploy-kubernetes.yml -f environment=staging

# Deploy specific images to production
gh workflow run deploy-kubernetes.yml \
  -f environment=production \
  -f image-backend=ghcr.io/your-org/cpay/backend:v1.0.0 \
  -f image-frontend=ghcr.io/your-org/cpay/frontend:v1.0.0
```

### Check Deployment Status

```bash
kubectl rollout status deployment/backend -n cpay
kubectl rollout status deployment/frontend -n cpay
kubectl get svc -n cpay
```

### View Logs

```bash
# Backend logs
kubectl logs -n cpay deployment/backend -f

# Frontend logs
kubectl logs -n cpay deployment/frontend -f

# MySQL logs
kubectl logs -n cpay deployment/mysql -f
```

### Rollback

```bash
kubectl rollout undo deployment/backend -n cpay
kubectl rollout undo deployment/frontend -n cpay
```

---

## ✅ PR Checks

When you open a PR:

1. ✅ Backend builds
2. ✅ Frontend builds
3. ✅ Docker Compose validates
4. ✅ Services start & health checks pass
5. ✅ PR commented with status

**Fix PR build failures:**
```bash
# Test locally
docker compose up --build

# Fix Dockerfile issues
# Commit and push
git commit -am "Fix Dockerfile"
git push origin feature-branch
```

---

## 🔍 Troubleshooting

### Build failed
```bash
# Check workflow logs
gh run list --workflow=docker-build.yml
gh run view <run-id> --log

# Run locally to debug
docker build -t cpay-backend ./InitializrSpringbootProjectFresh
```

### Deployment stuck
```bash
# Check pod status
kubectl get pods -n cpay
kubectl describe pod <pod-name> -n cpay

# Check events
kubectl get events -n cpay --sort-by='.lastTimestamp'

# Check logs
kubectl logs -n cpay deployment/backend
```

### Can't push images
```bash
# Verify auth token has write:packages permission
# Re-authenticate
gh auth login
gh workflow run docker-build.yml
```

---

## 📝 Environment Variables

### Backend (from GitHub Secrets)
```
ACTUATOR_USERNAME
ACTUATOR_PASSWORD
ADMIN_API_USERNAME
ADMIN_API_PASSWORD
CALLBACK_SIGNING_SECRET
MERCHANT_CHANNEL_ENCRYPTION_KEY
CPAY_KEY_ENCRYPTION_KEY
```

### Backend (from GitHub Variables)
```
CORS_ALLOWED_ORIGINS      # https://cpay.example.com,https://www.cpay.example.com
APP_BASE_URL              # https://cpay.example.com
CUSTOM_GATEWAYSTATE       # PRODUCTION
```

### Frontend (from GitHub Variables)
```
VITE_API_BASE_URL         # https://cpay.example.com/api
```

---

## 🔐 Secrets Management

### Add a Secret

```bash
gh secret set MY_SECRET --body "value"
gh secret set KUBE_CONFIG --body "$(cat ~/.kube/config | base64)"
```

### Remove a Secret

```bash
gh secret delete MY_SECRET
```

### List Secrets

```bash
gh secret list
```

### Use in Workflow

```yaml
env:
  DATABASE_PASSWORD: ${{ secrets.DATABASE_PASSWORD }}
```

---

## 📈 Monitoring

### Watch Build Progress

```bash
# Real-time workflow status
gh run watch

# View build times
gh run list --workflow=docker-build.yml --json conclusion,duration

# Compare image sizes
docker images | grep cpay
```

### Image Vulnerabilities

Check **Security** tab in GitHub for Trivy scan results.

Manually scan locally:
```bash
trivy image ghcr.io/your-org/cpay/backend:latest
```

---

## 🎯 Common Tasks

### Deploy after code review
```bash
# After PR approved & merged
gh run watch  # Watch the auto-triggered build
gh workflow run deploy-kubernetes.yml -f environment=staging
# Test staging
gh workflow run deploy-kubernetes.yml -f environment=production
```

### Hotfix production
```bash
git checkout -b hotfix/urgent-fix
# Make fix
git commit -am "Fix urgent issue"
git push origin hotfix/urgent-fix
# Create PR, review, merge to main
# Build & test automatically triggered
# Manual deploy to production
```

### Rollback production
```bash
# Get previous image tag
ghcr.io/your-org/cpay/backend:sha-previous-commit

# Deploy previous version
gh workflow run deploy-kubernetes.yml \
  -f environment=production \
  -f image-backend=ghcr.io/your-org/cpay/backend:sha-previous-commit
```

---

## 📞 Help

For detailed info, see:
- 📖 **CI_CD_SETUP.md** — Full setup guide
- 🐳 **DOCKER_SETUP.md** — Docker & Compose info
- 📜 **Contributing.md** — Development guidelines

---

**Next:** Configure secrets, push to main, and watch the build! 🚀
