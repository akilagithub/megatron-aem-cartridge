// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"
def projectNameKey = projectFolderName.toLowerCase().replace("/", "-");

// Jobs reference
def generateMegatronAEMJobs = freeStyleJob(projectFolderName + "/Generate_Megatron_AEM_Jobs")

generateMegatronAEMJobs.with {
    parameters {
        stringParam("GIT_REPOSITORY_URL", "ssh://git@innersource.accenture.com/a2632/megatron-aem.git", "Git Repository URL to build the project from.")
        stringParam("GIT_REPOSITORY_BRANCH", "dev", "Branch to build the project from.")
        stringParam("DOCKER_REGISTRY_USERNAME", "devops.training", "Docker registry username. If no username is provided, Jenkins jobs will not use authentification when conencting to registry")
        stringParam("DOCKER_REGISTRY_URL", "docker.accenture.com", "Docker registry URL where the built images will be stored")
        stringParam("DOCKER_REGISTRY_EMAIL", "devops.training@accenture.com", "Docker registry e-mail address")
        stringParam("DOCKER_REGISTRY_REPO", "megatron", "Docker registry repository, where you wish to push your images")
        stringParam("AEM_PRODUCT_NAME", "", "Product name from AEM license.properties file")
        stringParam("AEM_CUSTOMER_NAME", "", "Customer name from AEM license.properties file")
        stringParam("AEM_PRODUCT_VERSION", "", "Product version number from AEM license.properties file")
        stringParam("CONTENT_ADMIN_USER", "", "Username for AEM content deployment on an external server")
        stringParam("CONTENT_ADMIN_PASS", "", "Password for AEM content deployment on an external server")
        configure { project ->
            project / 'properties' / 'hudson.model.ParametersDefinitionProperty'/ 'parameterDefinitions' << 'hudson.model.PasswordParameterDefinition' {
                name("DOCKER_REGISTRY_PASSWORD")
                description("Docker registry password")
                defaultValue('ztNsaJPyrSyrPdtn')
            }
        }
        configure { project ->
            project / 'properties' / 'hudson.model.ParametersDefinitionProperty'/ 'parameterDefinitions' << 'hudson.model.PasswordParameterDefinition' {
                name("AEM_DOWNLOAD_ID")
                description("Download ID from AEM license.properties file")
                defaultValue('')
            }
        }
    }
    environmentVariables {
        env('WORKSPACE_NAME', workspaceFolderName)
        env('PROJECT_NAME', projectFolderName)
        env('PROJECT_NAME_KEY', projectNameKey)
        env('SCM_PROVIDER_ID',"${SCM_PROVIDER_ID}")
        env('SCM_NAMESPACE', "${SCM_NAMESPACE}")
        groovy("matcher = JENKINS_URL =~ /http:\\/\\/(.*?)\\/jenkins.*/; def map = [STACK_IP: matcher[0][1]]; return map;")
    }
    wrappers {
        preBuildCleanup()
        injectPasswords {
            injectGlobalPasswords()
        }
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    steps {
        shell('''set +x
                |set +e
                |GIT_REPOSITORY=$(git ls-remote --get-url ${GIT_REPOSITORY_URL} | sed -n 's#.*/\\([^.]*\\)\\.git#\\1#p')
                |git ls-remote ssh://jenkins@gerrit:29418/${PROJECT_NAME}/${GIT_REPOSITORY} 2> /dev/null
                |RESPONSE=$?
                |set -e
                |if [ ${RESPONSE} != 0 ]; then
                |    echo "Creating gerrit project : ${PROJECT_NAME}/${GIT_REPOSITORY} "
                |    ssh -p 29418 jenkins@gerrit gerrit create-project ${PROJECT_NAME}/${GIT_REPOSITORY} --empty-commit
                |    # Populate repository
                |    git clone ssh://jenkins@gerrit:29418/${PROJECT_NAME}/${GIT_REPOSITORY} .
                |    git remote add source "${GIT_REPOSITORY_URL}"
                |    git fetch source
                |    git push origin +refs/remotes/source/*:refs/heads/*
                |else
                |    echo "Repository ${PROJECT_NAME}/${GIT_REPOSITORY} exists! Creating jobs..."
                |fi
                |
                |echo "CONTENT_ADMIN_USER=$CONTENT_ADMIN_USER" > env.properties
                |echo "CONTENT_ADMIN_PASS=$CONTENT_ADMIN_PASS" > env.properties
                |echo "GIT_REPOSITORY_NAME=$GIT_REPOSITORY" > env.properties
                |echo "GIT_REPOSITORY_URL=$GIT_REPOSITORY_URL" >> env.properties
                |echo "GIT_REPOSITORY_BRANCH=$GIT_REPOSITORY_BRANCH" >> env.properties
                |
                '''.stripMargin())
        environmentVariables {
            propertiesFile('env.properties')
        }
        dsl {
            text(readFileFromWorkspace('cartridge/jenkins/jobs/dsl/reference_jobs.template'))
            additionalClasspath("${JENKINS_HOME}/userContent/job_dsl_additional_classpath/")
        }
    }
}
