# Backend

Spring Boot와 PostgreSQL을 사용하는 백엔드 애플리케이션입니다. 개발 환경과 운영 환경은 Spring Profile, Dockerfile, Docker Compose 파일로 분리되어 있습니다.

## 기술 구성

| 항목        | 버전 또는 구성         |
| ----------- | ---------------------- |
| Java        | 25                     |
| Spring Boot | 4.1.0                  |
| Gradle      | 9.5.1                  |
| PostgreSQL  | 15                     |
| 컨테이너    | Docker, Docker Compose |

## 개발 참고 API

- [Backend API 개발 가이드](../docs/backend-api-development-guide.md)
- [Ping과 공통 응답·예외 처리 예제](../docs/backend-ping-guide.md)
- [Backend 공통 설정 가이드](../docs/backend-common-settings.md)

## 사전 요구사항

Docker Compose로 전체 환경을 실행하려면 다음 도구가 필요합니다.

- Docker Desktop 또는 Docker Engine
- Docker Compose

호스트에서 Gradle로 Spring Boot를 직접 실행하려면 Java 25도 필요합니다. 실행 전에 Docker daemon이 켜져 있는지 확인하세요.

## 개발 환경 빠른 시작

전체 로컬 서비스는 저장소 루트의 `compose.dev.yml`로 실행합니다. 모든 명령은 저장소 루트에서 실행합니다.

### 1. 루트 환경변수 파일 생성

Git Bash, macOS, Linux:

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

루트 `.env`는 Git에서 제외됩니다. `JWT_SECRET_KEY`에는 디코딩했을 때 32바이트 이상인 Base64 값을 설정합니다. 실제 Secret은 `.env.example`에 작성하지 않습니다.

### 2. 전체 서비스 실행

```powershell
docker compose -f compose.dev.yml up --build
```

최초 실행 시 Java, Node.js, PostgreSQL, Redis 이미지와 의존성을 내려받기 때문에 시간이 걸릴 수 있습니다.

### 3. 상태와 로그 확인

```powershell
docker compose -f compose.dev.yml ps
docker compose -f compose.dev.yml logs -f backend
```

정상 상태에서는 다음 서비스를 확인할 수 있습니다.

- `frontend`: Vite, 브라우저 접속 `http://localhost:3102`
- `backend`: Spring Boot, 직접 확인 `http://localhost:8102`
- `postgres`: PostgreSQL 15, 내부 주소 `postgres:5432`
- `redis`: Redis 7, 내부 주소 `redis:6379`

브라우저의 `/api`와 `/ws` 요청은 Vite가 `backend:8080`으로 프록시합니다. `backend:8080`은 Compose 네트워크 내부 주소이므로 브라우저 코드에서 직접 사용하지 않습니다.

## 개발 환경 제어

모든 명령은 저장소 루트에서 실행합니다.

```powershell
docker compose -f compose.dev.yml start
docker compose -f compose.dev.yml restart backend
docker compose -f compose.dev.yml up --build -d
docker compose -f compose.dev.yml stop
docker compose -f compose.dev.yml down
```

`down`은 named volume을 삭제하지 않으므로 PostgreSQL 데이터는 유지됩니다. `down -v`는 로컬 데이터베이스 데이터를 삭제하므로 데이터 초기화가 명확히 필요한 경우에만 사용합니다.

## 호스트에서 Spring Boot 실행

IDE 또는 호스트의 Gradle을 사용하려면 저장소 루트에서 PostgreSQL과 Redis만 실행합니다.

```powershell
docker compose -f compose.dev.yml up -d postgres redis
Set-Location backend
.\gradlew.bat bootRun
```

Git Bash, macOS, Linux에서는 `./gradlew bootRun`을 사용합니다. 호스트에서 실행하는 Spring Boot는 Docker Compose의 루트 `.env`를 자동으로 읽지 않으므로 데이터베이스, Redis, JWT, Kakao 환경변수가 필요하면 IDE 실행 설정이나 셸에 별도로 주입합니다.

