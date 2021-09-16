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
  - name: maven
    image: docker.ted.local/mvn:ci-3.6-jdk8
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
    #image: docker.ted.local/kaniko-executor:latest
    image: docker.ted.local/kaniko-executor:v1.6.0-debug
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
    	        secretToken: "valarmorghulis",
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
	
    stage('拉取代码'){
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
	    // 从触发代码Tag来获取镜像Tag	
		def targetBranch = "${gitlabTargetBranch}"
		def tagName = targetBranch.split('/')[-1]
        def svcPrefix = tagName.split('-')[0]
        env.PREFIX = svcPrefix

        switch(svcPrefix) {
            case "server":
                env.SVC_REPO_DIR = "fate-serving-server"
            	break
			case "proxy":
			    env.SVC_REPO_DIR = "fate-serving-proxy"
			    break
			case "admin":
			    env.SVC_REPO_DIR = "fate-serving-admin"
                break
            default:
                echo "未找到匹配Tag, 打包全部服务镜像"
                //currentBuild.result = 'ABORTED'
                //error("Abort for unmatched tag: $tagName")
                //return
                env.SVC_REPO_DIR = "ALL"
                break
        }
        
		echo "开始编译项目"
        container('maven'){
            if(SVC_REPO_DIR == "ALL") {
                sh 'mvn clean install -U -Dmaven.test.skip=true'
            } else {
                sh "cd ${SVC_REPO_DIR}"
                sh 'mvn clean install -U -Dmaven.test.skip=true'
                sh "cd .."
            }
        }
    } // Build

    stage('打包镜像'){
		echo "Pack Docker Image"
        echo "${SVC_REPO_DIR}"
        sh "ls"
		
        //// 从触发代码仓库URL获取镜像名
		//def repoURL = "${gitlabSourceRepoHttpUrl}"
		//def imageName = (repoURL =~ /.*\/(.*)\.git$/)[0][1]
		//echo "$imageName"
	    // 从触发代码Tag来获取镜像Tag	
		def targetBranch = "${gitlabTargetBranch}"
		def tmp = targetBranch.split('/')
		def imageTag = tmp[-1]
		echo "Git TAG: $imageTag"
        
		container('kaniko') {
            def svcList = []
		    if(SVC_REPO_DIR == "ALL") {
		        svcList.addAll(["fate-serving-server", "fate-serving-proxy", "fate-serving-admin"])
		    } else {
                svcList[0] = "${SVC_REPO_DIR}"
		    }
            println svcList
            for(svc in svcList){
                echo "打包${svc}镜像"
                sh "/kaniko/executor --verbosity=debug --log-format=text --dockerfile=`pwd`/${svc}/Dockerfile --context=`pwd`/${svc} --insecure --skip-tls-verify --cache=true --destination=docker.ted.local/${svc}:${imageTag}"
                echo "===================================="
			    echo "镜像打包推送成功"
			    echo "docker.ted.local/${svc}:${imageTag}"
			    echo "===================================="
            }

		}
    }//stage('Packa Docker Image')

  }//node(POD_LABEL)
}//podTemplate
}//timestamps

