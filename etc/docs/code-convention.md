# Code Convention

이 문서는 프로젝트에서 사용할 코드 컨벤션을 정의합니다.

- 기본 목표는 팀원이 코드를 빠르게 읽고, 예측 가능한 방식으로 수정할 수 있게 만드는 것입니다.
- 언어와 프레임워크별 세부 규칙은 각 프로젝트의 린터와 포매터 설정을 우선합니다.
- 이 문서는 Backend, Frontend, Data Pipeline, Infra Settings에 공통으로 적용할 기준을 다룹니다.

## Basic Rules

- 코드는 사람이 먼저 읽는 문서라는 기준으로 작성합니다.
- 하나의 함수, 클래스, 모듈은 하나의 책임을 중심으로 구성합니다.
- 중복 제거보다 의도 표현을 우선합니다.
- 일관성 있는 이름, 파일 구조, 예외 처리 방식을 사용합니다.
- 포맷팅은 개인 취향으로 수정하지 않고 프로젝트 도구에 맡깁니다.
- 큰 변경과 단순 포맷팅 변경은 같은 커밋에 섞지 않습니다.
- 공개 API, 공통 모듈, 설정 파일 변경은 PR 본문에 영향 범위를 작성합니다.

## Formatting

포맷팅은 자동화 도구를 기준으로 통일합니다.

권장 도구:

| Area | Tool |
| --- | --- |
| Java, Kotlin | Checkstyle, Spotless, ktlint |
| Python | Black, Ruff, isort |
| JavaScript, TypeScript | Prettier, ESLint |
| Vue | Prettier, ESLint, Vue ESLint rules |
| SQL | SQLFluff |
| Markdown | Prettier |
| YAML, JSON | Prettier |

규칙:

- 저장소에 포매터 설정 파일을 포함합니다.
- IDE 기본 포맷보다 프로젝트 포매터 설정을 우선합니다.
- 자동 포맷 결과를 수동으로 되돌리지 않습니다.
- 린트 경고를 무시해야 한다면 코드 근처에 이유를 짧게 남깁니다.

## Naming Rules

이름은 구현 방식보다 역할과 의도를 드러내도록 작성합니다.

### Common

- 축약어는 널리 쓰이는 경우에만 사용합니다.
- 불리언 값은 긍정문으로 작성합니다.
- 컬렉션은 복수형을 사용합니다.
- 임시 이름, 의미 없는 이름, 타입만 반복하는 이름은 피합니다.

예시:

```text
좋음: isActive, users, accessToken, paymentRequest
피함: flag, data, temp, obj, userListData
```

### Backend

- Controller 또는 Router는 요청과 응답 흐름만 담당합니다.
- Service는 비즈니스 규칙을 담당합니다.
- Repository 또는 DAO는 데이터 접근을 담당합니다.
- DTO, Schema, Request, Response 이름을 역할에 맞게 구분합니다.

예시:

```text
UserController
UserService
UserRepository
CreateUserRequest
UserResponse
```

### Frontend

- 컴포넌트 이름은 PascalCase를 사용합니다.
- 훅, composable, util 함수는 역할을 드러내는 동사 기반 이름을 사용합니다.
- 페이지 컴포넌트와 공용 컴포넌트를 구분합니다.
- 이벤트 핸들러는 `handle` 접두사를 사용합니다.

예시:

```text
UserProfileCard
useAuth
useChatStream
formatDate
handleSubmit
```

## File And Directory Rules

디렉터리는 기술보다 역할과 변경 이유를 기준으로 나눕니다.

권장 기준:

- 기능 단위로 응집도를 유지합니다.
- 공통 모듈은 무분별하게 커지지 않도록 사용 기준을 둡니다.
- 파일 하나가 너무 많은 책임을 가지면 역할별로 분리합니다.
- 테스트 파일은 대상 코드와 찾기 쉬운 위치에 둡니다.

예시:

```text
backend/
  src/
    auth/
    user/
    common/

frontend/
  src/
    pages/
    components/
    features/
    hooks/
    utils/

data/
  jobs/
  dags/
  connectors/
  common/
```

## Function Rules

- 함수는 한 가지 목적을 수행하도록 작성합니다.
- 함수명만 보고 결과나 부수효과를 예상할 수 있어야 합니다.
- 매개변수가 많아지면 객체나 DTO로 묶는 것을 고려합니다.
- 숨겨진 전역 상태에 의존하지 않습니다.
- 복잡한 조건문은 의도를 드러내는 함수로 분리합니다.

피해야 할 패턴:

- 한 함수에서 검증, 비즈니스 처리, DB 저장, 응답 생성을 모두 수행
- 이름과 다르게 외부 상태를 변경하는 함수
- 여러 의미를 가진 `boolean` 매개변수

## Class And Module Rules

- 클래스와 모듈은 변경 이유가 하나에 가깝도록 구성합니다.
- 공통 모듈은 실제로 두 번 이상 필요해졌을 때 분리합니다.
- 순환 의존성이 생기지 않도록 계층 방향을 유지합니다.
- 외부 라이브러리 의존성은 가능한 한 경계 모듈 안에 가둡니다.

계층 예시:

```text
Controller/Router -> Service -> Repository -> Database
Page -> Feature Component -> Shared Component -> Utility
Job/DAG -> Operator/Task -> Connector -> External System
```

## API Rules

API는 클라이언트가 예측 가능한 방식으로 사용할 수 있게 설계합니다.

규칙:

- HTTP method는 리소스 동작에 맞게 사용합니다.
- URL은 명사 중심으로 작성합니다.
- API 버전이 필요한 프로젝트는 `/api/v1`처럼 URL prefix로 버전을 표현합니다.
- 응답 구조는 프로젝트 전체에서 일관되게 유지합니다.
- 에러 응답은 공통 포맷을 사용합니다.
- 페이지네이션, 정렬, 필터 파라미터 이름을 통일합니다.

