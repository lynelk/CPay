# Pre-Deployment Checklist

## Before First Deployment

### Local Testing
- [ ] `docker compose up` starts all services
- [ ] Frontend loads at http://localhost:3000
- [ ] Backend API responds at http://localhost:8081/actuator/health
- [ ] MySQL connects on localhost:3307
- [ ] No errors in logs

### Code Quality
- [ ] Backend tests pass: `mvn test`
- [ ] Frontend tests pass: `npm test`
- [ ] Code linting passes: `npm run lint`
- [ ] TypeScript checks pass: `npm run typecheck`
- [ ] OWASP dependencies scanned
- [ ] CodeQL security analysis passes

### Docker Images
- [ ] Backend Dockerfile builds: `docker build -t cpay-backend ./InitializrSpringbootProjectFresh`
- [ ] Frontend Dockerfile builds: `docker build -t cpay-frontend ./Clientside`
- [ ] Images run locally without errors
- [ ] Multi-stage builds produce minimal final images
- [ ] Non-root users configured in images

### GitHub Actions Setup
- [ ] Repository secrets configured:
  - [ ] `KUBE_CONFIG` (kubeconfig in base64)
  - [ ] `MYSQL_ROOT_PASSWORD`
  - [ ] `MYSQL_PASSWORD`
- [ ] GitHub variables configured:
  - [ ] `KUBE_NAMESPACE`
  - [ ] `INGRESS_HOST`
  - [ ] `APP_BASE_URL`
  - [ ] `API_BASE_URL`
  - [ ] `CORS_ALLOWED_ORIGINS`
- [ ] Workflows enabled in Actions tab
- [ ] Branch protection rules set (if desired)

### Kubernetes Setup
- [ ] Kubernetes cluster accessible: `kubectl cluster-info`
- [ ] Kubeconfig valid and authenticated
- [ ] Cluster has sufficient resources (CPU/memory)
- [ ] Namespace created (or can be auto-created)
- [ ] Ingress controller deployed (nginx-ingress or similar)
- [ ] cert-manager installed (for TLS certificates)
- [ ] DNS configured (or /etc/hosts entry for testing)

### Database
- [ ] MySQL user `cpay` can connect to cluster database
- [ ] Flyway migrations path correct
- [ ] Database credentials securely stored in Kubernetes secrets

---

## Staging Deployment

### Pre-Deploy
- [ ] All code merged to `develop` branch
- [ ] Code review completed
- [ ] CI/CD pipeline passed (green checkmarks)
- [ ] No blocking security vulnerabilities in scan results
- [ ] Staging environment exists in GitHub

### Deploy
```bash
make deploy-staging
# or
gh workflow run deploy-kubernetes.yml -f environment=staging
```

### Post-Deploy Tests
- [ ] Pods are running: `kubectl get pods -n cpay`
- [ ] Services are healthy: `kubectl get svc -n cpay`
- [ ] Backend health check passes: `curl https://staging.cpay.example.com/actuator/health`
- [ ] Frontend loads: `curl https://staging.cpay.example.com/`
- [ ] Database migrations completed
- [ ] No errors in pod logs: `kubectl logs -n cpay deployment/backend`

### Smoke Tests
- [ ] Login works (admin + merchant)
- [ ] Create a test transaction
- [ ] Check transaction status
- [ ] Generate a report
- [ ] Admin dashboard loads
- [ ] Merchant dashboard loads

### Performance/Load Tests (Optional)
- [ ] Response times acceptable
- [ ] Database queries optimize
- [ ] Memory usage stable
- [ ] No OOM errors after 1 hour

---

## Production Deployment

### Pre-Deploy Checklist
- [ ] Staging validation complete and passed
- [ ] Release notes prepared
- [ ] Rollback plan documented
- [ ] On-call team notified
- [ ] Maintenance window scheduled (if needed)
- [ ] All code merged to `main` branch
- [ ] Git tags created: `git tag v1.2.3 && git push origin v1.2.3`
- [ ] No blocking security vulnerabilities
- [ ] Database migration strategy reviewed
- [ ] Production secrets verified in GitHub Secrets

### Deploy
```bash
make deploy-prod
# or manually
gh workflow run deploy-kubernetes.yml -f environment=production
```

### During Deployment
- [ ] Monitor rolling updates: `kubectl rollout status deployment/backend -n cpay`
- [ ] Check pod events: `kubectl describe pod <pod-name> -n cpay`
- [ ] Watch logs live: `kubectl logs -n cpay deployment/backend -f`
- [ ] No pods in CrashLoopBackOff state

