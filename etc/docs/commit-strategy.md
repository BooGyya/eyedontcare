# Git Commit Strategy

이 문서는 템플릿으로 생성되는 실제 프로젝트에서 사용할 Git 커밋 전략을 정의합니다.

- 브랜치 전략은 `main`을 안정 브랜치, `dev`를 개발 통합 브랜치로 사용합니다.
- 커밋 전략은 이 흐름을 기준으로 작업 단위, 메시지 형식, PR 전 정리 방식을 통일하는 것을 목표로 합니다.

## Basic Git Rules

### main 직접 수정 금지

`main` 브랜치는 배포 가능한 안정 상태를 유지하는 브랜치입니다.

규칙:

- `main`에서 직접 작업하지 않습니다.
- 일반 기능 개발은 `dev`에서 작업 브랜치를 생성해 진행합니다.
- 긴급 운영 버그만 `main`에서 `hotfix/*` 브랜치를 생성해 수정합니다.
- `main`에는 PR과 리뷰를 거친 변경사항만 병합합니다.

### dev에서 작업 브랜치 생성

기능 개발, 문서 수정, 일반 버그 수정, 리팩토링은 `dev`를 기준으로 브랜치를 생성합니다.

```bash
git switch dev
git pull origin dev
git switch -c feat/login
```

브랜치 타입 예시:

```text
feat/*
bugfix/*
refactor/*
docs/*
```

### 작고 명확한 단위로 커밋

커밋은 최대한 작은 작업 단위로 나누어 작성합니다.

좋은 커밋 단위:

- 하나의 기능 추가
- 하나의 버그 수정
- 하나의 리팩토링 목적
- 하나의 문서 변경
- 하나의 설정 변경

피해야 할 커밋:

- 여러 기능과 버그 수정을 한 번에 묶은 커밋
- 코드 변경과 대규모 포맷팅이 섞인 커밋
- 메시지만 보고 변경 의도를 알 수 없는 커밋

### PR 전 최신 dev 반영

PR을 만들기 전 작업 브랜치에 최신 `dev` 내용을 반영합니다.

팀 규칙에 따라 `merge` 또는 `rebase` 중 하나를 선택합니다.
프로젝트에서 별도 규칙을 정하지 않았다면 협업 중인 공유 브랜치에서는 `merge`, 개인 작업 브랜치에서는 `rebase`를 사용할 수 있습니다.

merge 방식:

```bash
git switch dev
git pull origin dev
git switch feat/login
git merge dev
```

rebase 방식:

```bash
git switch feat/login
git fetch origin
git rebase origin/dev
```

rebase 후 원격 브랜치에 이미 push한 이력이 있다면 force push가 필요할 수 있습니다.
이때는 일반 `--force` 대신 `--force-with-lease`를 사용합니다.

```bash
git push --force-with-lease origin feat/login
```

`--force-with-lease`는 내가 마지막으로 알고 있던 원격 브랜치 상태와 현재 원격 브랜치 상태가 같을 때만 강제 push를 수행합니다.
내가 rebase하는 동안 다른 사람이 같은 원격 브랜치에 새 커밋을 push했다면 명령이 실패하므로,
다른 사람의 작업을 실수로 덮어쓰는 일을 줄일 수 있습니다.

```text
--force
  원격 브랜치를 조건 없이 덮어쓸 수 있음

--force-with-lease
  원격 브랜치가 내가 알고 있던 상태일 때만 덮어씀
```

### 커밋 후 원격 브랜치 push

작업 브랜치에서 커밋을 만든 뒤 원격 브랜치로 push합니다.

```bash
git switch feat/login
git add .
git commit -m "feat: add login API"
git push origin feat/login
```

이후 GitHub 또는 GitLab에서 `dev` 브랜치를 대상으로 PR을 생성합니다.

## Commit Message Format

커밋 메시지는 Conventional Commits 형식을 기본으로 사용합니다.

```text
<type>(<scope>): <subject>
```

`scope`는 선택 사항입니다.

예시:

```text
feat(auth): add login API
fix(auth): handle expired access token
docs: update branch strategy
ci: add GitHub Actions workflow
```

## Message Rules

### 제목

- 제목은 변경 내용을 짧고 명확하게 작성합니다.
- 가능하면 50자 이내로 작성합니다.
- 마침표로 끝내지 않습니다.
- 무엇을 했는지 드러나게 작성합니다.

### 본문

본문은 필요한 경우에만 작성합니다.

본문에는 다음 내용을 적습니다.

- 무엇을 추가하거나 변경했는지
- 왜 변경했는지
- 리뷰어가 알아야 할 배경이나 제약사항

본문에서는 코드가 어떻게 동작하는지 길게 설명하기보다, 변경 이유와 의도를 중심으로 작성합니다.

예시:

```text
feat(auth): add login API

Add an access-token based login endpoint for user authentication.
This prepares the backend for frontend login integration.
```

### 이슈 연결

이슈 기반 작업이라면 커밋 본문이나 PR 본문에 이슈 번호를 연결합니다.

```text
Refs #12
Closes #12
Fixes #12
Resolves #12
```

일반적으로 이슈를 자동으로 닫는 키워드는 PR 본문에 쓰는 것을 권장합니다.

## Commit Types