예시:

```text
GET    /api/v1/users
GET    /api/v1/users/{userId}
POST   /api/v1/users
PATCH  /api/v1/users/{userId}
DELETE /api/v1/users/{userId}
```

버전 규칙:

- 외부 클라이언트가 사용하는 API는 버전을 명시합니다.
- 내부 실험용 API나 초기 MVP에서는 버전 없이 시작할 수 있지만, 공개 API가 되면 버전 정책을 정합니다.
- breaking change가 발생하면 기존 버전을 유지하고 새 버전을 추가하는 방식을 우선 고려합니다.

## Error Handling

- 예외를 숨기지 않습니다.
- 사용자에게 보여줄 메시지와 로그에 남길 메시지를 구분합니다.
- 공통 에러 코드 또는 에러 타입을 사용합니다.
- 외부 시스템 오류는 원인을 추적할 수 있게 로깅합니다.
- 복구 가능한 오류와 복구 불가능한 오류를 구분합니다.

권장 응답 예시:

```json
{
  "code": "USER_NOT_FOUND",
  "message": "User not found"
}
```

## Logging Rules

- 로그는 문제를 재현하고 추적할 수 있는 정보를 남깁니다.
- 민감 정보는 로그에 남기지 않습니다.
- 정상 흐름과 오류 흐름의 로그 레벨을 구분합니다.
- 요청 ID, 사용자 ID, 작업 ID처럼 추적에 필요한 값을 포함합니다.
- 단순 디버깅용 로그는 PR 전에 제거합니다.

로그 레벨 기준:

| Level | Usage |
| --- | --- |
| `debug` | 개발 중 상세 흐름 확인 |
| `info` | 주요 비즈니스 이벤트 |
| `warn` | 처리 가능하지만 확인이 필요한 상황 |
| `error` | 실패한 요청, 작업, 외부 연동 |

## Configuration Rules

- 환경별 설정은 코드와 분리합니다.
- 민감 정보는 저장소에 커밋하지 않습니다.
- `.env.example` 또는 샘플 설정 파일을 제공합니다.
- 기본값은 로컬 개발자가 빠르게 실행할 수 있게 구성합니다.
- 운영 설정은 CI/CD 또는 배포 환경에서 주입합니다.

피해야 할 값:

```text
실제 DB 비밀번호
운영 API Key
개인 access token
Slack webhook URL
클라우드 secret key
```

## Database Rules

- 테이블, 컬렉션, 필드 이름은 일관된 스타일을 사용합니다.
- 마이그레이션 파일은 되돌릴 수 있는 형태로 작성합니다.
- 인덱스 추가는 조회 패턴과 함께 검토합니다.
- 대량 변경 쿼리는 운영 영향도를 확인합니다.
- 애플리케이션 코드와 DB 스키마 변경 순서를 고려합니다.

권장 기준:

```text
테이블: snake_case
컬럼: snake_case
인덱스: idx_<table>_<columns>
외래키: fk_<from_table>_<to_table>
```

## Frontend Rules

- UI 상태, 서버 상태, 폼 상태를 구분합니다.
- 컴포넌트는 표시 책임과 데이터 처리 책임을 분리합니다.
- 공용 컴포넌트는 도메인 로직을 갖지 않도록 합니다.
- 접근성 속성을 필요한 곳에 작성합니다.
- API 호출 로직은 컴포넌트에 직접 흩뿌리지 않습니다.

권장 분리:

```text
pages: 라우팅 단위 화면
features: 도메인 기능 단위 UI와 로직
components: 재사용 가능한 공용 UI
hooks/composables: 상태와 동작 재사용
utils: 순수 유틸리티 함수
```

## Test Rules

- 테스트 이름은 검증하려는 동작을 설명합니다.
- 정상 케이스와 실패 케이스를 함께 고려합니다.
- 외부 시스템은 필요에 따라 mock, stub, test container를 사용합니다.
- 리팩토링 PR은 기존 테스트가 통과해야 합니다.
- 버그 수정 PR은 가능하면 재현 테스트를 포함합니다.

테스트 범위:

| Type | Purpose |
| --- | --- |
| Unit Test | 함수, 클래스, 모듈 단위 검증 |
| Integration Test | DB, 외부 API, 모듈 간 연동 검증 |
| Scenario Test | 요구사항 기반 흐름 검증 |
| E2E Test | 실제 사용자 흐름 검증 |

## Comment Rules

주석은 코드가 말하지 못하는 의도와 배경을 설명할 때 사용합니다.

좋은 주석:

- 왜 이런 처리가 필요한지 설명
- 외부 시스템 제약사항 설명
- 임시 우회 코드의 제거 조건 설명

피해야 할 주석:

- 코드와 같은 말을 반복하는 주석
- 오래되어 현재 동작과 맞지 않는 주석
- 주석으로만 설명되고 코드 구조로 드러나지 않는 복잡한 로직

## Convention Checklist

코드를 작성하거나 수정할 때 다음 항목을 확인합니다.

- 네이밍이 역할과 의도를 설명하는가?
- 함수, 클래스, 모듈의 책임이 명확한가?
- 파일 위치가 프로젝트 구조와 일관되는가?
- API, 에러 응답, 로그 형식이 프로젝트 규칙과 일관되는가?
- 환경 설정과 민감 정보가 코드에 직접 포함되지 않았는가?
- 불필요한 주석, 디버깅 코드, 임시 로그가 남아 있지 않은가?
- 포매터와 린터 결과를 임의로 되돌리지 않았는가?
