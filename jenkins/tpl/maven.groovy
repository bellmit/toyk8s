#!groovy
pipeline {
  agent {
    kubernetes {
      cloud 'kubernetes'
      yaml """
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
  - name: maven
    image: docker.ted.mighty/maven:3.3.9-jdk-8
    #imagePullPolicy: IfNotPresent
    imagePullPolicy: Always
    command: ['cat']
    tty: true
    resources:
      limits:
        memory: "3072Mi"
        cpu: "2"
      requests:
        memory: "2048Mi"
        cpu: "1"
  - name: kaniko
    image: docker.ted.mighty/kaniko-executor:debug-v0.13.0
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
    }
  }
  parameters {
    gitParameter name: 'BRANCH_TAG',
                 branchFilter: 'origin/(.*)',
                 type: 'PT_BRANCH_TAG',
                 defaultValue: 'master',
                 useRepository: '.*ksbc.git'
  }
  stages {
    stage('Build with Maven'){
      steps {
        script {

          def image_tag = 'latest'
          if (params.BRANCH_TAG != 'master') {
            image_tag = "${params.BRANCH_TAG}"
          }

          container('maven'){
            checkout([$class: 'GitSCM',
                      branches: [[name: "${params.BRANCH_TAG}"]],
                      doGenerateSubmoduleConfigurations: false,
                      extensions: [],
                      submoduleCfg: [],
                      userRemoteConfigs: [[credentialsId: 'ted_gitlab', url: 'http://172.16.101.211/zhangpeng/ksbc.git']]
                     ])

            echo "------ [DEBUG]: Before building ------"
            echo "Branch/Tag: ${params.BRANCH_TAG}"
            echo "Image tag: ${image_tag}"
            sh 'mvn --version'
            sh 'du -sh ~/.m2'
            sh "ls -al ~/.m2"
            echo "--------------------------------------"

            sh 'mvn clean install -Dmaven.test.skip=true -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true'

            echo "------ [DEBUG]: After building ------"
            sh 'du -sh ~/.m2'
            sh "ls -al ~/.m2"
            echo "--------------------------------------"
          }

          //container('kaniko') {
          //checkout([$class: 'GitSCM',
          //          branches: [[name: "${params.BRANCH_TAG}"]],
          //          doGenerateSubmoduleConfigurations: false,
          //          extensions: [],
          //          submoduleCfg: [],
          //          userRemoteConfigs: [[credentialsId: 'ted_gitlab', url: 'http://172.16.101.211/zhangpeng/hellopy.git']]
          //         ])
          //sh "/kaniko/executor -f `pwd`/Dockerfile -c `pwd` --insecure --skip-tls-verify --cache=true --destination=docker.ted.mighty/hellopy:${image_tag}"
          //}
        }//script
      }
    }
  }
}//pipeline
