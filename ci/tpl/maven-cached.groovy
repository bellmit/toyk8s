#!groovy
pipeline {
  options {
    timestamps()
  }
  agent {
    kubernetes {
      cloud 'kubernetes'
      yaml """
kind: Pod
spec:
  containers:
  - name: jnlp
    image: 'docker.ted.mighty/jenkins/jnlp-slave:3.35-5-alpine'
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
    #image: maven:3.3.9-jdk-8
    #imagePullPolicy: IfNotPresent
    imagePullPolicy: Always
    command: ['cat']
    tty: true
    volumeMounts:
    - mountPath: "/root/.m2/repository"
      name: cache
    resources:
      limits:
        memory: "3072Mi"
        cpu: "2"
      requests:
        memory: "1024Mi"
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
  imagePullSecrets:
  - name: nexuscred
  volumes:
  - name: jenkins-docker-cfg
    projected:
      sources:
      - secret:
          name: nexuscred
          items:
            - key: .dockerconfigjson
              path: config.json
  - name: cache
    persistentVolumeClaim:
      claimName: maven-cache
"""
  }//kubernetes
}//agent

  parameters {

    gitParameter name: 'BRANCH_TAG',
                 description: '选择代码分支或Tag',
                 branchFilter: 'origin/(.*)',
                 type: 'PT_BRANCH_TAG',
                 defaultValue: 'master',
                 useRepository: '.*ksbc.git'

    choice(name: 'COMPONENT', 
           choices: ['all', 'ksbc-ks', 'ksbc-score', 'ksbc-sso', 'ksbc-notice', 'ksbc-sys-management'],
           description: '选择需要打包镜像的组件')
  }

  stages {
    stage('编译源代码'){
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

            echo "====== [DEBUG]: Before building ======"
            echo "Branch/Tag: ${params.BRANCH_TAG}"
            echo "Image tag: ${image_tag}"
            echo "Component: ${params.COMPONENT}"
            sh 'mvn --version'
            echo "======================================"

            //sh 'mvn -U clean install -Dmaven.test.skip=true -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true'

            echo "====== [DEBUG]: After building ======"
            echo "======================================"
          }
        }//script
      }//steps
    }
    stage('打包Docker镜像'){
      parallel {
        stage('ksbc-ks') {
          steps {
            echo "ksbc-ks"
            sh 'sleep 10'
          }
        }
        stage('ksbc-sso') {
          steps {
            echo "ksbc-sso"
            sh 'sleep 5'
          }
        }
      }
    }
  }
}//pipeline
