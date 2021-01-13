#!groovy
// Scripted Pipeline
podTemplate(cloud: "kubernetes-hangli",yaml: """
apiversion: v1
kind: Pod
spec:
  cloud: kubernetes-hangli
  containers:
  - name: jnlp
    #image: 'docker.cetcxl.local/jenkins-slave:test'
    image: 'docker.cetcxl.local/jenkins-inbound-agent:4.3-4-alpine'
    imagePullPolicy: Always
    args: ['\$(JENKINS_SECRET)', '\$(JENKINS_NAME)']
    resources:
      limits:
        memory: "512Mi"
        cpu: "1000m"
      requests:
        memory: "256Mi"
        cpu: "500m"
  - name: npm
    image: docker.cetcxl.local/node:stretch
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
  - name: maven
    image: docker.cetcxl.local/mvn:ci
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
    #image: docker.cetcxl.local/kaniko-executor:debug-v0.24.0
    image: docker.cetcxl.local/kaniko-executor:latest
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
  - name: docker-hangli
  volumes:
  - name: jenkins-docker-cfg
    configMap:
      name: nexus-cred
"""
){
	
    properties([
    	pipelineTriggers([
    	    [
    	        $class: 'GitLabPushTrigger',
    	        branchFilterType: 'All',
    	        triggerOnPush: true,
    	        triggerOnMergeRequest: false,
    	        triggerOpenMergeRequestOnPush: "never",
    	        triggerOnNoteRequest: true,
    	        noteRegex: "Jenkins please retry a build",
    	        skipWorkInProgressMergeRequest: true,
				// 填写在Jenkins上的Gitlab token名
    	        secretToken: "image_packer_webhook_token",
    	        ciSkip: false,
    	        setBuildDescription: true,
    	        addNoteOnMergeRequest: true,
    	        addCiMessage: true,
    	        addVoteOnMergeRequest: true,
    	        acceptMergeRequestOnSuccess: false,
    	        branchFilterType: "All",
    	        //includeBranchesSpec: "release/qat",
    	        excludeBranchesSpec: "",
    	    ]
    	]) // pipelineTriggers
    ]) // properties
	
    node(POD_LABEL) {
	
	//env.IMAGE_TAG_BACKEND = 'latest'
    //env.PROJECT_TYPE = 'npm'

    stage('Check Out'){
		// Debug
        sh "env" 
        checkout([$class: 'GitSCM',
                branches: [[name: "${gitlabTargetBranch}"]],
                doGenerateSubmoduleConfigurations: false,
                //extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'backend-service']],
                submoduleCfg: [], 
				// 'gitlab'为Jenkins上的Credential ID
                userRemoteConfigs: [[credentialsId: 'gitlab', url: "${gitlabSourceRepoHttpUrl}"]]
               ])  

    } // Checkout

    stage('编译') {
		if(fileExists('requirements.txt')) {
			echo "检测到requirements.txt"
			echo "Python项目，不需要编译"
		} else {
			if(fileExists('pom.xml')) {
				echo "检测到pom.xml"
				echo "开始编译"
                echo "TBD"
			} else {
				if (fileExists('package.json')) {
					echo "检测到package.json"
					echo "开始编译NPM项目"
                    container('npm'){
                        sh 'npm config set registry http://maven.cetcxl.local/repository/npm/'
                        sh 'ls;npm install;npm run build;ls'
                    }
				}
			}// npm
		}
    } // Build

    stage('Pack Docker Image'){
        // 从触发代码仓库URL获取镜像名
		def repoURL = "${gitlabSourceRepoHttpUrl}"
		def imageName = (repoURL =~ /.*\/(.*)\.git$/)[0][1]
		echo "$imageName"
	    // 从触发代码Tag来获取镜像Tag	
		def targetBranch = "${gitlabTargetBranch}"
		def tmp = targetBranch.split('/')
		def imageTag = tmp[-1]
		echo "docker.cetcxl.local/$imageName:$imageTag"
        
		container('kaniko') {
			//sh "/kaniko/executor -f `pwd`/Dockerfile -c `pwd` --insecure --skip-tls-verify --cache=true --destination=docker.cetcxl.local/${imageName}:${imageTag}"
			sh "/kaniko/executor --verbosity=debug -f `pwd`/Dockerfile -c `pwd` --insecure --skip-tls-verify --cache=true --destination=docker.cetcxl.local/${imageName}:${imageTag}"
			echo "===================================="
			echo "镜像打包推送成功"
			echo "docker.cetcxl.local/${imageName}:${imageTag}"
			echo "===================================="
		}
    }//stage('Packa Docker Image')

  }//node(POD_LABEL)
}//podTemplate


