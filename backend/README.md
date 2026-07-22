# Backend

Spring Boot와 PostgreSQL을 사용하는 백엔드 애플리케이션입니다. 개발 환경과 운영 환경은 Spring Profile, Dockerfile, Docker Compose 파일로 분리되어 있습니다.

## 기술 구성

| 항목 | 버전 또는 구성 |
| --- | --- |
| Java | 25 |
| Spring Boot | 4.1.0 |
| Gradle | 9.5.1 |
| PostgreSQL | 15 |
| 컨테이너 | Docker, Docker Compose |

## 사전 요구사항

Docker Compose로 전체 환경을 실행하려면 다음 도구가 필요합니다.

- Docker Desktop 또는 Docker Engine
- Docker Compose

호스트에서 Gradle로 Spring Boot를 직접 실행하려면 Java 25도 필요합니다. 실행 전에 Docker daemon이 켜져 있는지 확인하세요.

## 개발 환경 빠른 시작

모든 명령은 저장소의 `backend/` 디렉터리에서 실행합니다.

```bash
cd backend
```

저장소 루트에서 `docker compose -f compose.yml ...`을 실행하면 `compose.yml`을 찾지 못합니다.

### 1. 로컬 환경변수 파일 생성

Git Bash, macOS, Linux:

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

`.env`는 Git에서 제외됩니다. `.env.example`에는 로컬 개발용 예시값만 있으며, 운영 환경에서 예시 비밀번호를 재사용하면 안 됩니다.

### 2. Spring Boot와 PostgreSQL 실행

```bash
docker compose -f compose.yml up --build -d
```

최초 실행 시 Java 이미지, PostgreSQL 15 이미지, Gradle 의존성을 내려받기 때문에 시간이 걸릴 수 있습니다.

### 3. 실행 상태 확인

```bash
docker compose -f compose.yml ps
```

정상 상태에서는 다음 서비스를 확인할 수 있습니다.

- `app`: Spring Boot, 기본 포트 `8080`
- `postgres`: PostgreSQL 15, 기본 포트 `5432`, 상태 `healthy`

Spring Boot 로그 확인:

```bash
docker compose -f compose.yml logs -f app
```

PostgreSQL 로그 확인:

```bash
docker compose -f compose.yml logs -f postgres
```

`Ctrl+C`는 로그 출력을 종료할 뿐 컨테이너를 중지하지 않습니다.

기본 접속 주소:

- Spring Boot: `http://localhost:8080`
- PostgreSQL: `localhost:5432`

현재 프로젝트에는 API Controller가 없으므로 브라우저에서 루트 주소에 접속했을 때 `404`가 반환될 수 있습니다. 컨테이너 상태와 `Started BackendApplication` 로그로 기동 여부를 확인하세요.

## 개발 환경 제어

중지된 컨테이너 시작:

```bash
docker compose -f compose.yml start
```

애플리케이션만 재시작:

```bash
docker compose -f compose.yml restart app
```

이미지를 다시 빌드하여 실행:

```bash
docker compose -f compose.yml up --build -d
```

컨테이너를 제거하지 않고 중지:

```bash
docker compose -f compose.yml stop
```

컨테이너와 Compose 네트워크를 제거하고 종료:

```bash
docker compose -f compose.yml down
```

`down`은 named volume과 Docker 이미지를 삭제하지 않으므로 PostgreSQL 데이터는 유지됩니다.

> 주의: 다음 명령은 PostgreSQL named volume과 모든 로컬 데이터를 삭제합니다. 데이터 초기화가 필요한 경우에만 사용하세요.

```bash
docker compose -f compose.yml down -v
```

## 호스트에서 Spring Boot 실행

IDE 또는 호스트의 Gradle을 사용하려면 PostgreSQL만 Docker로 실행합니다.

```bash
docker compose -f compose.yml up -d postgres
```

Git Bash, macOS, Linux:

```bash
./gradlew bootRun
```

Windows PowerShell 또는 명령 프롬프트:

```powershell
.\gradlew.bat bootRun
```

기본 `dev` 프로필은 다음 값으로 `localhost:5432`에 접속합니다.

```text
database: backend
username: backend
password: backend-local-password
```

