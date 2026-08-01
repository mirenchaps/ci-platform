// ci-platform/jenkins/vars/smokeTest.groovy
//
// Shared Jenkins step: hit an HTTP endpoint and fail the build if it's not 200.
//
// Usage in a consuming Jenkinsfile:
//
//   @Library('ci-platform') _
//   smokeTest(path: '/metrics', port: 8000)
//
// Parameters:
//   path  (optional) — URL path to hit, default '/healthz'
//   port  (optional) — port the container is on, default 8000
//   host  (optional) — hostname, default 'localhost'

def call(Map config = [:]) {
    def path  = config.path  ?: '/healthz'
    def port  = config.port  ?: 8000
    def host  = config.host  ?: 'localhost'
    def url   = "http://${host}:${port}${path}"

    stage('Smoke Test') {
        // -f  → fail with non-zero exit if HTTP status >= 400
        // -sS → silent output but still show errors
        // --retry 5 --retry-delay 3 → retry up to 5 times (container may still be starting)
        sh """
            curl -fsSL \\
              --retry 5 \\
              --retry-delay 3 \\
              --retry-connrefused \\
              '${url}' > /dev/null
        """
        echo "Smoke test passed: ${url} returned 200"
    }
}
