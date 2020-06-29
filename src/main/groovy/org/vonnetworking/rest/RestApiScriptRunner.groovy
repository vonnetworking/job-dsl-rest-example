package org.vonnetworking.rest

import javaposse.jobdsl.dsl.DslScriptLoader

String pattern = System.getProperty('pattern')
String baseUrl = System.getProperty('baseUrl')
String username = System.getProperty('username')
String password = System.getProperty('password') // password or token
String paramFile = System.getProperty('paramFile') // password or token
String jobName = System.getProperty('jobName')
//String params = System.getProperty('params')

//System.out.println("baseUrl: $baseUrl, paramFile: $paramFile")
String usage() {
    System.out.println 'usage: -DbaseUrl=<baseUrl> -DparamFile=<paramFile>|-Dpattern=<pattern> [-Dusername=<username>] [-Dpassword=<password>]'
}

if (!baseUrl) {
    usage()
    System.exit 1
}
RestApiJobManagement jm = new RestApiJobManagement(baseUrl)
if (username && password) {
    jm.setCredentials username, password
    System.out.println("Setting up credentials.")
}


DslScriptLoader scriptLoader = new DslScriptLoader(jm)
if (!pattern) {
    System.out.println("Pattern not found, moving to job run logic.")
    if (!paramFile || !jobName) {
        usage()
        System.exit 1
    } else {
        System.out.println("paramFile found, executing job run logic.")
        Map params = jm.readParamsfromFile(paramFile)
        //System.out.println("Params: $params")
        jobBuild = jm.run(baseUrl, jobName, params)

    }
} else {
    new FileNameFinder().getFileNames('.', pattern).each { String fileName ->
        System.out.println "\nprocessing file: $fileName"
        File file = new File(fileName)
        scriptLoader.runScript(file.text)
    }
}