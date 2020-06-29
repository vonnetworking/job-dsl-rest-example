String jobFolder = 'devops'
String jobName = 'Devops_1'
String jobDescription = 'This is a sample job'
String repo = 'sheehan/gradle-example'

folder(jobFolder) {
    description jobDescription
}

job("$jobFolder/$jobName") {
    scm {
        github repo
    }
    triggers {
        scm 'H/5 * * * *'
    }
    steps {
        gradle 'assemble'
    }
}
