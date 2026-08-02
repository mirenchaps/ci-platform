// ci-platform/vars/kubernetesDeploy.groovy
//
// Shared Jenkins step: deploy a new image to a Kubernetes Deployment via kubectl.
//
// Usage in a consuming Jenkinsfile:
//
//   @Library('ci-platform') _
//   kubernetesDeploy(
//       deployment: 'home-network-mcp',
//       image:      "mirenchaps/home-network-mcp:${params.IMAGE_TAG}"
//   )
//
// Parameters:
//   deployment  (required) — name of the Kubernetes Deployment to update
//   image       (required) — full image reference including tag, e.g. 'mirenchaps/home-network-mcp:sha-abc1234'
//   container   (optional) — container name within the pod spec, defaults to deployment name
//   timeout     (optional) — rollout wait timeout, default '120s'
//
// Docs on Jenkins Shared Libraries:
//   https://www.jenkins.io/doc/book/pipeline/shared-libraries/
//   Global vars live in vars/ — each .groovy file becomes a callable step.

def call(Map config = [:]) {
    def deployment = config.deployment ?: error('kubernetesDeploy: deployment is required')
    def image      = config.image      ?: error('kubernetesDeploy: image is required')
    def container  = config.container  ?: deployment
    def timeout    = config.timeout    ?: '120s'

    stage('Deploy') {
        // kubectl set image updates the running deployment in-place.
        // kubectl rollout status blocks until all pods are healthy or timeout is hit.
        sh """
            kubectl set image deployment/${deployment} ${container}=${image}
            kubectl rollout status deployment/${deployment} --timeout=${timeout}
        """
    }
}
