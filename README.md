# ci-platform

A reusable CI/CD platform built on **GitHub Actions** (CI) and **Jenkins** (CD).
Kubernetes-hosted apps deploy via **GitOps with ArgoCD**; VM-hosted apps deploy
via **Terraform**.

Any Python project can plug into this platform with a two-file addition:
a thin `.github/workflows/ci.yml` that calls the reusable GitHub Actions workflow
here, and a thin `Jenkinsfile` that calls the shared Jenkins library here.

Kubernetes manifests and ArgoCD `Application` resources live in the companion
repo, [home-lab-gitops](https://github.com/mirenchaps/home-lab-gitops) — this
repo answers *what the software is*, that one answers *what is running*.

## Architecture

```
Developer pushes → GitHub
                      │
                      ▼
         ┌─────────────────────────────────────┐
         │   GitHub Actions (CI)               │
         │   lint → test → build               │  ← GitHub-hosted runners
         │            └── push image to Docker Hub
         │   trigger-jenkins                    │  ← self-hosted runner
         │            └── needs to reach Jenkins on the home lab VM
         └─────────────────────────────────────┘
                      │
                      │ triggers Jenkins via webhook (crumb + buildWithParameters)
                      ▼
         ┌─────────────────────────────────────┐
         │   Jenkins (CD)                       │  ← self-hosted runner (home lab VM)
         │                                       │
         │   Kubernetes-hosted apps:             │
         │     commit new image tag to           │
         │     home-lab-gitops repo             │
         │                                      │
         │   VM-hosted apps:                    │
         │     terraformDeploy → tf plan/apply  │
         │     └── smoke test                   │
         └─────────────────────────────────────┘
                      │
                      ▼ (Kubernetes-hosted apps only)
         ┌─────────────────────────────────────┐
         │   ArgoCD                              │
         │   polls home-lab-gitops, detects       │
         │   drift, syncs → cluster               │
         └─────────────────────────────────────┘
                      │
                      ▼
            Kubernetes Deployment updated (K3s)
```

CI never talks to the cluster directly — the only path in for a Kubernetes-hosted
app is a commit to `home-lab-gitops`. This is the platform's target architecture;
Jenkins committing the tag (rather than calling `kubectl` directly) is the last
piece being wired up.

**CI runners are mixed, not uniformly GitHub-hosted.** `lint`, `test`, and
`build-and-push` run on `ubuntu-latest` — free GitHub-hosted runners, since none
of that work needs access to anything private. `trigger-jenkins` runs on
`self-hosted` instead, because it has to reach Jenkins' crumb issuer and
`buildWithParameters` endpoint on the home lab VM, which has no public exposure.

## Repository layout

```
ci-platform/
├── .github/
│   └── workflows/
│       └── python-ci.yml         # reusable GitHub Actions workflow (CI)
├── vars/
│   ├── terraformDeploy.groovy    # shared step: terraform plan + apply (VM-hosted apps)
│   ├── kubernetesDeploy.groovy   # shared step: kubectl set image + rollout status —
│   │                             #   legacy push path for k8s apps, being replaced by
│   │                             #   GitOps/ArgoCD (see home-lab-gitops)
│   └── smokeTest.groovy          # shared step: curl health endpoint
├── terraform/
│   └── modules/
│       └── docker-app/           # reusable Terraform module for any containerised app
│           ├── main.tf
│           ├── variables.tf
│           └── outputs.tf
└── README.md
```

Kubernetes manifests (Deployments, Services, ArgoCD `Application` resources) are
not in this repo — they live in
[home-lab-gitops](https://github.com/mirenchaps/home-lab-gitops), organised by
app under `apps/<name>/`.

## How to use

### GitHub Actions CI

In your app repo, create `.github/workflows/ci.yml`:

```yaml
jobs:
  ci:
    uses: mirenchaps/ci-platform/.github/workflows/python-ci.yml@main
    with:
      image-name: your-image-name
    secrets:
      DOCKERHUB_USERNAME: ${{ secrets.DOCKERHUB_USERNAME }}
      DOCKERHUB_TOKEN: ${{ secrets.DOCKERHUB_TOKEN }}
```

The reusable workflow runs: **lint → test → docker build → docker push**.

### Jenkins CD

1. Register this repo as a Jenkins Shared Library (name: `ci-platform`).
2. In your app repo's `Jenkinsfile`, pick the deploy path for your target:

**Kubernetes-hosted apps (GitOps via ArgoCD)** — Jenkins does not deploy
directly. It commits the new image tag to
[home-lab-gitops](https://github.com/mirenchaps/home-lab-gitops); ArgoCD detects
the change and syncs it to the cluster. See that repo for the commit step and
the app's `Application` manifest.

**VM-hosted apps (Terraform)**:

```groovy
@Library('ci-platform') _

pipeline {
    agent any
    stages {
        stage('Deploy') {
            steps {
                terraformDeploy(
                    imageTag:       env.IMAGE_TAG,
                    port:           8000,
                    configFilePath: '/etc/your-app/config.json',  // optional
                    sshKeyPath:     '/etc/your-app/id_ed25519',   // optional
                    envVars:        [SOME_VAR: 'value'],          // optional
                )
            }
        }
        stage('Smoke')  { steps { smokeTest(path: '/metrics', port: 8000) } }
    }
}
```

`kubernetesDeploy.groovy` (direct `kubectl set image`) still exists in `vars/`
but is being phased out for Kubernetes targets in favour of the GitOps path above.

## Projects using this platform

| Project | Description |
|---|---|
| [home-network-mcp](https://github.com/mirenchaps/home-network-mcp) | Prometheus metrics exporter + MCP server for home lab monitoring |

## VM setup (Jenkins runner)

Jenkins runs on an Ubuntu 24.04 VM inside Hyper-V on a Windows Server 2022 home lab.
Setup guide: see [docs/vm-setup.md](docs/vm-setup.md) *(coming soon)*.