`.env`의 데이터베이스 값을 변경했다면 호스트에서 실행하는 Spring Boot에도 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`를 동일하게 설정해야 합니다. `.env`는 Docker Compose가 읽는 파일이며 Gradle이 자동으로 읽지는 않습니다.

## 테스트 실행

현재 context test는 PostgreSQL 연결이 필요합니다. 먼저 개발용 PostgreSQL을 실행하세요.

```bash
docker compose -f compose.yml up -d postgres
```

Git Bash, macOS, Linux:

```bash
./gradlew test
```

Windows PowerShell 또는 명령 프롬프트:

```powershell
.\gradlew.bat test
```

## Spring Profile 구성

| 프로필 | 데이터베이스 연결 | JPA 스키마 처리 | SQL 출력 | 용도 |
| --- | --- | --- | --- | --- |
| `dev` | 로컬 기본값 또는 환경변수 | `update` | 활성화 | 로컬 개발 |
| `prod` | 필수 환경변수 | `validate` | 비활성화 | 배포 |

공통 설정은 `application.yml`, 개발 설정은 `application-dev.yml`, 운영 설정은 `application-prod.yml`에 있습니다. 프로필을 지정하지 않으면 `dev`가 기본값입니다.

`ddl-auto: update`는 초기 개발 편의를 위한 설정입니다. 운영 스키마 변경 전에는 Flyway 같은 데이터베이스 마이그레이션 도구를 도입하는 것을 권장합니다.

## 환경변수

| 환경변수 | 개발 기본값 | 운영 환경 | 용도 |
| --- | --- | --- | --- |
| `POSTGRES_DB` | `backend` | 필수 | 데이터베이스 이름 |
| `POSTGRES_USER` | `backend` | 필수 | 데이터베이스 사용자 |
| `POSTGRES_PASSWORD` | 로컬 예시값 | 필수 Secret | 데이터베이스 비밀번호 |
| `POSTGRES_PORT` | `5432` | 호스트에 공개하지 않음 | 개발용 PostgreSQL 포트 |
| `APP_PORT` | `8080` | 선택, 기본값 `8080` | Spring Boot 호스트 포트 |

Spring Boot 컨테이너에는 Compose가 다음 환경변수를 자동으로 전달합니다.

- `SPRING_PROFILES_ACTIVE`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SERVER_PORT`

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

| 파일 | 역할 |
| --- | --- |
| `application.yml` | 애플리케이션 이름, 기본 프로필, 서버 포트 등 공통 설정 |
| `application-dev.yml` | 개발용 PostgreSQL과 JPA 설정 |
| `application-prod.yml` | 환경변수 기반 운영용 PostgreSQL과 JPA 설정 |
| `Dockerfile.dev` | Java 25 JDK와 Gradle `bootRun`을 사용하는 개발 이미지 |
| `Dockerfile.prod` | 멀티 스테이지 빌드와 JRE/non-root 사용자를 적용한 운영 이미지 |
| `compose.yml` | Spring Boot와 PostgreSQL 15 개발 환경 |
| `compose.prod.yml` | Spring Boot와 PostgreSQL 15 운영 환경 |
| `.env.example` | 로컬 환경변수 템플릿 |
| `.gitignore` | `.env`, Secret, 빌드·IDE 파일 제외 |
| `.dockerignore` | Docker 빌드 컨텍스트에서 불필요하거나 민감한 파일 제외 |

## 문제 해결

### `compose.yml`을 찾을 수 없음

다음과 같은 오류는 저장소 루트에서 명령을 실행했을 때 발생합니다.

```text
open .../compose.yml: The system cannot find the file specified.
```

`backend/`로 이동한 뒤 다시 실행하세요.

```bash
cd backend
docker compose -f compose.yml ps
```

### Docker daemon에 연결할 수 없음

Docker Desktop 또는 Docker Engine을 실행한 뒤 확인하세요.

```bash
docker info
```

### `8080` 또는 `5432` 포트를 이미 사용 중

`.env`에서 호스트 포트를 변경할 수 있습니다.

```dotenv
APP_PORT=8081
POSTGRES_PORT=5433
```

컨테이너 내부 포트는 변경되지 않습니다.

### `.env`의 PostgreSQL 계정 정보를 바꿨지만 적용되지 않음

PostgreSQL의 초기 계정과 데이터베이스는 named volume이 처음 생성될 때만 초기화됩니다. 기존 볼륨이 있으면 `.env`를 변경해도 이미 생성된 계정은 바뀌지 않습니다.

> 주의: 다음 초기화 명령은 모든 로컬 데이터베이스 데이터를 삭제합니다.

```bash
docker compose -f compose.yml down -v
docker compose -f compose.yml up --build -d
```

데이터가 필요하면 볼륨을 삭제하지 말고 PostgreSQL에서 사용자와 비밀번호를 직접 변경하세요.

### Spring Boot가 PostgreSQL에 연결하지 못함

PostgreSQL 상태와 로그를 확인하세요.

```bash
docker compose -f compose.yml ps
docker compose -f compose.yml logs postgres
```

`postgres`가 `healthy` 상태가 된 뒤 애플리케이션 로그를 확인합니다.

```bash
docker compose -f compose.yml logs app
```

### 코드 변경 후 애플리케이션이 재시작되지 않음

Spring Boot DevTools는 컴파일된 클래스 변경을 감지합니다. IDE 빌드 또는 Gradle 컴파일이 수행되어야 재시작됩니다. `build.gradle`이나 의존성을 변경했다면 개발 이미지를 다시 빌드하세요.

```bash
docker compose -f compose.yml up --build -d app
```

## 후속 권장사항

1. Flyway를 도입해 스키마 변경 이력을 관리합니다.
2. Spring Boot Actuator와 애플리케이션 healthcheck를 추가합니다.
3. Testcontainers로 PostgreSQL 통합 테스트를 자동화합니다.
4. 운영 Secret Manager와 데이터베이스 백업·복구 정책을 적용합니다.
5. CI에서 Gradle 테스트, Docker 빌드, Compose 검증, 이미지 취약점 검사를 실행합니다.
