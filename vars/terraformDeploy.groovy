// ci-platform/jenkins/vars/terraformDeploy.groovy
//
// Shared Jenkins step: run terraform plan then apply for a docker-app module.
//
// Usage in a consuming Jenkinsfile:
//
//   @Library('ci-platform') _
//   terraformDeploy(imageTag: 'sha-abc1234', port: 8000)
//
// Parameters:
//   imageTag  (required) — the Docker image tag to deploy, e.g. 'sha-abc1234'
//   port      (optional) — host port to expose, default 8000
//
// Docs on Jenkins Shared Libraries:
//   https://www.jenkins.io/doc/book/pipeline/shared-libraries/
//   Global vars live in vars/ — each .groovy file becomes a callable step.

def call(Map config = [:]) {
    def imageTag       = config.imageTag       ?: error('terraformDeploy: imageTag is required')
    def port           = config.port           ?: 8000
    def appName        = config.appName        ?: env.JOB_NAME.toLowerCase().replaceAll('[^a-z0-9-]', '-')
    def configFilePath = config.configFilePath ?: ''
    def sshKeyPath     = config.sshKeyPath     ?: ''

    // Dynamically locate the terraform module within the library checkout.
    // Jenkins may use a hash-based path under @libs rather than the library name,
    // so we find main.tf rather than hardcoding the path.
    def tfDir = sh(
        script: "find '${env.WORKSPACE}@libs' -name 'main.tf' -path '*/docker-app*' -exec dirname {} \\; 2>/dev/null | head -1",
        returnStdout: true
    ).trim()

    if (!tfDir) {
        error('terraformDeploy: could not find docker-app terraform module — is ci-platform library checked out?')
    }

    stage('Terraform Init') {
        dir(tfDir) {
            sh 'terraform init -input=false'
        }
    }

    stage('Terraform Plan') {
        dir(tfDir) {
            sh """
                terraform plan \\
                  -var="app_name=${appName}" \\
                  -var="image_tag=${imageTag}" \\
                  -var="host_port=${port}" \\
                  -var="config_file_path=${configFilePath}" \\
                  -var="ssh_key_path=${sshKeyPath}" \\
                  -out=tfplan \\
                  -input=false
            """
        }
    }

    stage('Terraform Apply') {
        dir(tfDir) {
            sh 'terraform apply -input=false tfplan'
        }
    }
}
