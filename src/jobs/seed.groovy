// If you want, you can define your seed job in the DSL and create it via the REST API.
// See https://github.com/sheehan/job-dsl-rest-example#rest-api-runner

job('new seed') {
    scm {
        github 'vonnetworking/dsl_seed_master_w_gradle'
    }
    parameters {
        stringParam('GIT_REPO', null)
    }
    steps {

        gradle 'clean test'
        gradle 'gatherJobs'
        dsl {
            external 'src/jobs/*.groovy'
            additionalClasspath 'src/main/groovy'
        }
    }
    publishers {
        archiveJunit 'build/test-results/**/*.xml'
    }
}