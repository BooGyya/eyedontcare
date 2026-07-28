def sendMMNotify(boolean success, Map info = [:]) {
    try {
        def titleLine = success
            ? "## :white_check_mark: 서비스 배포 성공"
            : "## :x: 서비스 배포 실패"

        def lines = []

        if (info.mention) {
            lines << "**담당자**: ${info.mention}"
        }

        if (info.branch) {
            lines << "**대상 브랜치**: `${info.branch}`"
        }

        if (info.commit?.msg) {
            lines << "**커밋**: ${info.commit.msg}"
        }

        if (info.buildUrl) {
            lines << "**빌드 상세**: [Jenkins에서 확인](${info.buildUrl})"
        }

        if (!success && info.details) {
            lines << "**실패 정보**: ${info.details}"
        }

        def text = "${titleLine}\n" +
            (lines ? "\n" + lines.join("\n") : "")

        writeFile(
            file: 'mattermost-payload.json',
            text: groovy.json.JsonOutput.toJson([
                text      : text,
                username  : 'Jenkins',
                icon_emoji: ':robot_face:'
            ])
        )

        withCredentials([
            string(
                credentialsId: 'mattermost-webhook',
                variable: 'MM_WEBHOOK'
            )
        ]) {
            def notificationStatus = sh(
                script: '''
                    curl -sS -f \
                      -X POST \
                      -H 'Content-Type: application/json' \
                      --data-binary @mattermost-payload.json \
                      "$MM_WEBHOOK"
                ''',
                returnStatus: true
            )

            if (notificationStatus != 0) {
                echo 'Mattermost notification failed.'
            }
        }
    } catch (Exception error) {
        // 알림 실패 때문에 실제 배포 결과가 실패로 바뀌지 않도록 처리합니다.
        echo "Mattermost notification error: ${error.message}"
    }
}

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
            script {
                def commitMessage = sh(
                    script: 'git log -1 --pretty=%s',
                    returnStdout: true
                ).trim()

                sendMMNotify(true, [
                    branch  : env.BRANCH_NAME ?: env.GIT_BRANCH ?: 'unknown',
                    commit  : [
                        msg: commitMessage
                    ],
                    buildUrl: env.BUILD_URL
                ])
            }
        }

        failure {
            script {
                sendMMNotify(false, [
                    branch  : env.BRANCH_NAME ?: env.GIT_BRANCH ?: 'unknown',
                    buildUrl: env.BUILD_URL,
                    details : 'Jenkins 콘솔 로그를 확인하세요.'
                ])
            }
        }

        cleanup {
            sh 'rm -f mattermost-payload.json'
        }
    }
}
