# ci-platform/terraform/modules/docker-app/outputs.tf
#
# Values this module exposes to the caller after apply.
# Outputs let the Jenkins pipeline (or a parent Terraform config) read
# the deployed container's details without hard-coding them.
#
# Docs: https://developer.hashicorp.com/terraform/language/values/outputs

output "container_id" {
  description = "Docker container ID of the deployed app"
  value       = docker_container.app.id
}

output "container_name" {
  description = "Docker container name"
  value       = docker_container.app.name
}

output "app_url" {
  description = "URL to reach the app on the host"
  value       = "http://localhost:${var.host_port}"
}
