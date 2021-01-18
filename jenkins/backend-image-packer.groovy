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
	    // 从触发代码Tag来获取镜像Tag	
		def targetBranch = "${gitlabTargetBranch}"
		def tagName = targetBranch.split('/')[-1]
        def svcPrefix = tagName.split('-')[0]
        env.PREFIX = svcPrefix

        switch(svcPrefix) {
            case "open":
                env.SVC_REPO_DIR = "dap-open-platform"
            	break
			case "operate":
				env.SVC_REPO_DIR = "dap-operate-platform"
				break
			case "manage":
				env.SVC_REPO_DIR = "dap-data-manage-platform"
                break
			case "sync":
				env.SVC_REPO_DIR = "dap-data-hive-sync-server"
                break
			case "execution":
				env.SVC_REPO_DIR = "dap-algorithm-execution-platform"
                break
			case "handle":
				env.SVC_REPO_DIR = "dap-algorithm-handle-platform"
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
            sh 'mvn clean install -U -Dmaven.test.skip=true'
        }
    } // Build

    stage('Pack Docker Image'){
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
		    if(SVC_REPO_DIR == "ALL") {
		        def svcList = ["dap-open-platform", "dap-operate-platform", "dap-data-manage-platform", "dap-data-hive-sync-server", "dap-algorithm-execution-platform", "dap-algorithm-handle-platform"]
                for(svc in svcList){
                    sh "/kaniko/executor --verbosity=debug -f `pwd`/${svc}/Dockerfile -c `pwd`/${svc} --insecure --skip-tls-verify --cache=true --destination=docker.cetcxl.local/${svc}:${imageTag}"
                    echo "===================================="
			        echo "镜像打包推送成功"
			        echo "docker.cetcxl.local/${svc}:${imageTag}"
			        echo "===================================="
                }
		    } else {
		        //sh "/kaniko/executor -f `pwd`/Dockerfile -c `pwd` --insecure --skip-tls-verify --cache=true --destination=docker.cetcxl.local/${imageName}:${imageTag}"
			    sh "/kaniko/executor --verbosity=debug -f `pwd`/${SVC_REPO_DIR}/Dockerfile -c `pwd`/${SVC_REPO_DIR} --insecure --skip-tls-verify --cache=true --destination=docker.cetcxl.local/${SVC_REPO_DIR}:${imageTag}"
			    echo "===================================="
			    echo "镜像打包推送成功"
			    echo "docker.cetcxl.local/${SVC_REPO_DIR}:${imageTag}"
			    echo "===================================="
		    }
		}
    }//stage('Packa Docker Image')

  }//node(POD_LABEL)
}//podTemplate
}//timestamps

