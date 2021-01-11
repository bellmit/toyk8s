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
                     description: '选择代码分支或Tag',
                     branchFilter: 'origin/(.*)',
                     type: 'PT_BRANCH_TAG',
                     defaultValue: 'master',
                     useRepository: '.*luna.git',
                     sortMode: 'ASCENDING'),
        choice(name: 'COMPONENT',
               choices: ['all', 'luna-chaincode-manager', 'luna-extractor', 'luna-invoke-middleware', 'luna-user-manager'],
               description: '选择需要打包镜像的组件')
      ])
    ])
    node(POD_LABEL) {
      stage('编译') {
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
                    userRemoteConfigs: [[credentialsId: 'ted_gitlab', url: 'http://172.16.101.211/zhangpeng/luna.git']]
                   ])

          echo "====== [DEBUG]: Before building ======"
          echo "Branch/Tag: ${params.BRANCH_TAG}"
          echo "Image tag: ${env.IMAGE_TAG}"
          echo "Component: ${params.COMPONENT}"
          sh 'mvn --version'
          echo "======================================"


          sh 'mvn -U clean install -Dmaven.test.skip=true -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true'

          echo "====== [DEBUG]: After building ======"
          echo "======================================"
        }
      }
      stage('打包Docker镜像'){
        if (params.COMPONENT == 'all') {
          echo "Build all components"
          //parallel(
            //'luna-chaincode-manager': {
              stage('luna-chaincode-manager'){
                env.COMP = 'luna-chaincode-manager'
                container('kaniko') {
                  echo "${env.COMP}"
                  sh "/kaniko/executor -f `pwd`/${env.COMP}/Dockerfile -c `pwd`/${env.COMP} --insecure --skip-tls-verify --cache=true --destination=docker.ted.mighty/${env.COMP}:${env.IMAGE_TAG}"
                }
              }
            //},
            //'luna-extractor': {
              stage('luna-extractor'){
                env.COMP = 'luna-extractor'
                container('kaniko') {
                  echo "${env.COMP}"
                  sh "/kaniko/executor -f `pwd`/${env.COMP}/Dockerfile -c `pwd`/${env.COMP} --insecure --skip-tls-verify --cache=true --destination=docker.ted.mighty/${env.COMP}:${env.IMAGE_TAG}"
                }
              }
            //},
            //'luna-invoke-middleware':{
              stage('luna-invoke-middleware'){
                env.COMP = 'luna-invoke-middleware'
                container('kaniko') {
                  echo "${env.COMP}"
                  sh "/kaniko/executor -f `pwd`/${env.COMP}/Dockerfile -c `pwd`/${env.COMP} --insecure --skip-tls-verify --cache=true --destination=docker.ted.mighty/${env.COMP}:${env.IMAGE_TAG}"
                }
              }
            //},
            //'luna-user-manager':{
              stage('luna-user-manager'){
                env.COMP = 'luna-user-manager'
                container('kaniko') {
                  echo "${env.COMP}"
                  sh "/kaniko/executor -f `pwd`/${env.COMP}/Dockerfile -c `pwd`/${env.COMP} --insecure --skip-tls-verify --cache=true --destination=docker.ted.mighty/${env.COMP}:${env.IMAGE_TAG}"
                }
              }
            //}
          //)//parallel

        } else {
          container('kaniko') {
            echo "Building: ${params.COMPONENT}"
            sh "/kaniko/executor -f `pwd`/${params.COMPONENT}/Dockerfile -c `pwd`/${params.COMPONENT} --insecure --skip-tls-verify --cache=true --destination=docker.ted.mighty/${params.COMPONENT}:${env.IMAGE_TAG}"
          }
        }
      }
    }//node(POD_LABEL)
  }//podTemplate
}//timestamps
