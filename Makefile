.PHONY: help build test deploy deploy-staging deploy-prod logs rollback clean

# Colors
GREEN  := \033[0;32m
YELLOW := \033[0;33m
RED    := \033[0;31m
NC     := \033[0m # No Color

help:
	@echo "$(GREEN)CPay Docker & CI/CD Commands$(NC)"
	@echo ""
	@echo "$(YELLOW)Docker Build & Test:$(NC)"
	@echo "  make build              - Build all Docker images locally"
	@echo "  make test               - Run tests in containers"
	@echo "  make up                 - Start full stack locally (compose)"
	@echo "  make down               - Stop full stack"
	@echo ""
	@echo "$(YELLOW)CI/CD Setup:$(NC)"
	@echo "  make setup-ci           - Interactive setup for GitHub Actions"
	@echo "  make secrets            - List GitHub secrets"
	@echo "  make variables          - List GitHub variables"
	@echo ""
	@echo "$(YELLOW)Deployment (Kubernetes):$(NC)"
	@echo "  make deploy-staging     - Deploy to staging environment"
	@echo "  make deploy-prod        - Deploy to production environment"
	@echo "  make logs-backend       - Show backend logs"
	@echo "  make logs-frontend      - Show frontend logs"
	@echo "  make rollback-backend   - Rollback backend deployment"
	@echo "  make rollback-frontend  - Rollback frontend deployment"
	@echo ""
	@echo "$(YELLOW)Utilities:$(NC)"
	@echo "  make clean              - Clean up Docker images & volumes"
	@echo "  make status             - Check deployment status"
	@echo "  make shell-backend      - Open shell in backend pod"
	@echo ""

# ===== Docker =====

build:
	@echo "$(GREEN)Building Docker images...$(NC)"
	docker compose build

test:
	@echo "$(GREEN)Running tests...$(NC)"
	docker compose run --rm backend mvn test -DskipTests=false
	docker compose run --rm frontend npm test -- --run

up:
	@echo "$(GREEN)Starting full stack...$(NC)"
	docker compose up --pull always

down:
	@echo "$(YELLOW)Stopping services...$(NC)"
	docker compose down

# ===== CI/CD Setup =====

setup-ci:
	@echo "$(GREEN)Setting up GitHub Actions...$(NC)"
	bash scripts/setup-github-actions.sh

secrets:
	@echo "$(GREEN)GitHub Secrets:$(NC)"
	gh secret list

variables:
	@echo "$(GREEN)GitHub Variables:$(NC)"
	gh variable list

# ===== Deployments =====

deploy-staging:
	@echo "$(GREEN)Deploying to staging...$(NC)"
	gh workflow run deploy-kubernetes.yml -f environment=staging
	@echo "$(YELLOW)Workflow started. Check progress:$(NC)"
	@echo "  gh run list --workflow=deploy-kubernetes.yml"

deploy-prod:
	@echo "$(RED)⚠️  Deploying to PRODUCTION$(NC)"
	@read -p "Are you sure? [y/N] " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		gh workflow run deploy-kubernetes.yml -f environment=production; \
		echo "$(YELLOW)Workflow started. Check progress:$(NC)"; \
		echo "  gh run list --workflow=deploy-kubernetes.yml"; \
	fi

# ===== Logs =====

logs-backend:
	@echo "$(GREEN)Backend logs (live):$(NC)"
	kubectl logs -n cpay deployment/backend -f --tail=100

logs-frontend:
	@echo "$(GREEN)Frontend logs (live):$(NC)"
	kubectl logs -n cpay deployment/frontend -f --tail=100

logs-mysql:
	@echo "$(GREEN)MySQL logs:$(NC)"
	kubectl logs -n cpay deployment/mysql -f --tail=50

# ===== Rollback =====

rollback-backend:
	@echo "$(RED)Rolling back backend...$(NC)"
	kubectl rollout undo deployment/backend -n cpay
	@echo "$(GREEN)Rollback initiated$(NC)"

rollback-frontend:
	@echo "$(RED)Rolling back frontend...$(NC)"
	kubectl rollout undo deployment/frontend -n cpay
	@echo "$(GREEN)Rollback initiated$(NC)"

# ===== Status =====

status:
	@echo "$(GREEN)Deployment Status:$(NC)"
	kubectl get deployments -n cpay
	@echo ""
	@echo "$(GREEN)Pod Status:$(NC)"
	kubectl get pods -n cpay
	@echo ""
	@echo "$(GREEN)Services:$(NC)"
	kubectl get svc -n cpay

shell-backend:
	@echo "$(GREEN)Opening shell in backend pod...$(NC)"
	@BACKEND_POD=$$(kubectl get pod -n cpay -l app=backend -o jsonpath='{.items[0].metadata.name}'); \
	kubectl exec -it -n cpay $$BACKEND_POD -- /bin/sh

shell-frontend:
	@echo "$(GREEN)Opening shell in frontend pod...$(NC)"
	@FRONTEND_POD=$$(kubectl get pod -n cpay -l app=frontend -o jsonpath='{.items[0].metadata.name}'); \
	kubectl exec -it -n cpay $$FRONTEND_POD -- /bin/sh

# ===== Utilities =====

clean:
	@echo "$(RED)Cleaning up Docker resources...$(NC)"
	docker compose down -v
	docker system prune -f --filter "label=app=cpay"
	@echo "$(GREEN)Cleanup complete$(NC)"

port-forward-backend:
	@echo "$(GREEN)Port forwarding backend (localhost:8081)...$(NC)"
	kubectl port-forward -n cpay svc/backend 8081:8081

port-forward-frontend:
	@echo "$(GREEN)Port forwarding frontend (localhost:3000)...$(NC)"
	kubectl port-forward -n cpay svc/frontend 3000:3000

# ===== Workflow Info =====

workflows:
	@echo "$(GREEN)Available Workflows:$(NC)"
	gh workflow list --all

watch-build:
	@echo "$(GREEN)Watching Docker build workflow...$(NC)"
	gh run list --workflow=docker-build.yml --limit=1
	@read -p "Enter run ID to watch (or press Enter for latest): " RUN_ID; \
	if [ -z "$$RUN_ID" ]; then \
		RUN_ID=$$(gh run list --workflow=docker-build.yml --limit=1 --json databaseId -q '.[0].databaseId'); \
	fi; \
	gh run watch $$RUN_ID

# ===== Documentation =====

docs:
	@echo "$(GREEN)CI/CD Documentation:$(NC)"
	@echo "  📖 CI_CD_SETUP.md — Complete setup guide"
	@echo "  🚀 CI_CD_QUICKSTART.md — Quick reference"
	@echo "  🐳 DOCKER_SETUP.md — Docker & Compose info"
	@echo ""
	@echo "Open docs: open CI_CD_SETUP.md"
