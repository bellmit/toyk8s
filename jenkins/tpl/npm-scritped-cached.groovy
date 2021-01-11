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
  - name: npm 
    image: docker.ted.mighty/node:cnpm
    imagePullPolicy: IfNotPresent
    command: ['cat']
    tty: true
    volumeMounts:
    - mountPath: "~/.npm"
      name: cache
    resources:
      limits:
        memory: "512Mi"
        cpu: "1000m"
      requests:
        memory: "256Mi"
        cpu: "500m"
  volumes:
  - name: cache
    persistentVolumeClaim:
      claimName: npm-cache
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

      stage('NPM Build') {

        container('npm') {
          checkout([$class: 'GitSCM',
                    branches: [[name: "${params.BRANCH_TAG}"]],
                    doGenerateSubmoduleConfigurations: false,
                    extensions: [],
                    submoduleCfg: [],
                    userRemoteConfigs: [[credentialsId: 'ted_gitlab', url: 'http://172.16.101.211/knowledge-sharing/document-collection-front.git']]
                   ])

          env.BRANCH_TAG = "${params.BRANCH_TAG}"
          echo "${env.BRANCH_TAG}"
          env.IMAGE_TAG = 'latest'

          if (env.BRANCH_TAG != 'master'){
            env.IMAGE_TAG = "${env.BRANCH_TAG}"
          }   

          echo "------ [DEBUG]: Before building ------"
          echo "Brangh/Tag: ${env.BRANCH_TAG}"
          echo "Image tag: ${env.IMAGE_TAG}"
          sh 'npm --version'
          sh 'du -sh ~/.npm'
          sh 'ls -al ~/.npm'
          echo "--------------------------------------"

          sh 'cnpm version'
          sh 'cnpm install'
          sh 'npm run build'

          echo "-----[DEBUG]: After building ------"
          sh 'du -sh ~/.npm'
          sh 'ls -al ~/.npm'
          echo "--------------------------------------"
        }
      }
    }
  }
}
