# SmartBank CI/CD Setup Guide

Complete step-by-step guide to configure GitHub Actions, Docker Hub, SonarCloud, and your
staging/production servers for the SmartBank pipeline.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Docker Hub Setup](#2-docker-hub-setup)
3. [SonarCloud Setup](#3-sonarcloud-setup)
4. [GitHub Repository Secrets (CI)](#4-github-repository-secrets-ci)
5. [GitHub Environments](#5-github-environments)
6. [Staging Environment Secrets](#6-staging-environment-secrets)
7. [Production Environment Secrets](#7-production-environment-secrets)
8. [Server Setup — Staging](#8-server-setup--staging)
9. [Server Setup — Production](#9-server-setup--production)
10. [SSH Key Generation](#10-ssh-key-generation)
11. [How the Pipeline Works](#11-how-the-pipeline-works)
12. [Day-to-Day Operations](#12-day-to-day-operations)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. Prerequisites

Before starting, make sure you have:

- A GitHub repository with this project pushed to it
- A Docker Hub account — [hub.docker.com](https://hub.docker.com)
- A SonarCloud account — [sonarcloud.io](https://sonarcloud.io) (free, sign in with GitHub)
- Two Linux servers (staging + production) with SSH access
- `openssl` and `ssh-keygen` available on your local machine

---

## 2. Docker Hub Setup

### 2.1 Create a Docker Hub Access Token

> Use a token, not your account password. Tokens can be revoked independently.

1. Log in to [hub.docker.com](https://hub.docker.com)
2. Click your avatar (top-right) → **Account Settings**
3. Go to **Security → New Access Token**
4. Name it `smartbank-ci`
5. Set permission to **Read, Write, Delete**
6. Click **Generate**
7. **Copy the token immediately** — it is shown only once

### 2.2 What images will be pushed

The CI pipeline pushes one image per service using this naming pattern:

```
{DOCKERHUB_USERNAME}/smartbank-{service}:{branch}-{git-sha8}
{DOCKERHUB_USERNAME}/smartbank-{service}:latest
```

Example:

```
johndoe/smartbank-auth-service:main-a1b2c3d4
johndoe/smartbank-api-gateway:latest
```

Services: `config-server`, `discovery-server`, `api-gateway`, `auth-service`,
`account-service`, `transaction-service`, `notification-service`

---

## 3. SonarCloud Setup

### 3.1 Import your project

1. Go to [sonarcloud.io](https://sonarcloud.io) and **Sign in with GitHub**
2. Click **+** (top-right) → **Analyze new project**
3. Select your GitHub organization → find `smart-banking` → click **Set Up**
4. Choose **Free plan**
5. SonarCloud will auto-detect it as a Gradle project

### 3.2 Get your SONAR_ORG

After import, your organization slug appears in the URL:

```
https://sonarcloud.io/organizations/YOUR-ORG-SLUG/projects
                                    ^^^^^^^^^^^^^^^^
                                    this is SONAR_ORG
```

### 3.3 Get your SONAR_PROJECT_KEY

On your project page:
- Go to **Administration → Update Key**
- The key shown is your `SONAR_PROJECT_KEY`
- It typically looks like: `your-org_smart-banking`

### 3.4 Generate a SONAR_TOKEN

1. SonarCloud → your avatar → **My Account**
2. Go to **Security** tab
3. Under **Generate Tokens**, enter name `smartbank-ci` → click **Generate**
4. **Copy the token** — shown only once

### 3.5 Disable automatic analysis (important)

Since we run analysis via Gradle in CI, disable SonarCloud's own auto-analysis:

1. Project → **Administration → Analysis Method**
2. Turn off **Automatic Analysis**

---

## 4. GitHub Repository Secrets (CI)

These secrets are available to all workflows regardless of environment.

**Navigation:** `Your repo → Settings → Secrets and variables → Actions → New repository secret`

### Secrets to add

| Secret Name | Value | How to get it |
|---|---|---|
| `DOCKERHUB_USERNAME` | Your Docker Hub username | Your Docker Hub login name |
| `DOCKERHUB_TOKEN` | Docker Hub access token | [Section 2.1](#21-create-a-docker-hub-access-token) |
| `SONAR_TOKEN` | SonarCloud user token | [Section 3.4](#34-generate-a-sonar_token) |
| `SONAR_PROJECT_KEY` | SonarCloud project key | [Section 3.3](#33-get-your-sonar_project_key) |
| `SONAR_ORG` | SonarCloud organization slug | [Section 3.2](#32-get-your-sonar_org) |

### Screenshot path

```
GitHub repo
└── Settings
    └── Secrets and variables
        └── Actions
            └── Repository secrets
                ├── DOCKERHUB_USERNAME  ✓
                ├── DOCKERHUB_TOKEN     ✓
                ├── SONAR_TOKEN         ✓
                ├── SONAR_PROJECT_KEY   ✓
                └── SONAR_ORG          ✓
```

---

## 5. GitHub Environments

GitHub Environments allow per-environment secrets and protection rules (approval gates).

**Navigation:** `Your repo → Settings → Environments → New environment`

### 5.1 Create the `staging` environment

1. Click **New environment**
2. Name it exactly: `staging`
3. Click **Configure environment**
4. **No protection rules needed** — staging deploys automatically after CI passes
5. Add secrets (see [Section 6](#6-staging-environment-secrets))

### 5.2 Create the `production` environment

1. Click **New environment**
2. Name it exactly: `production`
3. Click **Configure environment**
4. Under **Deployment protection rules**:
   - Check **Required reviewers**
   - Add yourself + at least one teammate as reviewers
5. Under **Deployment branches**:
   - Select **Selected branches**
   - Add rule: `main`
6. Add secrets (see [Section 7](#7-production-environment-secrets))

> The `production` environment name in the workflow (`environment: production`) must exactly
> match what you created here, or the approval gate will not activate.

---

## 6. Staging Environment Secrets

**Navigation:** `Settings → Environments → staging → Add secret`

### Kubernetes secrets

| Secret | What to put | Example |
|---|---|---|
| `STAGING_KUBECONFIG` | Base64-encoded kubeconfig for the staging K8s cluster | See [Section 10](#10-kubeconfig-setup) |

### Runtime secrets (base64-encoded for Helm)

| Secret | Notes |
|---|---|
| `DB_PASSWORD_B64` | Base64-encoded PostgreSQL password |
| `JWT_SECRET_B64` | Base64-encoded JWT secret (≥ 256 bits) |
| `CONFIG_PASSWORD_B64` | Base64-encoded Config Server password |
| `REDIS_PASSWORD_B64` | Base64-encoded Redis password |
| `SMTP_PASSWORD_B64` | Base64-encoded SMTP password |

### Runtime secrets (plaintext for Bitnami subcharts)

| Secret | Notes |
|---|---|
| `POSTGRES_PASSWORD` | PostgreSQL password (plaintext — Bitnami chart handles encoding) |
| `REDIS_PASSWORD` | Redis password (plaintext) |
| `GRAFANA_PASSWORD` | Grafana admin password (plaintext) |

### Generate secrets

```bash
# JWT secret
openssl rand -base64 64

# Base64-encode for Helm
echo -n 'YOUR_PASSWORD' | base64
```

### Generate JWT_SECRET

```bash
openssl rand -base64 64
# Copy the entire output including any = padding
```

---

## 7. Production Environment Secrets

**Navigation:** `Settings → Environments → production → Add secret`

### Kubernetes secrets

| Secret | What to put |
|---|---|
| `PROD_KUBECONFIG` | Base64-encoded kubeconfig for the production K8s cluster (separate from staging) |

### Runtime secrets (base64-encoded for Helm)

| Secret | Notes |
|---|---|
| `DB_PASSWORD_B64` | **Different from staging** — base64-encoded strong password |
| `JWT_SECRET_B64` | **Different from staging** — tokens issued in staging must not work in prod |
| `CONFIG_PASSWORD_B64` | **Different from staging** |
| `REDIS_PASSWORD_B64` | **Different from staging** |
| `SMTP_PASSWORD_B64` | Can reuse the same provider; ideally a separate API key |

### Runtime secrets (plaintext for Bitnami subcharts)

| Secret | Notes |
|---|---|
| `POSTGRES_PASSWORD` | **Different from staging** |
| `REDIS_PASSWORD` | **Different from staging** |
| `GRAFANA_PASSWORD` | **Different from staging** |

> **Rule:** Never share credentials between environments. A compromised staging secret
> must not grant any access to production.

---

## 8. Kubernetes Cluster Setup — Staging

The CD pipeline deploys via `helm upgrade --install` using a kubeconfig stored as a GitHub secret. You need a K8s cluster accessible from GitHub Actions.

### 8.1 Cluster Options

| Option | Use Case |
|---|---|
| **Managed K8s** (EKS, GKE, AKS) | Production-grade, auto-managed control plane |
| **Self-hosted** (kubeadm, k3s) | On-premise or VM-based |
| **Local** (minikube, kind) | Development & testing only |

### 8.2 Cluster Requirements

- Kubernetes v1.28+
- NGINX Ingress Controller installed
- Metrics Server (for HPA autoscaling)
- Sufficient resources: ~4 CPU, ~8GB RAM minimum

### 8.3 Install NGINX Ingress Controller

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.1/deploy/static/provider/cloud/deploy.yaml
```

### 8.4 Install Metrics Server (if not present)

```bash
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

---

## 9. Kubernetes Cluster Setup — Production

Same requirements as staging but with:

- **Separate cluster** from staging (or at minimum, separate namespace with RBAC)
- **Node autoscaling** enabled (if cloud-managed)
- **Persistent storage class** configured (gp3, ssd, etc.)
- **Network policies** to restrict inter-namespace traffic
- **Pod security standards** enforced

### 9.1 Production Hardening

```bash
# Create a dedicated service account for CI/CD with limited permissions
kubectl create serviceaccount smartbank-deployer -n smartbank
kubectl create rolebinding smartbank-deployer-binding \
  --clusterrole=admin \
  --serviceaccount=smartbank:smartbank-deployer \
  -n smartbank
```

---

## 10. Kubeconfig Setup

The CD pipeline needs a kubeconfig to authenticate with each cluster.

### 10.1 Export Kubeconfig

```bash
# For managed clusters — use provider CLI
aws eks update-kubeconfig --name smartbank-staging --region us-east-1
gcloud container clusters get-credentials smartbank-staging --zone us-central1-a
az aks get-credentials --resource-group smartbank --name smartbank-staging

# For local/self-hosted
cat ~/.kube/config
```

### 10.2 Base64-Encode for GitHub Secrets

```bash
# Staging
cat ~/.kube/config | base64 -w 0
# Copy the output → GitHub secret STAGING_KUBECONFIG

# Production (switch context first)
kubectl config use-context production-context
cat ~/.kube/config | base64 -w 0
# Copy the output → GitHub secret PROD_KUBECONFIG
```

### 10.3 Verify from CI

The CD workflow decodes and writes the kubeconfig:

```yaml
- name: Configure kubeconfig
  run: |
    mkdir -p $HOME/.kube
    echo "${{ secrets.STAGING_KUBECONFIG }}" | base64 -d > $HOME/.kube/config
```

---

## 11. How the Pipeline Works

### CI Pipeline (`ci.yml`) — triggers on push/PR to `main` or `develop`

```
Push / Pull Request
        │
        ▼
┌─────────────────────────────────┐
│  Job 1: build-and-test          │
│  ./gradlew test                 │
│  + jacocoTestReport             │
│  + jacocoTestCoverageVerif.     │  ← fails if coverage < 70%
│                                 │
│  Testcontainers spins up        │
│  PostgreSQL + Kafka in Docker   │
└────────────┬────────────────────┘
             │
      ┌──────┴──────┐
      │             │
      ▼             ▼
┌──────────┐  ┌──────────────────────────┐
│  Job 2:  │  │  Job 3:                  │
│ Sonar-   │  │  build-docker-images     │
│ Cloud    │  │  (matrix × 7 services)   │
│ analysis │  │  docker build + push     │
│          │  │  tag: {branch}-{sha8}    │
└──────────┘  └──────────┬───────────────┘
                         │
                    ┌────┴────┐
                    ▼         ▼
         ┌───────────────┐  ┌─────────────────┐
         │  Job 4:       │  │  Job 5:         │
         │ security-scan │  │  helm-lint      │
         │ Trivy ×7 imgs │  │  helm lint      │
         │ fail CRITICAL │  │  chart validate │
         └───────────────┘  └─────────────────┘
```

### CD Pipeline (`cd.yml`) — triggers on push to `main` only

```
Push to main (CI must have passed)
        │
        ▼
┌─────────────────────────────────┐
│  Job 1: deploy-staging          │
│  helm upgrade --install         │
│  → smartbank namespace          │
│  kubectl rollout status ×7      │
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────┐
│  Job 2: smoke-tests             │
│  kubectl port-forward gateway   │
│  curl /actuator/health ×5       │
│  register → login → JWT         │
│  → protected endpoint check     │
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────┐
│  Job 3: deploy-production       │
│                                 │
│  ⏸ WAITING FOR APPROVAL        │  ← reviewers get email notification
│  [Approve and deploy] button    │
│                                 │
│  (after approval)               │
│  helm upgrade --install (prod)  │
│  kubectl rollout status ×7      │
│  health check via kubectl exec  │
│  git tag release-YYYYMMDD-sha   │
└─────────────────────────────────┘
```

### Image tagging strategy

| Tag | When created | Purpose |
|---|---|---|
| `main-a1b2c3d4` | Every push to main | Immutable, traceable to exact commit |
| `develop-a1b2c3d4` | Every push to develop | Dev builds |
| `latest` | Every push to main | Convenience tag for docker compose |
| `buildcache` | Every build | BuildKit layer cache stored in Docker Hub |

---

## 12. Day-to-Day Operations

### Deploy a specific version manually

```bash
# Via Helm
helm upgrade smartbank helm/smartbank \
  --namespace smartbank \
  --set global.imageTag=main-a1b2c3d4 \
  --reuse-values
```

### Roll back to a previous version

```bash
# List Helm release history
helm history smartbank -n smartbank

# Rollback to previous release
helm rollback smartbank -n smartbank

# Rollback to a specific revision
helm rollback smartbank 3 -n smartbank
```

### View running pods

```bash
kubectl get pods -n smartbank
kubectl get pods -n smartbank -w    # watch for changes
```

### View service logs

```bash
# Single service
kubectl logs -f deployment/auth-service -n smartbank

# All pods of a service
kubectl logs -l app.kubernetes.io/name=auth-service -n smartbank --all-containers

# Previous crashed pod
kubectl logs deployment/auth-service -n smartbank --previous
```

### Check service health

```bash
# Via port-forward
kubectl port-forward svc/api-gateway 8080:80 -n smartbank &
curl http://localhost:8080/actuator/health

# Via kubectl exec
kubectl exec deployment/api-gateway -n smartbank -- \
  wget -qO- http://localhost:8080/actuator/health
```

### Check HPA & scaling

```bash
kubectl get hpa -n smartbank
kubectl top pods -n smartbank
```

### Re-trigger a failed production deploy

If a deploy expires (no approval within 30 days) or you need to re-deploy the same commit:

1. Go to **Actions** tab in GitHub
2. Find the CD workflow run
3. Click **Re-run failed jobs**

---

## 13. Troubleshooting

### CI: Testcontainers fails in GitHub Actions

**Symptom:** `Could not find a valid Docker environment`

**Fix:** Add this env var to the `build-and-test` job:

```yaml
env:
  TESTCONTAINERS_RYUK_DISABLED: 'true'
  DOCKER_HOST: unix:///var/run/docker.sock
```

---

### CI: JaCoCo coverage fails at 70%

**Symptom:** `Rule violated for bundle ... coverage rate is X%, but expected minimum is 70%`

**Fix options:**
- Write more tests (correct fix)
- Temporarily lower the threshold in `build.gradle`:
  ```groovy
  limit { minimum = 0.50 }   # lower threshold while catching up
  ```
- Exclude generated code:
  ```groovy
  classDirectories.setFrom(files(classDirectories.files.collect {
      fileTree(dir: it, exclude: ['**/dto/**', '**/entity/**', '**/config/**'])
  }))
  ```

---

### CI: SonarCloud Quality Gate fails

1. Go to [sonarcloud.io](https://sonarcloud.io) → your project → **Issues**
2. Fix the flagged issues or mark as **Won't Fix** / **False Positive** with justification
3. Re-push — SonarCloud re-evaluates on the next scan

---

### CD: Helm deploy fails — kubeconfig error

**Check:**
1. `STAGING_KUBECONFIG` / `PROD_KUBECONFIG` secret is valid base64-encoded kubeconfig
2. The kubeconfig has the correct cluster endpoint and credentials
3. The cluster is reachable from GitHub Actions runners

**Test locally:**

```bash
echo "$KUBECONFIG_B64" | base64 -d > /tmp/test-kubeconfig
KUBECONFIG=/tmp/test-kubeconfig kubectl get nodes
rm /tmp/test-kubeconfig
```

---

### CD: Helm deploy times out

The `--wait --timeout 10m` flag causes Helm to wait for all pods to be ready.

**Check pod status:**

```bash
kubectl get pods -n smartbank
kubectl describe pod <pending-pod> -n smartbank
kubectl logs <crashing-pod> -n smartbank --previous
```

Common causes:
- Insufficient cluster resources (check `kubectl top nodes`)
- Image pull errors (check `DOCKERHUB_USERNAME` secret and image tags)
- Database not ready (PostgreSQL StatefulSet takes time on first deploy)

---

### CD: Smoke tests fail after deploy

**Check:**

```bash
kubectl get pods -n smartbank
kubectl logs deployment/api-gateway -n smartbank --tail=50
```

Common causes:
- Config server not yet healthy when downstream services start (K8s readiness probes should handle this)
- Wrong `JWT_SECRET` format (must be the same base64-encoded value across all services)
- Database migrations failed (check `auth-service` / `account-service` logs for Flyway errors)
- LoadBalancer has no external IP (use port-forward fallback)

---

## Summary — Secrets Checklist

### Repository secrets (5 total)

- [ ] `DOCKERHUB_USERNAME`
- [ ] `DOCKERHUB_TOKEN`
- [ ] `SONAR_TOKEN`
- [ ] `SONAR_PROJECT_KEY`
- [ ] `SONAR_ORG`

### `staging` environment secrets (9 total)

- [ ] `STAGING_KUBECONFIG` — base64-encoded kubeconfig
- [ ] `DB_PASSWORD_B64` — base64-encoded PostgreSQL password
- [ ] `JWT_SECRET_B64` — base64-encoded JWT secret
- [ ] `CONFIG_PASSWORD_B64` — base64-encoded Config Server password
- [ ] `REDIS_PASSWORD_B64` — base64-encoded Redis password
- [ ] `SMTP_PASSWORD_B64` — base64-encoded SMTP password
- [ ] `POSTGRES_PASSWORD` — plaintext (for Bitnami chart)
- [ ] `REDIS_PASSWORD` — plaintext (for Bitnami chart)
- [ ] `GRAFANA_PASSWORD` — plaintext (for Grafana)

### `production` environment secrets (9 total)

- [ ] `PROD_KUBECONFIG` — base64-encoded kubeconfig
- [ ] `DB_PASSWORD_B64` — **different from staging**
- [ ] `JWT_SECRET_B64` — **different from staging**
- [ ] `CONFIG_PASSWORD_B64` — **different from staging**
- [ ] `REDIS_PASSWORD_B64` — **different from staging**
- [ ] `SMTP_PASSWORD_B64` — **different from staging**
- [ ] `POSTGRES_PASSWORD` — **different from staging**
- [ ] `REDIS_PASSWORD` — **different from staging**
- [ ] `GRAFANA_PASSWORD` — **different from staging**
