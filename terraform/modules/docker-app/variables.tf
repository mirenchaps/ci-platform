# ci-platform/terraform/modules/docker-app/variables.tf
#
# Input variables for the docker-app module.
# Every value that changes between deployments is a variable —
# this is what makes the module reusable across projects.
#
# Docs: https://developer.hashicorp.com/terraform/language/values/variables

variable "app_name" {
  description = "Name used for the Docker container and network"
  type        = string
}

variable "image_tag" {
  description = "Docker image tag to deploy, e.g. 'mirenchaps/home-network-mcp:sha-abc1234'"
  type        = string
}

variable "host_port" {
  description = "Host port to map to the container's exposed port"
  type        = number
  default     = 8000
}

variable "container_port" {
  description = "Port the application listens on inside the container"
  type        = number
  default     = 8000
}

variable "env_vars" {
  description = "Environment variables to pass into the container (map of name → value)"
  type        = map(string)
  default     = {}
}
