// generated by generatebuilscript.sh
pipeline {
   agent  { label 'ubuntu' }
   options {
      buildDiscarder(logRotator(numToKeepStr: '1'))
      disableConcurrentBuilds() 
   }
   triggers {
      pollSCM('H/5 * * * * ')
   }
   environment {
     buildnumber = 201902131200
   }
   tools {
      maven 'Maven 3.3.9'
      jdk 'JDK 1.8 (latest)'
   }
   stages {
      stage('Informations') {
          steps {
              slackSend (channel:'#netbeans-builds', message:"STARTED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' ($env.BUILD_URL), Branch we are building is : 275dea5557510c107cf9d193fe61555aacd544b1",color:'#f0f0f0')
          }
      }
      stage('mavenutils preparation') {
          // this stage is temporary
          steps {
              echo 'Get Mavenutils sources'
              sh 'rm -rf mavenutils'
              checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', noTags: true, reference: '', shallow: true], [$class: 'MessageExclusion', excludedMessage: 'Automated site publishing.*'], [$class: 'RelativeTargetDirectory', relativeTargetDir: 'mavenutils']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/apache/incubator-netbeans-mavenutils/']]])
              script {
                 def mvnfoldersforsite  = ['parent','nbm-shared','nb-repository-plugin']
                 for (String mvnproject in mvnfoldersforsite) {
                     dir('mavenutils/'+mvnproject) {
                        sh "mvn clean install -Dmaven.repo.local=${env.WORKSPACE}/.repository"
                     }
                 }
              }
          }
      }
      stage('SCM operation') {
          steps {
              echo 'Get NetBeans sources'
              checkout poll:false, scm:[$class: 'GitSCM', branches: [[name: '275dea5557510c107cf9d193fe61555aacd544b1']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', noTags: false, reference: '', shallow: true], [$class: 'RelativeTargetDirectory', relativeTargetDir: 'netbeanssources']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/apache/incubator-netbeans/']]]
          }
      }
      stage('NetBeans Builds') {
          steps {
              dir ('netbeanssources'){
                  withAnt(installation: 'Ant (latest)') {
                      sh "ant -Dbuildnumber=${env.buildnumber}"
                      sh "ant build-javadoc -Dbuildnumber=${env.buildnumber}"
                      sh "ant build-source-zips -Dbuildnumber=${env.buildnumber}"
                      sh "ant build-nbms -Dbuildnumber=${env.buildnumber}"
                  }
              }
              script {
                        sh 'rm -rf testrepo/.m2'
                        sh "mvn org.apache.netbeans.utilities:nb-repository-plugin:1.4-SNAPSHOT:download -DnexusIndexDirectory=${env.WORKSPACE}/repoindex -Dmaven.repo.local=${env.WORKSPACE}/.repository -DrepositoryUrl=https://repo.maven.apache.org/maven2"
                        sh 'mkdir -p testrepo/.m2'
                        sh "mvn org.apache.netbeans.utilities:nb-repository-plugin:1.4-SNAPSHOT:populate -DnexusIndexDirectory=${env.WORKSPACE}/repoindex -DnetbeansNbmDirectory=${env.WORKSPACE}/netbeanssources/nbbuild/nbms -DnetbeansInstallDirectory=${env.WORKSPACE}/netbeanssources/nbbuild/netbeans -DnetbeansSourcesDirectory=${env.WORKSPACE}/netbeanssources/nbbuild/build/source-zips -DnebeansJavadocDirectory=${env.WORKSPACE}/netbeanssources/nbbuild/build/javadoc  -Dmaven.repo.local=${env.WORKSPACE}/.repository -DparentGAV=org.apache.netbeans:netbeans-parent:1 -DforcedVersion=RELEASE110 -DskipInstall=true -DdeployUrl=file://${env.WORKSPACE}/testrepo/.m2"
              }
              archiveArtifacts 'testrepo/.m2/**'
          }
      }
   }
   post {
     cleanup  {
         cleanWs()  
     }
     success {
       slackSend (channel:'#netbeans-builds', message:"SUCCESS: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL}) ",color:'#00FF00')
     }
     failure {
       slackSend (channel:'#netbeans-builds', message:"FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'  (${env.BUILD_URL})",color:'#FF0000')
     }
   }
}