| Type | Description | Example |
| --- | --- | --- |
| `init` | 프로젝트 초기 설정 | `init: initialize Spring Boot project` |
| `feat` | 새로운 기능 추가 | `feat(auth): add login API` |
| `fix` | 버그 수정 | `fix(auth): handle expired token` |
| `build` | 빌드 시스템 또는 외부 의존성 변경 | `build: add Dockerfile` |
| `chore` | 기능 변화 없는 기타 작업과 설정 변경 | `chore: update env example` |
| `ci` | CI/CD 설정 변경 | `ci: add test workflow` |
| `docs` | 문서 수정 | `docs: update README` |
| `style` | 코드 포맷팅, 세미콜론, 들여쓰기 등 | `style: format user service` |
| `refactor` | 기능 변경 없는 코드 구조 개선 | `refactor(auth): split token service` |
| `test` | 테스트 추가 또는 수정 | `test(auth): add login test` |
| `perf` | 성능 개선 | `perf(post): optimize list query` |
| `revert` | 이전 커밋 되돌리기 | `revert: revert login API change` |
| `release` | 버전 릴리즈 | `release: v1.0.0` |

## Gitmoji

프로젝트에서 원한다면 gitmoji를 함께 사용할 수 있습니다.

공식 CLI를 사용하려면 전역으로 설치합니다.

```bash
npm install -g gitmoji-cli
```

설치 후 다음 명령으로 대화형 커밋 메시지를 작성할 수 있습니다.

```bash
gitmoji -c
```

전역 설치 없이 사용할 수도 있습니다.

```bash
npx gitmoji-cli -c
```

형식:

```text
<emoji> <type>(<scope>): <subject>
```

예시:

```text
✨ feat(auth): add login API
🐛 fix(auth): handle expired token
📝 docs: update README
```

gitmoji는 선택 사항입니다.
팀에서 사용하기로 정했다면 모든 커밋에서 일관되게 사용하고,
사용하지 않기로 했다면 Conventional Commits만 사용합니다.

권장 매핑:

| Gitmoji | Type | Usage |
| --- | --- | --- |
| 🎉 | `init` | 프로젝트 초기 설정 |
| ✨ | `feat` | 새로운 기능 추가 |
| 🐛 | `fix` | 버그 수정 |
| 🏗️ | `build` | 빌드 시스템, Docker, 의존성 변경 |
| 🔧 | `chore` | 설정 파일, 환경 변수, 기타 작업 |
| 👷 | `ci` | CI/CD 워크플로 변경 |
| 📝 | `docs` | 문서 추가 또는 수정 |
| 🎨 | `style` | 코드 포맷팅, 스타일 정리 |
| ♻️ | `refactor` | 기능 변경 없는 리팩토링 |
| ✅ | `test` | 테스트 추가 또는 수정 |
| ⚡️ | `perf` | 성능 개선 |
| ⏪️ | `revert` | 이전 커밋 되돌리기 |
| 🚀 | `release` | 버전 릴리즈 |

gitmoji를 사용할 때도 커밋 타입은 유지합니다.

```bash
git commit -m "✨ feat(auth): add login API"
git commit -m "🐛 fix(auth): handle token refresh error"
git commit -m "📝 docs: update commit strategy"
```

## Branch Type And Commit Type

브랜치 타입과 커밋 타입은 완전히 같을 필요는 없지만, 가능한 한 작업 목적이 일치하도록 작성합니다.

| Branch | Common Commit Types |
| --- | --- |
| `feat/*` | `feat`, `fix`, `test`, `docs`, `refactor`, `chore` |
| `bugfix/*` | `fix`, `test` |
| `hotfix/*` | `fix`, `test`, `release` |
| `refactor/*` | `refactor`, `test` |
| `docs/*` | `docs` |
| `release/*` | `release`, `fix`, `docs`, `chore` |

예를 들어 `feat/login` 브랜치에서도 테스트를 추가한다면 `test(auth): add login test`
커밋을 사용할 수 있습니다.

또한 기능 개발 중 해당 기능 안에서 발견한 작은 버그를 함께 수정한다면
`fix(auth): handle login validation error`처럼 `fix` 커밋을 사용할 수 있습니다.
브랜치 타입은 작업의 큰 목적을 나타내고, 커밋 타입은 각 커밋의 실제 변경 목적을 나타냅니다.

## Recommended Examples

```bash
git commit -m "init: initialize FastAPI project"
git commit -m "build: add Dockerfile"
git commit -m "chore: add env example"
git commit -m "feat(auth): add login API"
git commit -m "test(auth): add login unit test"
git commit -m "fix(auth): handle password validation error"
git commit -m "refactor(auth): split token service"
git commit -m "docs: update API guide"
git commit -m "ci: add test workflow"
git commit -m "release: v1.0.0"
```

## Template Repository Rules

이 템플릿 저장소의 모든 템플릿 브랜치에서도 같은 커밋 규칙을 사용합니다.
예를 들어 `settings/*`, `git/*`, `github/*`, `ai/*`, `docs/*`, `api/*`,
`modules/*`, `frontend/*`, `backend/*`, `data/*`, `observability/*` 브랜치에 동일하게 적용합니다.

권장 커밋:

```text
docs: add branch strategy
docs: add commit strategy
docs: add FastAPI template README
chore: add FastAPI Dockerfile
build: add FastAPI docker compose
```

각 기술 스택의 `README.md`는 단일 커밋으로 작성합니다.

```text
docs: add <template-name> README
```