## 테스트 실행

백엔드 전체 테스트는 PostgreSQL과 Redis 연결이 필요합니다.

```powershell
docker compose -f compose.dev.yml up -d postgres redis
Set-Location backend
.\gradlew.bat test
```

Git Bash, macOS, Linux에서는 `./gradlew test`를 사용합니다.

## Spring Profile 구성

| 프로필 | 데이터베이스 연결         | JPA 스키마 처리 | SQL 출력 | 용도      |
| ------ | ------------------------- | --------------- | -------- | --------- |
| `dev`  | 로컬 기본값 또는 환경변수 | `update`        | 활성화   | 로컬 개발 |
| `prod` | 필수 환경변수             | `validate`      | 비활성화 | 배포      |

공통 설정은 `application.yml`, 개발 설정은 `application-dev.yml`, 운영 설정은 `application-prod.yml`에 있습니다. 프로필을 지정하지 않으면 `dev`가 기본값입니다.

`ddl-auto: update`는 초기 개발 편의를 위한 설정입니다. 운영 스키마 변경 전에는 Flyway 같은 데이터베이스 마이그레이션 도구를 도입하는 것을 권장합니다.

## 환경변수

로컬 전체 스택의 환경변수 기준 파일은 저장소 루트의 `.env`입니다.

| 환경변수                   | 개발 기본값                                 | 용도                               |
| -------------------------- | ------------------------------------------- | ---------------------------------- |
| `FRONTEND_HOST_PORT`       | `3102`                                      | Vite 호스트 포트                   |
| `BACKEND_HOST_PORT`        | `8102`                                      | Spring Boot 호스트 포트            |
| `POSTGRES_DB`              | `backend`                                   | 데이터베이스 이름                  |
| `POSTGRES_USER`            | `backend`                                   | 데이터베이스 사용자                |
| `POSTGRES_PASSWORD`        | 로컬 예시값                                 | 데이터베이스 비밀번호              |
| `POSTGRES_PORT`            | `5432`                                      | PostgreSQL 호스트 포트             |
| `REDIS_PORT`               | `6379`                                      | Redis 호스트 포트                  |
| `JWT_SECRET_KEY`           | 필수                                        | Base64 인코딩된 JWT HMAC 키        |
| `KAKAO_CLIENT_ID`          | 로컬 대체값                                 | Kakao REST API 키                  |
| `KAKAO_CLIENT_SECRET`      | 로컬 대체값                                 | Kakao client secret                |
| `KAKAO_REDIRECT_URI`       | `http://localhost:3102/auth/kakao/callback` | Kakao callback 주소                |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3102`                     | HTTP 및 WebSocket origin 허용 목록 |

Compose는 백엔드 컨테이너에 Spring datasource, Redis, JWT, Kakao, CORS 설정을 명시적으로 전달합니다. 루트 `.env` 전체를 컨테이너에 주입하지 않으며 프런트엔드에는 `VITE_API_BASE_URL`과 내부 프록시 대상만 전달합니다.

## 운영 환경 실행

운영 환경에서는 배포 플랫폼의 Secret Manager 사용을 권장합니다. 단일 서버에서 Compose로 실행하는 경우 Git에서 제외되는 별도 환경 파일을 사용할 수 있습니다.

Git Bash, macOS, Linux:

```bash
cp .env.example .env.prod
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env.prod
```

> 실행 전에 `.env.prod`의 `POSTGRES_PASSWORD`를 충분히 강한 운영용 값으로 반드시 변경하세요. `.env.example`의 비밀번호를 운영에서 사용하면 안 됩니다.

운영 환경 실행:

```bash
docker compose --env-file .env.prod -f compose.prod.yml up --build -d
```

상태 확인:

```bash
docker compose --env-file .env.prod -f compose.prod.yml ps
```

로그 확인:

```bash
docker compose --env-file .env.prod -f compose.prod.yml logs -f app
```

운영 환경 종료:

```bash
docker compose --env-file .env.prod -f compose.prod.yml down
```

운영 구성의 특징:

- `prod` Spring Profile 사용
- 멀티 스테이지 Docker 빌드
- Spring Boot를 `spring:spring` non-root 사용자로 실행
- PostgreSQL 포트를 호스트에 공개하지 않음
- 필수 데이터베이스 환경변수 누락 시 Compose 실행 실패
- PostgreSQL과 Spring Boot에 `restart: unless-stopped` 적용

## 설정 파일

| 파일                   | 역할                                                          |
| ---------------------- | ------------------------------------------------------------- |
| `application.yml`      | 애플리케이션 이름, 기본 프로필, 서버 포트 등 공통 설정        |
| `application-dev.yml`  | 개발용 PostgreSQL과 JPA 설정                                  |
| `application-prod.yml` | 환경변수 기반 운영용 PostgreSQL과 JPA 설정                    |
| `Dockerfile.dev`       | Java 25 JDK와 Gradle `bootRun`을 사용하는 개발 이미지         |
| `Dockerfile.prod`      | 멀티 스테이지 빌드와 JRE/non-root 사용자를 적용한 운영 이미지 |
| `../compose.dev.yml`   | 프런트엔드, 백엔드, PostgreSQL, Redis 통합 개발 환경          |
| `compose.prod.yml`     | Spring Boot와 PostgreSQL 15 운영 환경                         |
| `../.env.example`      | 통합 로컬 환경변수 템플릿                                     |
| `.gitignore`           | `.env`, Secret, 빌드·IDE 파일 제외                            |
| `.dockerignore`        | Docker 빌드 컨텍스트에서 불필요하거나 민감한 파일 제외        |

## 문제 해결

### `compose.dev.yml`을 찾을 수 없음

통합 Compose 명령은 저장소 루트에서 실행합니다.

```powershell
Set-Location ..
docker compose -f compose.dev.yml ps
```

### Docker daemon에 연결할 수 없음

Docker Desktop 또는 Docker Engine을 실행한 뒤 확인합니다.

```powershell
docker info
```

### 호스트 포트를 이미 사용 중

루트 `.env`에서 호스트 포트만 변경합니다.

```dotenv
FRONTEND_HOST_PORT=3103
BACKEND_HOST_PORT=8103
POSTGRES_PORT=5433
REDIS_PORT=6380
```

컨테이너 내부 주소인 `frontend:5173`, `backend:8080`, `postgres:5432`, `redis:6379`는 변경하지 않습니다.

### PostgreSQL 계정 변경이 적용되지 않음

PostgreSQL 초기 계정은 named volume이 처음 생성될 때만 적용됩니다. 기존 데이터가 필요하면 볼륨을 삭제하지 말고 PostgreSQL에서 계정을 직접 변경합니다.

### 백엔드가 PostgreSQL 또는 Redis에 연결하지 못함

```powershell
docker compose -f compose.dev.yml ps
docker compose -f compose.dev.yml logs postgres redis backend
```

PostgreSQL과 Redis가 `healthy` 상태인지 먼저 확인합니다.

### 코드 변경이 컨테이너에 반영되지 않음

기본 `up` 명령은 이미지를 빌드한 시점의 코드를 사용합니다. Compose watch를 사용하려면 저장소 루트에서 다음 명령을 실행합니다.

```powershell
docker compose -f compose.dev.yml up --build --watch
```

의존성 파일을 변경한 경우 해당 이미지가 다시 빌드됩니다.

## 후속 권장사항

1. Flyway를 도입해 스키마 변경 이력을 관리합니다.
2. Spring Boot Actuator와 애플리케이션 healthcheck를 추가합니다.
3. Testcontainers로 PostgreSQL 통합 테스트를 자동화합니다.
4. 운영 Secret Manager와 데이터베이스 백업·복구 정책을 적용합니다.
5. CI에서 Gradle 테스트, Docker 빌드, Compose 검증, 이미지 취약점 검사를 실행합니다.
