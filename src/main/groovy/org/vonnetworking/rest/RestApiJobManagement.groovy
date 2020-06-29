package org.vonnetworking.rest

import groovyx.net.http.ContentType
import groovyx.net.http.HttpResponseDecorator
import groovyx.net.http.RESTClient
import javaposse.jobdsl.dsl.*

class RestApiJobManagement extends MockJobManagement {

    final RESTClient restClient
    private boolean crumbHeaderSet = false

    RestApiJobManagement(String baseUrl) {
        restClient = new RESTClient(baseUrl)
        restClient.handler.failure = { it }
    }

    void setCredentials(String username, String password) {
        crumbHeaderSet = false
        restClient.headers['Authorization'] = 'Basic ' + "$username:$password".bytes.encodeBase64()
    }

    @Override
    String getConfig(String jobName) throws JobConfigurationNotFoundException {
        String xml = fetchExistingXml(jobName)
        if (!xml) {
            throw new JobConfigurationNotFoundException(jobName)
        }

        xml
    }

    @Override
    boolean createOrUpdateConfig(Item item, boolean ignoreExisting) throws NameNotProvidedException {
        createOrUpdateConfig(item.name, item.xml, ignoreExisting, false)
    }

    @Override
    void createOrUpdateView(String viewName, String config, boolean ignoreExisting) throws NameNotProvidedException, ConfigurationMissingException {
        createOrUpdateConfig(viewName, config, ignoreExisting, true)
    }

    boolean createOrUpdateConfig(String name, String xml, boolean ignoreExisting, boolean isView) throws NameNotProvidedException {
        boolean success
        String status

        String existingXml = fetchExistingXml(name, isView)
        if (existingXml) {
            if (ignoreExisting) {
                success = true
                status = 'ignored'
            } else {
                success = update(name, xml, isView)
                status = success ? 'updated' : 'update failed'
            }
        } else {
            success = create(name, xml, isView)
            status = success ? 'created' : 'create failed'
        }

        println "$name - $status"
        success
    }

    @Override
    InputStream streamFileInWorkspace(String filePath) throws IOException {
        new File(filePath).newInputStream()
    }

    @Override
    String readFileInWorkspace(String filePath) throws IOException {
        new File(filePath).text
    }

    private boolean create(String name, String xml, boolean isView) {
        String job
        String path
        if (name.contains('/')) {
            int index = name.lastIndexOf('/')
            String folder = name[0..(index - 1)]
            job = name[(index + 1)..-1]
            path = getPath(folder, isView) + '/createItem'
        } else {
            job = name
            path = isView ? 'createView' : 'createItem'
        }

        setCrumbHeader()
        HttpResponseDecorator resp = restClient.post(
            path: path,
            body: xml,
            query: [name: job],
            requestContentType: 'application/xml'
        )

        resp.status == 200
    }

    private boolean update(String name, String xml, boolean isView) {
        setCrumbHeader()
        HttpResponseDecorator resp = restClient.post(
            path: getPath(name, isView) + '/config.xml',
            body: xml,
            requestContentType: 'application/xml'
        )

        resp.status == 200
    }

    Map readParamsfromFile (String filePath) throws IOException{
        def jsonText = new File(filePath).text
        new groovy.json.JsonSlurper().parseText(jsonText)
    }

    private boolean run(String baseUrl, String jobName, Map params) {
        setCrumbHeader()
        
        HttpResponseDecorator resp = restClient.post(
            path: '/job/' + jobName + '/buildWithParameters',
            query: params,
            requestContentType: 'application/json'
        )
        
        //trace the request through the queue to determine status
        String queueUrl
        resp.headers.each { header ->
            if (header.getName() == 'Location') {
                queueUrl = header.getValue().replace(baseUrl, '')
                System.out.println(queueUrl)
            }
            
        }
        //loop on the queue item until we can get the build number and check status on that
        def queueResponseData = null
        def buildURL = null
        def queueExecutableData = null
        while (!buildURL) {
            System.out.println("looping waiting for Jenkins Job $jobName to Start...")
            HttpResponseDecorator queueResp = restClient.get(
                path: "$queueUrl/api/json"
            )
            //System.out.println(queueResp.responseData['url'])
            queueResponseData = queueResp.responseData
            
            queueExecutableData = queueResponseData['executable']
            if (queueExecutableData) {
                buildURL = queueExecutableData['url'].replace(baseUrl, '')
            }
            sleep 1000
        }

        def buildResult = null
        while (!buildResult) {
            System.out.println("looping waiting for Jenkins Job $jobName to Complete...")
            HttpResponseDecorator buildResp = restClient.get(
                path: buildURL + '/api/json',
                contentType: 'application/json'
            )
            def buildResponseData = buildResp.responseData 
            buildResult = buildResponseData['result']
            sleep 1000
        }

        if (buildResult != "SUCCESS") {
            System.out.println("Build failed with result: $buildResult; please check logs for $buildURL")
        } else {
            System.out.println("Build Succeeded!")
        }

        resp.status == 200
    }
    
    private String fetchExistingXml(String name, boolean isView) {
        setCrumbHeader()
        HttpResponseDecorator resp = restClient.get(
            contentType: ContentType.TEXT,
            path: getPath(name, isView) + '/config.xml',
            headers: [Accept: 'application/xml'],
        )
        resp?.data?.text
    }

    static String getPath(String name, boolean isView) {
        if (name.startsWith('/')) {
            return '/' + getPath(name[1..-1], isView)
        }
        isView ? "view/$name" : "job/${name.replaceAll('/', '/job/')}"
    }

    private setCrumbHeader() {
        if (crumbHeaderSet)
            return

        HttpResponseDecorator resp = restClient.get(path: 'crumbIssuer/api/xml')
        if (resp.status == 200) {
            restClient.headers[resp.data.crumbRequestField] = resp.data.crumb
        }
        crumbHeaderSet = true
    }
}