### Post-Deploy Verification
- [ ] All replicas are running and ready
- [ ] Services are healthy
- [ ] Ingress routing correctly
- [ ] TLS certificates valid
- [ ] Database migrations completed without errors
- [ ] Health checks passing
- [ ] Metrics are being collected

### Smoke Tests
- [ ] Admin login works
- [ ] Merchant login works
- [ ] Create a test transaction (small amount)
- [ ] Transaction processing works end-to-end
- [ ] Reports generate
- [ ] Webhooks fire correctly
- [ ] Error handling works
- [ ] Rate limiting works
- [ ] Admin console responsive

### Monitoring & Alerts
- [ ] Prometheus scraping metrics
- [ ] Grafana dashboards showing data
- [ ] Error rate within thresholds
- [ ] Database connections healthy
- [ ] Memory/CPU within limits
- [ ] Disk space adequate
- [ ] Network latency acceptable

### Communication
- [ ] Stakeholders notified of deployment
- [ ] Slack notification sent
- [ ] On-call team acknowledged
- [ ] Deployment tracked in issue/ticket

---

## Rollback Plan (If Needed)

### When to Rollback
- [ ] Critical bugs preventing login
- [ ] Data loss or corruption
- [ ] Complete service unavailability
- [ ] Security vulnerability discovered
- [ ] Performance degradation >20%

### Automatic Rollback
```bash
# Fastest: Kubernetes automatic
kubectl rollout undo deployment/backend -n cpay
kubectl rollout undo deployment/frontend -n cpay

# Wait for rollout
kubectl rollout status deployment/backend -n cpay
```

### Manual Rollback (GitHub Actions)
```bash
# Get previous image tag
gh run list --workflow=docker-build.yml --limit=5

# Deploy previous version
gh workflow run deploy-kubernetes.yml \
  -f environment=production \
  -f image-backend=ghcr.io/your-org/cpay/backend:v1.2.2 \
  -f image-frontend=ghcr.io/your-org/cpay/frontend:v1.2.2
```

### Verify Rollback
- [ ] Old version running
- [ ] Services healthy
- [ ] No errors in logs
- [ ] Functionality restored

---

## Post-Deployment

### Day 1
- [ ] Monitor error rates every hour
- [ ] Check database replication lag (if applicable)
- [ ] Verify backup jobs completed
- [ ] No unexpected alerts

### Week 1
- [ ] Performance metrics baseline established
- [ ] No critical issues reported by users
- [ ] Database statistics updated
- [ ] Logs retention policy applied

### Weekly
- [ ] Performance trending stable
- [ ] Security scans clean
- [ ] Backup verification
- [ ] Dependency updates reviewed

---

## Troubleshooting Reference

### Pod stuck in CrashLoopBackOff
```bash
kubectl logs -n cpay deployment/backend
kubectl describe pod <pod-name> -n cpay
# Check: env vars, secrets, resource limits, database connection
```

### Services not responding
```bash
kubectl get svc -n cpay
kubectl get endpoints -n cpay
# Check: service selectors, pod labels, network policies
```

### Database connection issues
```bash
kubectl exec -n cpay <backend-pod> -- \
  java -cp app/cpay-backend.jar ... -c "test mysql connection"
# Verify: DB_URL, credentials, MySQL pod running
```

### Ingress not routing
```bash
kubectl describe ingress cpay-ingress -n cpay
kubectl get events -n cpay
# Check: ingress class, cert-manager, DNS resolution
```

### Image pull errors
```bash
kubectl describe pod <pod-name> -n cpay
# Check: imagePullSecrets, registry credentials, image exists
```

---

## Deployment Success Criteria

✅ **Deployment is successful when:**

- All pods are Running and Ready (1/1)
- All services are accessible
- Ingress resolves correctly
- TLS certificate valid
- Health checks passing
- No errors in logs for 5 minutes
- Smoke tests pass
- Performance metrics acceptable
- No security vulnerabilities
- Backup completed successfully

✅ **Ready to declare completion:**

- Stakeholders notified
- Monitoring alerts confirmed
- On-call team ready
- Documentation updated

---

## Emergency Contacts

- **On-Call:** [Team Slack channel]
- **Database Admin:** [Contact]
- **Platform Team:** [Contact]
- **Security Team:** [Contact]

---

## Change Log

| Date | Version | Status | Notes |
|------|---------|--------|-------|
| YYYY-MM-DD | v1.0.0 | Staged | Initial deployment |
| YYYY-MM-DD | v1.0.1 | Prod | Bugfix rollout |
|  |  |  |  |

