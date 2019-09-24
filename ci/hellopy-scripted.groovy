#!groovy
timestamps {
  podTemplate(yaml: """
kind: Pod
spec:
  containers:
  - name: jnlp
    image: 'docker.ted.mighty/jenkins/jnlp-slave:3.27-1'
    imagePullPolicy: IfNotPresent
    args: ['\$(JENKINS_SECRET)', '\$(JENKINS_NAME)']
    resources:
      limits:
        memory: "512Mi"
        cpu: "1000m"
      requests:
        memory: "256Mi"
        cpu: "500m"
  - name: kaniko
    image: docker.ted.mighty/kaniko:debug
    imagePullPolicy: IfNotPresent
    command: ['/busybox/cat']
    tty: true
    resources:
      limits:
        memory: "512Mi"
        cpu: "1000m"
      requests:
        memory: "256Mi"
        cpu: "500m"
    volumeMounts:
      - name: jenkins-docker-cfg
        mountPath: /kaniko/.docker
  volumes:
  - name: jenkins-docker-cfg
    projected:
      sources:
      - secret:
          name: regcred
          items:
            - key: .dockerconfigjson
              path: config.json
"""
  ) {
    node(POD_LABEL) {
      // Pipeline Parameters
      parameters {
        gitParameter name: 'BRANCH_TAG',
                     branchFilter: 'origin/(.*)',
                     type: 'PT_BRANCH_TAG',
                     defaultValue: 'master',
                     useRepository: '.*hellopy.git'
      }
  
      stage('Build with Kaniko') {
        // Use variables in Scripted Pipeline
        //def image_tag = 'latest'
        //if (params.BRANCH_TAG != 'master') {
        //  image_tag = "${params.BRANCH_TAG}"
        //}
  
        // Use environment variables
        env.IMAGE_TAG = 'latest'
        if (params.BRANCH_TAG != 'master') {
          env.IMAGE_TAG = "${params.BRANCH_TAG}"
        }
  
        container('kaniko') {
          checkout([$class: 'GitSCM',
                    branches: [[name: "${params.BRANCH_TAG}"]],
                    doGenerateSubmoduleConfigurations: false,
                    extensions: [],
                    submoduleCfg: [],
                    userRemoteConfigs: [[credentialsId: 'ted_gitlab', url: 'http://172.16.101.211/zhangpeng/hellopy.git']]
                   ])
          sh "/kaniko/executor -f `pwd`/Dockerfile -c `pwd` --insecure --skip-tls-verify --cache=true --destination=docker.ted.mighty/hellopy:${env.IMAGE_TAG}"
        }
      }
    }
  }
}
