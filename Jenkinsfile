pipeline {
    agent { node 'ubuntu' }

    stages {
        stage('Build') {
            steps {
                sh 'mvn -B -DskipTests clean package -POWB2'
            }
        }
        stage('Test'){
            steps {
                sh 'mvn -B verify -POWB2'
                junit 'reports/**/*.xml'
            }
        }
        stage('Test - Weld') {
            steps {
                sh 'mvn -B verify -PWeld3'
                junit 'reports/**/*.xml'
            }
        }
        stage('Deploy') {
            when {
              expression {
                currentBuild.result == null || currentBuild.result == 'SUCCESS'
              }
            }
            steps {
                sh 'mvn -B source:jar deploy'
            }
        }
    }
    post {
        always {
            deleteDir()
        }
        unstable {
            mail to: 'scm@geronimo.apache.org',
                         subject: "Unstable Pipeline: ${currentBuild.fullDisplayName}",
                         body: "Build failure: ${env.BUILD_URL}"
        }
        failure {
            mail to: 'scm@geronimo.apache.org',
                         subject: "Failed Pipeline: ${currentBuild.fullDisplayName}",
                         body: "Build failure: ${env.BUILD_URL}"
        }
    }
}