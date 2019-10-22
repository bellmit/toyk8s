#!groovy
// Scripted Pipeline
timestamps {
  podTemplate(yaml: """
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
  - name: maven
    image: docker.ted.mighty/maven:3.3.9-jdk-8
    #image: maven:3.3.9-jdk-8
    #imagePullPolicy: IfNotPresent
    imagePullPolicy: Always
    command: ['cat']
    tty: true
    resources:
      limits:
        memory: "3072Mi"
        cpu: "2"
      requests:
        memory: "1024Mi"
        cpu: "1"
  imagePullSecrets:
  - name: nexuscred
"""
  ){
    // Pipeline Parameters
    properties([
      parameters([
        gitParameter(name: 'BRANCH_TAG',
                     description: '选择代码分支或Tag',
                     branchFilter: 'origin/(.*)',
                     type: 'PT_BRANCH_TAG',
                     defaultValue: 'master',
                     useRepository: '.*ksbc.git',
                     sortMode: 'ASCENDING'),
        choice(name: 'COMPONENT',
               choices: ['all', 'ksbc-ks', 'ksbc-score', 'ksbc-sso', 'ksbc-notice', 'ksbc-sys-management', 'ksbc-ccf'],
               description: '选择需要打包镜像的组件')
      ])
    ])
    node(POD_LABEL) {
      stage('编译源代码') {
        // Use environment variables
        env.IMAGE_TAG = 'latest'
        if (params.BRANCH_TAG != 'master') {
          env.IMAGE_TAG = "${params.BRANCH_TAG}"
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
          echo "Image tag: ${env.IMAGE_TAG}"
          echo "Component: ${params.COMPONENT}"
          sh 'mvn --version'
          echo "======================================"

          echo "====== [DEBUG]: After building ======"
          echo "======================================"
        }
      }
      stage('打包Docker镜像'){
        if (params.COMPONENT == 'all') {
          echo "Build all components"
        } else {
          container('kaniko') {
            echo "Building: ${params.COMPONENT}"
          }
        }
      }
    }
  }//podTemplate
}//timestamps
