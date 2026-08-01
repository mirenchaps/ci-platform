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
    def imageTag = config.imageTag ?: error('terraformDeploy: imageTag is required')
    def port     = config.port     ?: 8000
    // app_name defaults to the Jenkins job name if not explicitly set —
    // env.JOB_NAME is always available in a Jenkins pipeline run.
    def appName  = config.appName  ?: env.JOB_NAME.toLowerCase().replaceAll('[^a-z0-9-]', '-')

    // Jenkins checks out shared libraries under <WORKSPACE>@libs/<library-name>.
    // The exact parent path varies by Jenkins version and config, so we derive it
    // from WORKSPACE which is always set by Jenkins for the current job.
    // Docs: https://www.jenkins.io/doc/book/pipeline/shared-libraries/#directory-structure
    def tfDir = "${env.WORKSPACE}@libs/ci-platform/terraform/modules/docker-app"
    // If the above path is wrong on your Jenkins, check the actual checkout location with:
    //   sh 'find $WORKSPACE/.. -name "main.tf" 2>/dev/null | head -5'

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
                  -out=tfplan \\
                  -input=false
            """
        }
    }

    stage('Terraform Apply') {
        dir(tfDir) {
            // input() pauses the pipeline and waits for a human to click Proceed.
            // Remove this block if you want fully automated deploys.
            input message: "Deploy image ${imageTag} to port ${port}?", ok: 'Deploy'
            sh 'terraform apply -input=false tfplan'
        }
    }
}
