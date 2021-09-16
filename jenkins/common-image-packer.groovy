#!groovy
// Scripted Pipeline
timestamps {
podTemplate(cloud: "kubernetes-hangli",yaml: """
apiversion: v1
kind: Pod
spec:
  cloud: kubernetes-hangli
  containers:
  - name: jnlp
    #image: 'docker.ted.local/jenkins-slave:test'
    image: 'docker.ted.local/jenkins-inbound-agent:4.3-4-alpine'
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
    image: docker.ted.local/node:stretch
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
    image: docker.ted.local/mvn:ci
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
    #image: docker.ted.local/kaniko-executor:debug-v0.24.0
    image: docker.ted.local/kaniko-executor:latest
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
        def projectType = ""
		if(fileExists('requirements.txt')) {
		    projectType = "python"
		}
		if(fileExists('pom.xml')) {
		    projectType = "maven"
		}
		if(fileExists('package.json')) {
		    projectType = "npm"
		}

        switch(projectType) {
            case "python":
				echo "检测到Python项目"
				echo "不需要编译"
                break
            case "maven":
				echo "检测到maven项目"
				echo "开始编译"
        		container('maven'){
        		    sh 'mvn clean install -U -Dmaven.test.skip=true'
        		}
                break
            case "npm":
				echo "检测到npm项目"
				echo "开始编译"
                container('npm'){
                    sh 'npm config set registry http://maven.ted.local/repository/npm/'
                    sh 'ls;npm install;npm run build;ls'
                }
                break
            default:
                echo "未找到匹配项目，退出"
                currentBuild.result = 'ABORTED'
				sh "pwd;ls"
                error("unknown project")
                break
        } //switch
    } //Build

    stage('Pack Docker Image'){
        // 从触发代码仓库URL获取镜像名
		def repoURL = "${gitlabSourceRepoHttpUrl}"
		def imageName = (repoURL =~ /.*\/(.*)\.git$/)[0][1]
	    // 从触发代码Tag来获取镜像Tag	
		def targetBranch = "${gitlabTargetBranch}"
		def imageTag = targetBranch.split('/')[-1]
		echo "docker.ted.local/$imageName:$imageTag"
        
		container('kaniko') {
			//sh "/kaniko/executor -f `pwd`/Dockerfile -c `pwd` --insecure --skip-tls-verify --cache=true --destination=docker.ted.local/${imageName}:${imageTag}"
			sh "/kaniko/executor --verbosity=debug -f `pwd`/Dockerfile -c `pwd` --insecure --skip-tls-verify --cache=true --destination=docker.ted.local/${imageName}:${imageTag}"
			echo "===================================="
			echo "镜像打包推送成功"
			echo "镜像名: docker.ted.local/${imageName}:${imageTag}"
			echo "===================================="
		}
    }//stage('Packa Docker Image')

  }//node(POD_LABEL)
}//podTemplate
}//timestamps



