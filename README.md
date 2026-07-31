# ci-platform

A reusable CI/CD platform built on **GitHub Actions** (CI) and **Jenkins** (CD),
with infrastructure managed by **Terraform**.

Any Python project can plug into this platform with a two-file addition:
a thin `.github/workflows/ci.yml` that calls the reusable GitHub Actions workflow
here, and a thin `Jenkinsfile` that calls the shared Jenkins library here.

## Architecture

```
Developer pushes → GitHub
                      │
                      ▼
         ┌────────────────────────┐
         │   GitHub Actions (CI)  │  ← runs on GitHub-hosted runners (free)
         │                        │
         │  lint → test → build   │
         │          └── push image to Docker Hub
         └────────────────────────┘
                      │
                      │ triggers Jenkins via webhook
                      ▼
         ┌────────────────────────┐
         │   Jenkins (CD)         │  ← runs on self-hosted runner (home lab VM)
         │                        │
         │  tf plan → tf apply    │
         │       └── smoke test   │
         └────────────────────────┘
                      │
                      ▼
            Docker container deployed
            on home lab Hyper-V VM
```

## Repository layout

```
ci-platform/
├── .github/
│   └── workflows/
│       └── python-ci.yml       # reusable GitHub Actions workflow (CI)
├── jenkins/
│   └── vars/
│       ├── terraformDeploy.groovy   # shared step: terraform plan + apply
│       └── smokeTest.groovy         # shared step: curl health endpoint
├── terraform/
│   └── modules/
│       └── docker-app/         # reusable Terraform module for any containerised app
│           ├── main.tf
│           ├── variables.tf
│           └── outputs.tf
└── README.md
```

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
2. In your app repo's `Jenkinsfile`:

```groovy
@Library('ci-platform') _

pipeline {
    agent any
    stages {
        stage('Deploy') { steps { terraformDeploy(imageTag: env.IMAGE_TAG, port: 8000) } }
        stage('Smoke')  { steps { smokeTest(path: '/metrics', port: 8000) } }
    }
}
```

## Projects using this platform

| Project | Description |
|---|---|
| [home-network-mcp](https://github.com/mirenchaps/home-network-mcp) | Prometheus metrics exporter + MCP server for home lab monitoring |

## VM setup (Jenkins runner)

Jenkins runs on an Ubuntu 24.04 VM inside Hyper-V on a Windows Server 2022 home lab.
Setup guide: see [docs/vm-setup.md](docs/vm-setup.md) *(coming soon)*.
