#!groovy
// Scripted Pipeline
timestamps{
  podTemplate(yaml: """
kind: Pod
spec:
  containers:
  - name: jnlp
    image: docker.ted.mighty/jenkins/jnlp-slave:3.35-5-alpine
    imagePullPolicy: IfNotPresent
    args: ['\$(JENKINS_SECRET)', '\$(JENKINS_NAME)']
    resources:
      limits:
        memory: "512Mi"
        cpu: "1000m"
      requests:
        memory: "256Mi"
        cpu: "500m"
  - name: npm 
    image: docker.ted.mighty/node:cnpm
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
"""
  ){
    // Pipeline Parameters
    properties([
      parameters([
        gitParameter(name: 'BRANCH_TAG',
                     branchFilter: 'origin/(.*)',
                     type: 'PT_BRANCH_TAG',
                     defaultValue: 'master',
                     useRepository: '.*document-collection-front.git',
                     sortMode: 'ASCENDING')
      ])  
    ])

    node(POD_LABEL){
      stage('Build with NPM'){

        container('npm'){
          checkout([$class: 'GitSCM',
                    branches: [[name: "${params.BRANCH_TAG}"]],
                    doGenerateSubmoduleConfigurations: false,
                    extensions: [],
                    submoduleCfg: [],
                    userRemoteConfigs: [[credentialsId: 'ted_gitlab', url: 'http://172.16.101.211/zhangpeng/document-collection-front.git']]
                   ])

          env.BRANCH_TAG = "${params.BRANCH_TAG}"
          env.IMAGE_TAG = 'latest'

          if (env.BRANCH_TAG != 'master'){
            env.IMAGE_TAG = "${env.BRANCH_TAG}"
          }

          echo "------ [DEBUG]: Before building ------"
          echo "Brangh/Tag: ${env.BRANCH_TAG}"
          echo "Image tag: ${env.IMAGE_TAG}"
          sh 'npm --version'
          sh 'npm config get cache'
          sh 'cnpm config get cache'
          sh 'du -sh ~/.npm'
          sh 'ls -al ~/.npm'
          echo "-------------------------------------"

          sh 'cnpm version'
          sh 'cnpm install'
          sh 'cnpm run build'

          echo "------ [DEBUG]: After building ------"
          sh 'du -sh ~/.npm'
          sh 'ls -al ~/.npm'
          sh 'ls'
          sh 'ls dist'
          echo "-------------------------------------"
        }
      }//stage

      stage("Build with Kaniko") {
        echo "${env.IMAGE_TAG}"
        container('kaniko'){
          sh "/kaniko/executor -f `pwd`/Dockerfile -c `pwd` --insecure --skip-tls-verify --cache=true --destination=docker.ted.mighty/document-collection-front:${env.IMAGE_TAG}"
        }
      }

    }//node
  }//podTemplate
}//timestamps
