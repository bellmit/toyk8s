#!groovy
//Declarative pipeline
pipeline {
  agent {
    kubernetes {
      cloud 'kubernetes'
      //defaultContainer 'jnlp'
      //yamlFile 'debug.yaml'
      yaml """
apiVersion: v1
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
  - name: ubuntu
    image: 'docker.ted.mighty/ubuntu:16.04'
    imagePullPolicy: IfNotPresent
    command: ['cat']
    tty: true
    resources:
      limits:
        memory: "512Mi"
        cpu: "1000m"
      requests:
        memory: "256Mi"
        cpu: "500m"
  imagePullSecrets:
  - name: nexuscred
"""
    }
  }

  parameters {
    gitParameter name: 'BRANCH_TAG',
                 branchFilter: 'origin/(.*)',
                 type: 'PT_BRANCH_TAG',
                 defaultValue: 'master',
                 useRepository: '.*hellopy.git'
  }

  stages {
    stage('Debug') {
      environment {
        GGGGGGG='yoo'
      }
      steps {
        script {
          def image_tag = 'latest'
          if (params.BRANCH_TAG != 'master') {
            image_tag = "${params.BRANCH_TAG}"
          } 

          echo "${image_tag}"
          sh "export IMAGE_TAGGGG=${image_tag}"
          echo "============: ${GGGGGGG}"

          container('ubuntu') {
            checkout([$class: 'GitSCM',
          	        branches: [[name: "${params.BRANCH_TAG}"]],
          	        doGenerateSubmoduleConfigurations: false,
          	        extensions: [], 
          	        submoduleCfg: [], 
          	        userRemoteConfigs: [[credentialsId: 'ted_gitlab', url: 'http://172.16.101.211/zhangpeng/hellopy.git']]
          	       ])
          }
        }//script
      }
    }

    stage('Debug-1') {
      steps {
        sh 'env'
      }
    }

  }//stages
}//pipeline
