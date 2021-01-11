#!groovy
// Scripted Pipeline
timestamps {
  podTemplate(yaml: """
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
                     useRepository: '.*hellopy.git',
                     sortMode: 'ASCENDING')
      ])
    ])
    node(POD_LABEL) {
      stage('Build with Kaniko') {
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
          echo "====== [DEBUG]: Before building ======"
          echo "Branch/Tag: ${params.BRANCH_TAG}"
          echo "Image tag: ${image_tag}"
          echo "======================================"

          sh "/kaniko/executor -f `pwd`/Dockerfile -c `pwd` --insecure --skip-tls-verify --cache=true --destination=docker.ted.mighty/hellopy:${env.IMAGE_TAG}"

          echo "====== [DEBUG]: After building ======"
          echo "======================================"
        }
      }
    }
  }
}//timestamps
