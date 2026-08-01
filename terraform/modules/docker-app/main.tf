# ci-platform/terraform/modules/docker-app/main.tf
#
# Reusable Terraform module: deploy any containerised app via the Docker provider.
#
# This module creates:
#   - a dedicated Docker network for the app
#   - a Docker container from the given image tag
#
# The Docker provider manages containers the same way the AWS provider manages
# EC2 instances — the same plan/apply workflow, just local.
#
# Docs: https://registry.terraform.io/providers/kreuzwerker/docker/latest/docs

terraform {
  required_providers {
    # kreuzwerker/docker is the standard community Docker provider for Terraform.
    # Find it at: registry.terraform.io → search "docker" → kreuzwerker/docker
    docker = {
      source  = "kreuzwerker/docker"
      version = "~> 3.0"
    }
  }
}

# Pull the image before creating the container so Terraform tracks it as a resource.
# If the tag doesn't exist in the registry, this step will fail loudly — which is
# the right behaviour (fail fast rather than running a stale image).
resource "docker_image" "app" {
  name         = var.image_tag
  # force_remove = true means Terraform removes the old image on destroy,
  # keeping the host clean after repeated deployments.
  force_remove = true
}

# Isolated network so this container doesn't share a network namespace
# with other containers on the host.
resource "docker_network" "app" {
  name = "${var.app_name}-network"
}

resource "docker_container" "app" {
  name  = var.app_name
  image = docker_image.app.image_id

  # Bind host_port → container_port so the app is reachable on the host.
  ports {
    internal = var.container_port
    external = var.host_port
  }

  networks_advanced {
    name = docker_network.app.name
  }

  # Pass through any environment variables (e.g. Grafana Cloud credentials).
  # env is a flat list of "KEY=VALUE" strings — not a block, so no dynamic{} here.
  # If env_vars is empty the list comprehension produces [] and no vars are set.
  env = [for k, v in var.env_vars : "${k}=${v}"]

  # Mount a config file from the host into the container if provided.
  dynamic "volumes" {
    for_each = var.config_file_path != "" ? [1] : []
    content {
      host_path      = var.config_file_path
      container_path = "/app/config.json"
      read_only      = true
    }
  }

  restart = "on-failure"
}
