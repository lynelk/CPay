#!/bin/bash
# Setup GitHub Actions secrets and variables for CI/CD
# Usage: ./scripts/setup-github-actions.sh

set -euo pipefail

REPO="${1:-.}"
GITHUB_USER="${GITHUB_USER:-}"
REGISTRY_USER="${REGISTRY_USER:-}"

echo "=========================================="
echo "CPay GitHub Actions Setup"
echo "=========================================="
echo ""

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo "❌ GitHub CLI (gh) not found. Install from: https://cli.github.com"
    exit 1
fi

# Get current repo info
REPO_FULL=$(gh repo view --json nameWithOwner --jq .nameWithOwner)
echo "📦 Repository: $REPO_FULL"
echo ""

# === SECRETS ===
echo "🔐 Setting up GitHub Secrets..."
echo ""

# Kubeconfig
read -p "Enter path to kubeconfig file (leave empty to skip): " KUBE_CONFIG_PATH
if [ -n "$KUBE_CONFIG_PATH" ]; then
    if [ -f "$KUBE_CONFIG_PATH" ]; then
        echo "Setting KUBE_CONFIG secret..."
        cat "$KUBE_CONFIG_PATH" | base64 -w 0 | gh secret set KUBE_CONFIG --repository "$REPO_FULL" --body -
        echo "✅ KUBE_CONFIG set"
    else
        echo "❌ Kubeconfig file not found: $KUBE_CONFIG_PATH"
    fi
fi

# MySQL passwords
read -sp "Enter MySQL root password: " MYSQL_ROOT_PASSWORD
echo ""
gh secret set MYSQL_ROOT_PASSWORD --repository "$REPO_FULL" --body "$MYSQL_ROOT_PASSWORD"
echo "✅ MYSQL_ROOT_PASSWORD set"

read -sp "Enter MySQL 'cpay' user password: " MYSQL_PASSWORD
echo ""
gh secret set MYSQL_PASSWORD --repository "$REPO_FULL" --body "$MYSQL_PASSWORD"
echo "✅ MYSQL_PASSWORD set"

# Optional: Slack webhook
read -p "Enter Slack webhook URL (leave empty to skip): " SLACK_WEBHOOK
if [ -n "$SLACK_WEBHOOK" ]; then
    gh secret set SLACK_WEBHOOK_URL --repository "$REPO_FULL" --body "$SLACK_WEBHOOK"
    echo "✅ SLACK_WEBHOOK_URL set"
fi

echo ""

# === VARIABLES ===
echo "📋 Setting up GitHub Variables..."
echo ""

# Kubernetes namespace
read -p "Enter Kubernetes namespace (default: cpay): " KUBE_NAMESPACE
KUBE_NAMESPACE="${KUBE_NAMESPACE:-cpay}"
gh variable set KUBE_NAMESPACE --repository "$REPO_FULL" --body "$KUBE_NAMESPACE"
echo "✅ KUBE_NAMESPACE set to: $KUBE_NAMESPACE"

# Replicas
read -p "Enter backend replicas (default: 2): " BACKEND_REPLICAS
BACKEND_REPLICAS="${BACKEND_REPLICAS:-2}"
gh variable set BACKEND_REPLICAS --repository "$REPO_FULL" --body "$BACKEND_REPLICAS"
echo "✅ BACKEND_REPLICAS set to: $BACKEND_REPLICAS"

read -p "Enter frontend replicas (default: 2): " FRONTEND_REPLICAS
FRONTEND_REPLICAS="${FRONTEND_REPLICAS:-2}"
gh variable set FRONTEND_REPLICAS --repository "$REPO_FULL" --body "$FRONTEND_REPLICAS"
echo "✅ FRONTEND_REPLICAS set to: $FRONTEND_REPLICAS"

# Domain and URLs
read -p "Enter ingress host / domain (e.g., cpay.example.com): " INGRESS_HOST
gh variable set INGRESS_HOST --repository "$REPO_FULL" --body "$INGRESS_HOST"
echo "✅ INGRESS_HOST set to: $INGRESS_HOST"

read -p "Enter app base URL (e.g., https://cpay.example.com): " APP_BASE_URL
gh variable set APP_BASE_URL --repository "$REPO_FULL" --body "$APP_BASE_URL"
echo "✅ APP_BASE_URL set to: $APP_BASE_URL"

read -p "Enter API base URL (e.g., https://cpay.example.com/api): " API_BASE_URL
gh variable set API_BASE_URL --repository "$REPO_FULL" --body "$API_BASE_URL"
echo "✅ API_BASE_URL set to: $API_BASE_URL"

read -p "Enter CORS allowed origins (comma-separated): " CORS_ALLOWED_ORIGINS
gh variable set CORS_ALLOWED_ORIGINS --repository "$REPO_FULL" --body "$CORS_ALLOWED_ORIGINS"
echo "✅ CORS_ALLOWED_ORIGINS set"

echo ""

# === ENVIRONMENTS ===
echo "🌍 Creating deployment environments..."

# Staging environment
gh api repos/"$REPO_FULL"/environments \
    -f name="staging" \
    -f description="Staging environment" \
    2>/dev/null || echo "ℹ️  Staging environment may already exist"
echo "✅ Staging environment ready"

# Production environment
gh api repos/"$REPO_FULL"/environments \
    -f name="production" \
    -f description="Production environment" \
    2>/dev/null || echo "ℹ️  Production environment may already exist"
echo "✅ Production environment ready"

echo ""

# === SUMMARY ===
echo "=========================================="
echo "✅ Setup Complete!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "1. Create GitHub environments (if not exists):"
echo "   - Settings → Environments → New environment"
echo "   - Create 'staging' and 'production'"
echo ""
echo "2. Add environment protection rules (optional):"
echo "   - Settings → Environments → Production"
echo "   - Required reviewers: (select team members)"
echo "   - Deployment branches: Only allow main"
echo ""
echo "3. Test the pipeline:"
echo "   - git commit --allow-empty -m 'Trigger CI/CD'"
echo "   - git push origin main"
echo "   - Check GitHub Actions tab"
echo ""
echo "4. Deploy:"
echo "   - After build succeeds, run:"
echo "   - gh workflow run deploy-kubernetes.yml -f environment=staging"
echo ""
echo "Documentation:"
echo "  - 📖 CI_CD_SETUP.md — Full setup guide"
echo "  - 🚀 CI_CD_QUICKSTART.md — Quick reference"
echo ""
