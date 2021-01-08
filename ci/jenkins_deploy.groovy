timestamps {
    echo "======please check parameters======"
    echo "TARGET_ENV_ADDRESS: $TARGET_ENV_ADDRESS"
    echo "RESET_SERVICE : $RESET_SERVICE "
    echo "RESET_MYSQL: $RESET_MYSQL"
    echo "WORK_NODE: $WORK_NODE"
    echo "BRANCH_FOR_XLPAY: $BRANCH_FOR_XLPAY"
    echo "BRANCH_FOR_TRUSTLINK_DATA: $BRANCH_FOR_TRUSTLINK_DATA"
    echo "BRANCH_FOR_WEB: $BRANCH_FOR_WEB"
    echo "MySQL_PASSWORD: $MYSQL_PASSWORD"
    echo "BINLOG_IP: $BINLOG_IP"
	echo "BINLOG_PASSWORD: $BINLOG_PASSWORD"
	String[] str;
    str = env.deploy_list.split(",");
    str.each{
        if ( it == 'mysql'){
            env.mysql=true
        }
        if ( it == 'redis'){
            env.redis=true
        }
		if ( it == 'xlpay_admin'){
            env.xlpay_admin=true
        }
		if ( it == 'xlpay_pay_user'){
            env.xlpay_pay_user=true
        }
        if ( it == 'pay_trustlink_data'){
            env.pay_trustlink_data=true
        }
        if ( it == 'pay_web'){
            env.pay_web=true
        }
		if ( it == 'xstor'){
		    env.xstor=true
		}
    }
	
	
    node('COMMON_WORKER') {
        env.TARGET_ENV_ADDRESS = "$TARGET_ENV_ADDRESS"
        env.MYSQL_PASSWORD = params.MYSQL_PASSWORD
        env.TARGET_ENV_LABEL = "$WORK_NODE"
        env.RESET_SERVICE  = "$RESET_SERVICE"
        env.RESET_MYSQL  = "$RESET_MYSQL"
        env.PROJECT_NETWORK = "xyf"
        env.PROJECT_NAME = "xyf"
        env.BRANCH_FOR_xlpay = "$BRANCH_FOR_XLPAY"
        env.BRANCH_FOR_TRUSTLINK_DATA = "$BRANCH_FOR_TRUSTLINK_DATA"
        env.BRANCH_FOR_WEB = "$BRANCH_FOR_WEB"
        env.BINLOG_IP = "$BINLOG_IP"
		env.BINLOG_PASSWORD = "$BINLOG_PASSWORD"
        stage('编译') {
            Build_xlpay_core: {
				if (env.xlpay_admin == 'true' || env.xlpay_pay_user == 'true') {
					task '编译 xlpay-core'
					ws {
						build job: 'build_mvn_package', parameters: [string(name: 'GIT_REPO_ADDR', value: "git@172.16.101.211:business-project/xlpay/xlpay.git"),
																			string(name: 'GIT_BRANCH', value: env.BRANCH_FOR_XLPAY),
																			string(name: 'PROJECT_SUB_PATH', value: "")]
						cleanWs()
					}				
				}

            }
            parallel Build_xlpay_admin: {
				if (env.xlpay_admin == 'true') {
					task '编译 xlpay-admin'
					ws {
						build job: 'build_mvn_docker_service', parameters: [string(name: 'GIT_REPO_ADDR', value: "git@172.16.101.211:business-project/xlpay/xlpay.git"),
																			string(name: 'GIT_BRANCH', value: env.BRANCH_FOR_XLPAY),
																			string(name: 'PROJECT_SUB_PATH', value: "xlpay-admin")]
						cleanWs()
					}				
				}

            }, Build_xlpay_pay_user: {
				if (env.xlpay_pay_user == 'true') {
					task '编译 xlpay-pay-user'
					ws {
						build job: 'build_mvn_docker_service', parameters: [string(name: 'GIT_REPO_ADDR', value: "git@172.16.101.211:business-project/xlpay/xlpay.git"),
																			string(name: 'GIT_BRANCH', value: env.BRANCH_FOR_XLPAY),
																			string(name: 'PROJECT_SUB_PATH', value: "xlpay-pay-user")]
						cleanWs()
					}				
				}

            },Build_pay_trustlink_data: {
				if (env.pay_trustlink_data == 'true') {
					task '编译 pay-trustlink-data'
					ws {
						build job: 'build_mvn_docker_service', parameters: [string(name: 'GIT_REPO_ADDR', value: "git@172.16.101.211:business-project/xlpay/pay-trustlink-data.git"),
																			string(name: 'GIT_BRANCH', value: env.BRANCH_FOR_TRUSTLINK_DATA),
																			string(name: 'PROJECT_SUB_PATH', value: "")]
						cleanWs()
					}				
				}

            }, Build_Zxl_Ec_Store: {
			    if (env.xstor == 'true') {
					task '编译 zxl-evidence-center'
					ws {
						build job: 'build_mvn_docker_service', parameters: [string(name: 'GIT_REPO_ADDR', value: "git@172.16.101.211:zhixinlian/zxl-evidence-center.git"),
																			string(name: 'GIT_BRANCH', value: 'sffy'),
																			string(name: 'IMAGE_NAME', value: 'ec-store'),
																			string(name: 'DEPLOY_SCRIPTS_PATH', value: "int-ec-store")]
						cleanWs()
					}				
				}

            }, Build_pay_web: {
				if (env.pay_web == 'true') {
					task '编译 pay-web'
					ws {
						build job: 'build_npm_dist', parameters: [string(name: 'GIT_REPO_ADDR', value: "git@172.16.101.211:business-project/xlpay/pay-web.git"),
																  string(name: 'GIT_BRANCH', value: env.BRANCH_FOR_WEB)]
					}					
				}

			}, Scp_DeployRepo_To_Target: {
                task '更新部署脚本/opt/ci/zp/'
                ws {
                    build job: 'deploy_repo_to_remote', parameters: [string(name: 'GIT_REPO_ADDR', value: 'git@172.16.101.211:devops/deploy.git'),
                                                                     string(name: 'DEPLOY_PATH', value: '/opt/ci/' + env.PROJECT_NAME),
                                                                     string(name: 'GIT_BRANCH', value: 'master'),
                                                                     string(name: 'DEPLOY_TARGET', value: env.TARGET_ENV_ADDRESS)]
                    cleanWs()
                }
            }
            cleanWs()
        } //stage('编译')


        stage('部署基础服务') {
            parallel MySQL: {
                if (env.RESET_MYSQL == 'true' && env.mysql == 'true') {
                    task '重启MySQL'
                    ws {
                        build job: 'Reinit_MySQL', parameters: [string(name: 'PROJECT_NAME', value: env.PROJECT_NAME),
                                                               [$class: 'hudson.model.PasswordParameterValue', name: 'MYSQL_PASS', value: env.MYSQL_PASSWORD],
                                                               booleanParam(name: 'RESET_ALL', value: params.RESET_MYSQL),
                                                                [$class: 'LabelParameterValue', name: 'WORK_NODE', label: env.TARGET_ENV_LABEL]]
                        cleanWs()
                    }
                } else {
                    task '保留MySQL'
                }
            }, Redis: {
                if (env.RESET_MYSQL == 'true' && env.redis == 'true') {
                    task '重启Redis'
                    ws {
                        build job: 'Reinit_Redis', parameters: [string(name: 'PROJECT_NAME', value: env.PROJECT_NAME),
                                                                [$class: 'LabelParameterValue', name: 'WORK_NODE', label: env.TARGET_ENV_LABEL]]
                        cleanWs()
                    }
                } else {
                    task '保留Redis'
                }
            }

            cleanWs()
        } //stage('基础服务')

        stage('启动服务') {
            
            Start_xlpay_admin: {
				if (env.xlpay_admin == 'true') {
					task '启动 xlpay_admin'
					ws {
						build job: 'deploy_private_image', parameters: [string(name: 'IMAGE_NAME', value: "xlpay-admin:ci-" + env.BRANCH_FOR_XLPAY),
																		string(name: 'TARGET_IMAGE_NAME', value: "xlpay-admin"),
																		string(name: 'DEPLOY_TARGET', value: env.TARGET_ENV_ADDRESS)]
					    build job: 'load_deploy_from_image', parameters: [string(name: 'IMAGE_NAME', value: "xlpay-admin"),
																		  string(name: 'PROJECT_NAME', value: env.PROJECT_NAME),
																		  booleanParam(name: 'REINIT', value: params.RESET_SERVICE),
																		  [$class: 'LabelParameterValue', name: 'WORK_NODE', label: env.TARGET_ENV_LABEL]]
					    build job: 'Restart_xlpay_admin', parameters: [string(name: 'PROJECT_NAME', value: env.PROJECT_NAME),
																			  [$class: 'hudson.model.PasswordParameterValue', name: 'MYSQL_PASSWORD', value: env.MYSQL_PASSWORD],
																			  booleanParam(name: 'REINIT', value: env.RESET_SERVICE),
																			  booleanParam(name: 'RESET_MYSQL', value: env.RESET_MYSQL),
																			  [$class: 'LabelParameterValue', name: 'WORK_NODE', label: env.TARGET_ENV_LABEL]]
						cleanWs()
					}				
				} else {
					task '保留xlpay_admin'
				}

            }
            parallel Start_xlpay_pay_user: {
				if (env.xlpay_pay_user == 'true') {
					task '启动 xlpay_pay_user'
					ws {
						build job: 'deploy_private_image', parameters: [string(name: 'IMAGE_NAME', value: "xlpay-pay-user:ci-" + env.BRANCH_FOR_XLPAY),
																		string(name: 'TARGET_IMAGE_NAME', value: "xlpay-pay-user"),
																		string(name: 'DEPLOY_TARGET', value: env.TARGET_ENV_ADDRESS)]
					    build job: 'load_deploy_from_image', parameters: [string(name: 'IMAGE_NAME', value: "xlpay-pay-user"),
																		  string(name: 'PROJECT_NAME', value: env.PROJECT_NAME),
																		  booleanParam(name: 'REINIT', value: params.RESET_SERVICE),
																		  [$class: 'LabelParameterValue', name: 'WORK_NODE', label: env.TARGET_ENV_LABEL]]
					    build job: 'Restart_xlpay_pay_user', parameters: [string(name: 'PROJECT_NAME', value: env.PROJECT_NAME),
																			  [$class: 'hudson.model.PasswordParameterValue', name: 'MYSQL_PASSWORD', value: env.MYSQL_PASSWORD],
																			  booleanParam(name: 'REINIT', value: env.RESET_SERVICE),
																			  booleanParam(name: 'RESET_MYSQL', value: env.RESET_MYSQL),
																			  [$class: 'LabelParameterValue', name: 'WORK_NODE', label: env.TARGET_ENV_LABEL]]
						cleanWs()
					}				
				} else {
					task '保留xlpay_admin'
				}

            },Start_pay_trustlink_data: {
				if (env.pay_trustlink_data == 'true') {
					task '启动 pay_trustlink_data'
					ws {
						build job: 'deploy_private_image', parameters: [string(name: 'IMAGE_NAME', value: "pay-trustlink-data:ci-" + env.BRANCH_FOR_TRUSTLINK_DATA),
																		string(name: 'TARGET_IMAGE_NAME', value: "pay-trustlink-data"),
																		string(name: 'DEPLOY_TARGET', value: env.TARGET_ENV_ADDRESS)]
					    build job: 'load_deploy_from_image', parameters: [string(name: 'IMAGE_NAME', value: "pay-trustlink-data"),
																		  string(name: 'PROJECT_NAME', value: env.PROJECT_NAME),
																		  booleanParam(name: 'REINIT', value: params.RESET_SERVICE),
																		  [$class: 'LabelParameterValue', name: 'WORK_NODE', label: env.TARGET_ENV_LABEL]]
					    build job: 'Restart_trustlink_data', parameters: [string(name: 'PROJECT_NAME', value: env.PROJECT_NAME),
					                                                        string(name: 'BINLOG_IP', value: env.BINLOG_IP),
																			[$class: 'hudson.model.PasswordParameterValue', name: 'MYSQL_PASSWORD', value: env.MYSQL_PASSWORD],
																			[$class: 'hudson.model.PasswordParameterValue', name: 'BINLOG_PASSWORD', value: env.BINLOG_PASSWORD],
																			booleanParam(name: 'REINIT', value: env.RESET_SERVICE),
																			booleanParam(name: 'RESET_MYSQL', value: env.RESET_MYSQL),
																			[$class: 'LabelParameterValue', name: 'WORK_NODE', label: env.TARGET_ENV_LABEL]]
						cleanWs()
					}	
				} else {
					task '保留trustlink'
				}
            },Start_Ec_Store: {
				if (env.xstor == 'true') {
					task '启动 zxl-evidence-center'			
					ws {

						build job: 'deploy_private_image', parameters: [string(name: 'IMAGE_NAME', value: "ec-store:ci-sffy"),
																		string(name: 'TARGET_IMAGE_NAME', value: "ec-store"),
																		booleanParam(name: 'ONLY_COPY_JAR', value: params.ONLY_COPY_JAR),
																		string(name: 'DEPLOY_TARGET', value: env.TARGET_ENV_ADDRESS)]
						build job: 'load_deploy_from_image', parameters: [string(name: 'IMAGE_NAME', value: "ec-store"),
																		  string(name: 'PROJECT_NAME', value: env.PROJECT_NAME),
																		  booleanParam(name: 'REINIT', value: params.RESET_SERVICE),
																		  [$class: 'LabelParameterValue', name: 'WORK_NODE', label: env.TARGET_ENV_LABEL]]
						build job: 'Restart_Ec_Store', parameters: [string(name: 'PROJECT_NAME', value: env.PROJECT_NAME),
																		   string(name: 'MYSQL_PASS', value: env.MYSQL_PASSWORD),
																		   booleanParam(name: 'REINIT', value: env.RESET_SERVICE),
																		   booleanParam(name: 'RESET_MYSQL', value: env.RESET_MYSQL),
																		   [$class: 'LabelParameterValue', name: 'WORK_NODE', label: env.TARGET_ENV_LABEL]]
						cleanWs()
					}				
				} else {
					task '保留xstor'
				}

            },Start_pay_web: {
				if (env.pay_web == 'true') {
					task '启动 pay_web'
					ws {
						build job: 'deploy_npm_dist', parameters: [string(name: 'NPM_DIST_NAME', value: "pay-web"),
																   string(name: 'GIT_BRANCH', value: env.BRANCH_FOR_WEB),
																   string(name: 'DEPLOY_PATH', value: "/opt/dist/xyf"),
																   string(name: 'REMOTE_DIST_NAME', value: "xyf"),
																   string(name: 'DEPLOY_TARGET', value: env.TARGET_ENV_ADDRESS)]
						build job: 'reload_npm_dist', parameters: [string(name: 'NPM_DIST_NAME', value: "xyf"),
																   string(name: 'PROJECT_NAME', value: env.PROJECT_NAME),
																   [$class: 'LabelParameterValue', name: 'WORK_NODE', label: env.TARGET_ENV_LABEL]]
						cleanWs()
					}				
				} else {
					task '保留pay_web'
				}
            }

            ws {
                task 'Remove Docker Without Tag'
                    build job: 'remove_docker_without_tag', parameters: [[$class: 'LabelParameterValue', name: 'WORK_NODE', label: env.TARGET_ENV_LABEL]]
                    cleanWs()
            }

            cleanWs()
        } //stage('启动')

    }

echo "Done"
}
