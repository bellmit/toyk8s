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
    image: docker.cetcxl.local/mvn:ci-3.6-jdk8
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
    image: docker.cetcxl.local/kaniko-executor:v1.6.0-debug
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
	
    stage('准备'){
		// Debug
        sh "env" 
        echo "====== 拉取代码 ======"

        checkout([$class: 'GitSCM',
            branches: [[name: "${gitlabTargetBranch}"]],
            doGenerateSubmoduleConfigurations: false,
            extensions: [[
                $class: 'SubmoduleOption', 
                disableSubmodules: false, 
                parentCredentials: true, 
                recursiveSubmodules: true, 
                reference: '', 
                // trace submodule: "git submodule update --remote"
                trackingSubmodules: true
            ]],
            submoduleCfg: [], 
			// 'gitlab'为Jenkins上的Credential ID
            userRemoteConfigs: [[credentialsId: 'gitlab', url: "${gitlabSourceRepoHttpUrl}"]]
        ])  
        //exit 0

	    // 从触发代码Tag来获取镜像Tag	
		def targetBranch = "${gitlabTargetBranch}"
		def tagName = targetBranch.split('/')[-1]
        def svcPrefix = tagName.split('-')[0]
        env.TAG = tagName
        env.TAG_PREFIX = svcPrefix
        env.PACKAGE_DIR = "cluster-deploy/${TAG}"
        echo "[DEBUG]: PACKAGE_DIR: ${PACKAGE_DIR}"
        echo "[DEBUG]: TAG: ${TAG}"
        echo "[DEBUG]: TAG_PREFIX: ${TAG_PREFIX}"

    } // Stage(准备)

    def tmpTag = "${TAG}"
    if(tmpTag.startsWith("base")) {
        stage('编译base image') {
            container('kaniko') {
                sh "cp python/requirements.txt docker-build/docker/base/"
                sh "cat docker-build/docker/base/requirements.txt"
                sh "/kaniko/executor --verbosity=info --log-format=text --log-timestamp=true --dockerfile=`pwd`/docker-build/docker/base/Dockerfile --context=`pwd`/docker-build/docker/base --insecure --skip-tls-verify --cache=false --destination=docker.cetcxl.local/fate-base-image:${TAG}"
                echo "===================================="
			    echo "镜像打包推送成功"
			    echo "docker.cetcxl.local/fate-base-image:${TAG}"
			    echo "===================================="
            }
        } // Base image
    } else {


    stage('编译fateboard跟eggroll') {
        sh "mkdir -p ${PACKAGE_DIR}/python/arch"
        sh "cp -a fate.env RELEASE.md bin conf python examples ${PACKAGE_DIR}"
        sh "pwd;ls ${PACKAGE_DIR}"

        //if(svcPrefix.startsWith("base")) {
        //    echo "**************************************"
        //    stage('编译base image') {
        //        container('kaniko') {
        //            sh "cp python/requirements.txt docker-build/docker/base/"
        //            sh "/kaniko/executor --verbosity=debug --log-format=text --dockerfile=`pwd`/docker-build/docker/base/Dockerfile --context=`pwd`/docker-build/docker/base --insecure --skip-tls-verify --cache=true --destination=docker.cetcxl.local/fate-base-image:${TAG}"
        //            //echo "/kaniko/executor --verbosity=debug --log-format=text --dockerfile=`pwd`/docker-build/docker/base/Dockerfile --context=`pwd`/docker-build/docker/base --insecure --skip-tls-verify --cache=true --destination=docker.cetcxl.local/fate-base-image:${TAG}"

        //        }
        //    } // Base image
        //    //currentBuild.result = 'SUCCESS'
        //    //abort("hehe")
        //}

        parallel fateboard: {
            stage('编译fateboard') {
                container('maven') {
                    echo "====== Building fateboard ======"
                    sh "cd fateboard && ls && pwd && mvn clean install -U -Dmaven.test.skip=true"
                    //echo "cd fateboard && ls && pwd && mvn clean install -U -Dmaven.test.skip=true"
                    sh "mkdir -p ${PACKAGE_DIR}/fateboard/conf; mkdir -p ${PACKAGE_DIR}/fateboard/ssh"
                    sh "cp fateboard/target/*.jar ${PACKAGE_DIR}/fateboard/fateboard.jar"
                    sh "cp fateboard/bin/service.sh ${PACKAGE_DIR}/fateboard"
                    sh "cp fateboard/bin/service.sh ${PACKAGE_DIR}/fateboard"
                    sh "cp fateboard/src/main/resources/application.properties ${PACKAGE_DIR}/fateboard/conf/"
                    sh "touch ${PACKAGE_DIR}/fateboard/ssh/ssh.properties"
                    sh "ls ${PACKAGE_DIR}; ls ${PACKAGE_DIR}/fateboard"
                }
            }

        },
        eggroll: {
            stage('编译eggroll') {
                container('maven') {
                    echo "====== Building eggroll ======"
                    sh "cd eggroll/deploy && bash auto-packaging.sh"
                    sh "mkdir ${PACKAGE_DIR}/eggroll"
                    sh "mv eggroll/eggroll.tar.gz ${PACKAGE_DIR}/eggroll/"
                    sh "cd ${PACKAGE_DIR}/eggroll && tar -xvf eggroll.tar.gz && rm -f eggroll.tar.gz"
                }
            }
        }
    } // Build


    stage('打包镜像'){
        sh "cp -r ${PACKAGE_DIR}/python docker-build/docker/modules/python/python"
        sh "cp -r ${PACKAGE_DIR}/eggroll docker-build/docker/modules/python/eggroll"
        sh "cp -r ${PACKAGE_DIR}/examples docker-build/docker/modules/python/examples"
        sh "cp -r ${PACKAGE_DIR}/conf docker-build/docker/modules/python/conf"
        sh "cp -r ${PACKAGE_DIR}/fate.env docker-build/docker/modules/python/fate.env"

		sh "cp -r ${package_dir}/fateboard docker-build/docker/modules/fateboard/"
        sh "cp -r ${package_dir}/python docker-build/docker/modules/eggroll/"
        sh "cp -r ${package_dir}/eggroll docker-build/docker/modules/eggroll/"
		sh "cp python/requirements.txt docker-build/docker/modules/python/"
		sh "cp python/requirements.txt docker-build/docker/modules/python-nn/"

        echo "开始打包镜像"
        echo "${TAG}"
        echo "${PACKAGE_DIR}"
        sh "ls ${PACKAGE_DIR};cd ${PACKAGE_DIR};du -sh *"
		
        //// 从触发代码仓库URL获取镜像名
		//def repoURL = "${gitlabSourceRepoHttpUrl}"
		//def imageName = (repoURL =~ /.*\/(.*)\.git$/)[0][1]
		//echo "$imageName"
	    // 从触发代码Tag来获取镜像Tag	
		//def targetBranch = "${gitlabTargetBranch}"
		//def tmp = targetBranch.split('/')
		//def imageTag = tmp[-1]
		//echo "Git TAG: $imageTag"
        
		container('kaniko') {
            //def svcList = ["python", "eggroll", "fateboard", "python-nn"]
            def svcList = ["python", "eggroll", "fateboard"]
            for(svc in svcList){
                echo "打包${svc}镜像"
                sh "/kaniko/executor --verbosity=info --log-format=text --log-timestamp=true --build-arg=PREFIX=docker.cetcxl.local --build-arg=TAG=${TAG} --dockerfile=docker-build/docker/modules/${svc}/Dockerfile --context=docker-build/docker/modules/${svc} --insecure --skip-tls-verify --cache=true --destination=docker.cetcxl.local/fate-${svc}:${TAG}"
                //sh "/kaniko/executor --verbosity=debug --log-format=text --build-arg=PREFIX=docker.cetcxl.local --build-arg=TAG=${TAG} --dockerfile=docker-build/docker/modules/${svc}/Dockerfile --context=docker-build/docker/modules/${svc} --insecure --skip-tls-verify --cache=true --destination=docker.cetcxl.local/fate-${svc}:${TAG}"
                echo "===================================="
			    echo "镜像打包推送成功"
			    echo "docker.cetcxl.local/fate-${svc}:${TAG}"
			    echo "===================================="
            }
	    }
    }//stage('Pack Docker Image')
    }//else

  }//node(POD_LABEL)
}//podTemplate
}//timestamps

