#!groovy
// Scripted Pipeline
  podTemplate(yaml: """
kind: Pod
spec:
  containers:
  - name: jnlp
    image: '172.16.101.214:5000/jenkins-slave:test'
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
    image: 172.16.101.214:5000/node:stretch
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
    // image: 172.16.101.214:5000/mvn:3.3.9-jdk-8-alpine
    image: 172.16.101.214:5000/mvn:ci
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
    image: 172.16.101.214:5000/kaniko-executor:debug-v0.24.0
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
  - name: ci-nexus-cred
  volumes:
  - name: jenkins-docker-cfg
    configMap:
      name: nexus-cred
"""
  ){
	
    // Pipeline Parameters
    properties([
      parameters([
        gitParameter(name: 'BRANCH_XLPAY_ADMIN',
                     description: '选择xlpay-admin代码分支',
                     branchFilter: 'origin/(.*)',
                     type: 'PT_BRANCH_TAG',
                     defaultValue: 'master',
                     useRepository: '.*xlpay.git',
                     sortMode: 'ASCENDING'),
        gitParameter(name: 'BRANCH_PAY_TRUSTLINK_DATA',
                     description: '选择pay-trustlink-data分支',
                     branchFilter: 'origin/(.*)',
                     type: 'PT_BRANCH_TAG',
                     defaultValue: 'master',
                     useRepository: '.*pay-trustlink-data.git',
                     sortMode: 'ASCENDING'),
        gitParameter(name: 'BRANCH_PAY_WEB',
                     description: '选择pay-web分支',
                     branchFilter: 'origin/(.*)',
                     type: 'PT_BRANCH_TAG',
                     defaultValue: 'master',
                     useRepository: '.*pay-web.git',
                     sortMode: 'ASCENDING'),	
		choice(choices: ['test', 'prod'], description: '配置版本', name: 'CONFIG'),
		string(defaultValue: 'xyf', description: '部署namespace名称', name: 'NAMESPACE', trim: false),			 
		extendedChoice(defaultValue: 'xlpay_admin,xlpay_pay_user,pay_trustlink_data,pay_web', description: '选择需要部署的组件', descriptionPropertyValue: '部署xlpay_admin,部署xlpay_pay_user,部署pay_trustlink_data,部署pay_web', multiSelectDelimiter: ',', name: 'deploy_list', quoteValue: false, saveJSONParameterToFile: false, type: 'PT_CHECKBOX', value: 'xlpay_admin,xlpay_pay_user,pay_trustlink_data,pay_web', visibleItemCount: 4)	
      ])
    ])
	
    node(POD_LABEL) {
	
	env.IMAGE_TAG_XLPAY_ADMIN = 'latest'
    if (params.BRANCH_XLPAY_ADMIN != 'master') {
        env.IMAGE_TAG_XLPAY_ADMIN = "${params.BRANCH_XLPAY_ADMIN}"
    }
	
	env.IMAGE_TAG_PAY_TRUSTLINK_DATA = 'latest'
    if (params.BRANCH_PAY_TRUSTLINK_DATA != 'master') {
        env.IMAGE_TAG_PAY_TRUSTLINK_DATA = "${params.BRANCH_PAY_TRUSTLINK_DATA}"
    }
	
	env.IMAGE_TAG_PAY_WEB = 'latest'
    if (params.BRANCH_PAY_WEB != 'master') {
        env.IMAGE_TAG_PAY_WEB = "${params.BRANCH_PAY_WEB}"
    }
	env.NAMESPACE = params.NAMESPACE
	env.CONFIG = params.CONFIG
	list = env.deploy_list
	echo list
	echo ","
	String[] str;
    str = list.split(",");
    str.each{
	    echo it
		if ( it == 'xlpay_admin'){
            env.xlpay_admin=true
			echo "是否部署xlpay_admin:${xlpay_admin}"
        }
		if ( it == 'xlpay_pay_user'){
            env.xlpay_pay_user=true
			echo "是否部署xlpay_pay_user:${xlpay_pay_user}"
        }
        if ( it == 'pay_trustlink_data'){
            env.pay_trustlink_data=true
			echo "是否部署pay_trustlink_data:${pay_trustlink_data}"
        }
        if ( it == 'pay_web'){
            env.pay_web=true
			echo "是否部署pay_web:${pay_web}"
        }
    }
	
	stage('编译') {
		download_code:{
			container('maven'){
				checkout([$class: 'GitSCM',
						branches: [[name: "${params.BRANCH_XLPAY_ADMIN}"]],
						doGenerateSubmoduleConfigurations: false,
						extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'xlpay']],
						submoduleCfg: [],
						userRemoteConfigs: [[credentialsId: 'ted_gitlab', url: 'http://172.16.101.211/business-project/xlpay/xlpay.git']]
					   ])	
			    checkout([$class: 'GitSCM',
						branches: [[name: "${params.BRANCH_PAY_TRUSTLINK_DATA}"]],
						doGenerateSubmoduleConfigurations: false,
						extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'pay-trustlink-data']],
						submoduleCfg: [],
						userRemoteConfigs: [[credentialsId: 'ted_gitlab', url: 'http://172.16.101.211/business-project/xlpay/pay-trustlink-data.git']]
					   ])	
			    checkout([$class: 'GitSCM',
						branches: [[name: "${params.BRANCH_PAY_WEB}"]],
						doGenerateSubmoduleConfigurations: false,
						extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'pay-web']],
						submoduleCfg: [],
						userRemoteConfigs: [[credentialsId: 'ted_gitlab', url: 'http://172.16.101.211/business-project/xlpay/pay-web.git']]
					   ])						
			}	
		}
	    parallel build_xlpay_admin: {
			if (env.xlpay_admin == 'true' || env.xlpay_pay_user == 'true') {
				container('maven'){
				    echo "====== [DEBUG]: Before building xlpay admin======"
				    echo "Branch/Tag: ${params.BRANCH_XLPAY_ADMIN}"
				    echo "Image tag: ${env.IMAGE_TAG_XLPAY_ADMIN}"
				    sh 'cd xlpay;mvn clean install -U -Dmaven.test.skip=true'
				    echo "====== [DEBUG]: After building ======"
				}			
			}		
		}, build_pay_trustlink_data: {
			if (env.pay_trustlink_data == 'true') {
				container('maven'){
				    echo "====== [DEBUG]: Before building ======"
				    echo "Branch/Tag: ${params.BRANCH_PAY_TRUSTLINK_DATA}"
				    echo "Image tag: ${env.IMAGE_TAG_PAY_TRUSTLINK_DATA}"
				    sh 'cd pay-trustlink-data;mvn clean install -U -Dmaven.test.skip=true'
				    echo "====== [DEBUG]: After building ======"
				}				
			}		
	
		}, build_pay_web: {
			if (env.pay_web == 'true') {
				container('npm'){
				    echo "====== [DEBUG]: Before building ======"
				    echo "Branch/Tag: ${params.BRANCH_PAY_WEB}"
				    echo "Image tag: ${env.BRANCH_PAY_WEB}"
				    sh 'npm config set registry http://172.16.101.214:8081/repository/npm/'
				    sh 'cd pay-web;ls;npm install;npm run build'
				    echo "====== [DEBUG]: After building ======"
				}			
			}
		}
	}
	

    stage('build Docker image'){
		build_image_xlpay_admin: {
			if (env.xlpay_admin == 'true') {
				container('kaniko') {
					sh "/kaniko/executor -f `pwd`/xlpay/xlpay-admin/Dockerfile -c `pwd`/xlpay/xlpay-admin --insecure --skip-tls-verify --cache=true --destination=docker.ted.local/xlpay-admin:${IMAGE_TAG_XLPAY_ADMIN}"
				}			
			}			
		}
		build_image_xlpay_pay_user: {
			if (env.xlpay_pay_user == 'true') {
				container('kaniko') {
					sh "/kaniko/executor -f `pwd`/xlpay/xlpay-pay-user/Dockerfile -c `pwd`/xlpay/xlpay-pay-user --insecure --skip-tls-verify --cache=true --destination=docker.ted.local/xlpay-pay-user:${IMAGE_TAG_XLPAY_ADMIN}"
				}
			}			
		}
		buld_image_pay_trustlink_data: {
			if (env.pay_trustlink_data == 'true') {
				container('kaniko') {		    
					sh "/kaniko/executor -f `pwd`/pay-trustlink-data/Dockerfile -c `pwd`/pay-trustlink-data --insecure --skip-tls-verify --cache=true --destination=docker.ted.local/pay-trustlink-data:${IMAGE_TAG_PAY_TRUSTLINK_DATA}"
				}
			}
		}
		build_image_pay_web: {
			if (env.pay_web == 'true') {
				container('kaniko') {
					sh "/kaniko/executor -f `pwd`/pay-web/Dockerfile -c `pwd`/pay-web --insecure --skip-tls-verify --cache=true --destination=docker.ted.local/pay-web:${IMAGE_TAG_PAY_WEB}"
				}
			}
		}
    }//stage('build Docker image')
	
    stage('部署'){
		download_yaml: {
			checkout([$class: 'GitSCM',
						branches: [[name: "*/master"]],
						doGenerateSubmoduleConfigurations: false,
						extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'ci']],
						submoduleCfg: [],
						userRemoteConfigs: [[credentialsId: 'ted_gitlab', url: 'http://172.16.101.211/business-project/xlpay/ci.git']]
					])	
		}		

		deploy_xlpay_admin: {
			if (env.xlpay_admin == 'true') {
				env.IMAGE_TAG=env.IMAGE_TAG_XLPAY_ADMIN
				container('jnlp') {					
					sh "set +e;kubectl -n ${NAMESPACE} delete configmap xlpay-admin;set -e"
					sh "kubectl -n ${NAMESPACE} create configmap xlpay-admin --from-env-file=./ci/config/${CONFIG}/xlpay-admin.env"	
					sh "envsubst < `pwd`/ci/deploy_yaml/xyf-xlpay-admin.yaml|kubectl -n ${NAMESPACE} apply -f -"				
				}
			}
		}
		deploy_pay_trustlink_data: {
			if (env.pay_trustlink_data == 'true') {
				env.IMAGE_TAG=env.IMAGE_TAG_PAY_TRUSTLINK_DATA	
				container('jnlp') {
					sh "set +e;kubectl -n ${NAMESPACE} delete configmap pay-trustlink-data;set -e"
					sh "kubectl -n ${NAMESPACE} create configmap pay-trustlink-data --from-env-file=./ci/config/${CONFIG}/pay-trustlink-data.env"	
					sh "envsubst < `pwd`/ci/deploy_yaml/xyf-pay-trustlink-data.yaml|kubectl -n ${NAMESPACE} apply -f -"	
				}
			}
		}
		deploy_xlpay_pay_user: {
			if (env.xlpay_pay_user == 'true') {
				env.IMAGE_TAG=env.IMAGE_TAG_XLPAY_ADMIN
				container('jnlp') {				
					sh "set +e;kubectl -n ${NAMESPACE} delete configmap xlpay-pay-user;set -e"
					sh "kubectl -n ${NAMESPACE} create configmap xlpay-pay-user --from-env-file=./ci/config/${CONFIG}/xlpay-pay-user.env"	
					sh "envsubst < `pwd`/ci/deploy_yaml/xyf-xlpay-pay-user.yaml|kubectl -n ${NAMESPACE} apply -f -"	
				}
			}
		}
		deploy_pay_web: {
			if (env.pay_web == 'true') {
				env.IMAGE_TAG=env.IMAGE_TAG_PAY_WEB
				container('jnlp') {	
					sh "envsubst < `pwd`/ci/deploy_yaml/xyf-pay-web.yaml|kubectl -n ${NAMESPACE} apply -f -"	
				}
			}			
		}
		
		if (env.CONFIG == 'prod') {
    		deploy_ingress: {
    		    	sh "kubectl -n ${NAMESPACE} apply -f `pwd`/ci/deploy_yaml/xyf-ingress.yaml"
    				sh "sleep 10s"
    				sh "kubectl -n ${NAMESPACE} get po -o wide"
    		}
		}
    }//stage('部署')
      
    }//node(POD_LABEL)
  }//podTemplate

