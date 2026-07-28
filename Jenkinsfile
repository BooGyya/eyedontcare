pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timestamps()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Validate') {
            steps {
                sh '''
                    test -f frontend/Dockerfile.prod
                    test -f frontend/nginx.conf
                    test -f backend/Dockerfile.prod
                    test -f compose.prod.yml
                '''
            }
        }

        stage('Build and Deploy') {
            steps {
                withCredentials([
                    file(
                        credentialsId: 's15p11b102-prod-env',
                        variable: 'PROD_ENV_FILE'
                    )
                ]) {
                    sh '''
                        docker compose \
                          --env-file "$PROD_ENV_FILE" \
                          -f compose.prod.yml \
                          -p s15p11b102 \
                          config >/dev/null

                        docker compose \
                          --env-file "$PROD_ENV_FILE" \
                          -f compose.prod.yml \
                          -p s15p11b102 \
                          up -d --build --remove-orphans
                    '''
                }
            }
        }

        stage('Status') {
            steps {
                withCredentials([
                    file(
                        credentialsId: 's15p11b102-prod-env',
                        variable: 'PROD_ENV_FILE'
                    )
                ]) {
                    sh '''
                        docker compose \
                          --env-file "$PROD_ENV_FILE" \
                          -f compose.prod.yml \
                          -p s15p11b102 \
                          ps
                    '''
                }
            }
        }
    }

    post {
        success {
            echo 'Deployment succeeded'
        }
        failure {
            echo 'Deployment failed. Check the console log.'
        }
    }
}
