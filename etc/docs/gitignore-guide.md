# Gitignore Guide

이 문서는 프로젝트에서 공통으로 사용할 `.gitignore` 기본값과 프로젝트별 확장 기준을 정의합니다.

## Purpose

`.gitignore`는 Git에 올리지 않을 로컬 파일, 빌드 산출물, 의존성 디렉터리, secret 파일을 지정합니다.

주요 목적은 다음과 같습니다.

- 로컬 환경 파일과 secret이 저장소에 올라가지 않게 합니다.
- 의존성 디렉터리와 빌드 산출물을 제외합니다.
- 운영체제, 에디터, IDE가 생성하는 불필요한 파일을 제외합니다.
- 프로젝트별 기술 스택에 맞는 ignore 규칙을 추가할 수 있는 기준을 제공합니다.

## Default Scope

이 브랜치의 `.gitignore`는 모든 프로젝트에 공통으로 적용하기 쉬운 최소 규칙만 포함합니다.

포함 범위는 다음과 같습니다.

| Group | Examples |
| --- | --- |
| Environment and secrets | `.env`, `.env.*`, private key files |
| Logs | `*.log`, `logs/` |
| OS files | `.DS_Store`, `Thumbs.db`, `desktop.ini` |
| Editor and IDE files | `.idea/`, local `.vscode/` files |
| Node.js | `node_modules/`, `dist/`, `build/`, `coverage/` |
| Python | `.venv/`, `__pycache__/`, `.pytest_cache/` |
| Java and JVM | `target/`, `out/`, compiled archives |
| Temporary files | `*.tmp`, `*.swp` |
| Local data | local SQLite and DB files |

## Environment Files

실제 secret 값이 들어가는 환경 파일은 Git에 올리지 않습니다.

```gitignore
.env
.env.*
!.env.example
```

`.env.example`은 예외로 두어 저장소에 포함합니다. 이 파일에는 실제 secret 값이 아니라 필요한 환경변수 이름과 예시 값만 작성합니다.

예시:

```dotenv
DATABASE_URL=postgresql://user:password@localhost:5432/app
JWT_SECRET=replace-me
```

## VS Code Files

개인별 VS Code 설정은 기본적으로 제외하되, 팀에서 공유할 만한 설정은 예외로 둡니다.

```gitignore
.vscode/*
!.vscode/extensions.json
!.vscode/settings.json
```

공유해도 되는 파일 예시는 다음과 같습니다.

| File | Usage |
| --- | --- |
| `.vscode/extensions.json` | 팀에서 권장하는 확장 목록 |
| `.vscode/settings.json` | 프로젝트 공통 에디터 설정 |

개인별 launch profile, task, workspace state는 프로젝트 상황에 따라 추가 여부를 결정합니다.

## Using Toptal gitignore.io

프로젝트 기술 스택이 정해지면 Toptal gitignore.io에서 추가 규칙을 생성해 병합하는 것을 권장합니다.

서비스 URL:

```text
https://www.toptal.com/developers/gitignore
```

사용 절차:

1. 프로젝트에서 사용하는 운영체제, IDE, 언어, 프레임워크, 빌드 도구를 입력합니다.
2. 생성된 `.gitignore` 내용을 확인합니다.
3. 이 브랜치의 기본 `.gitignore` 아래에 필요한 규칙만 병합합니다.
4. lockfile, 설정 파일, template 파일이 실수로 제외되지 않는지 확인합니다.
5. 변경 후 `git status --ignored`로 ignore 결과를 검증합니다.

입력 키워드 예시는 다음과 같습니다.

| Project Type | Suggested Keywords |
| --- | --- |
| Frontend | `Node`, `VisualStudioCode`, `macOS`, `Windows`, `Linux` |
| React or Vue | `Node`, `Vite`, `VisualStudioCode` |
| Python API | `Python`, `PyCharm`, `VisualStudioCode` |
| Java or Spring | `Java`, `Gradle`, `Maven`, `IntelliJ` |
| Django | `Python`, `Django`, `VisualStudioCode` |

## Files That Should Usually Be Tracked

다음 파일은 실수로 ignore하지 않도록 주의합니다.

| File | Reason |
| --- | --- |
| `.env.example` | 필요한 환경변수 목록을 문서화합니다. |
| `package-lock.json` | npm dependency tree를 고정합니다. |
| `pnpm-lock.yaml` | pnpm dependency tree를 고정합니다. |
| `yarn.lock` | Yarn dependency tree를 고정합니다. |
| `requirements.txt` | Python dependency 목록을 공유합니다. |
| `poetry.lock` | Poetry dependency tree를 고정합니다. |
| `build.gradle`, `pom.xml` | JVM 프로젝트 빌드 설정입니다. |
| `.editorconfig` | 에디터 기본 포맷을 공유합니다. |
| `.gitattributes` | Git 줄 끝과 binary 처리 기준을 공유합니다. |

## Files That Must Not Be Tracked

다음 파일은 저장소에 올리지 않습니다.

| File Pattern | Reason |
| --- | --- |
| `.env`, `.env.*` | 실제 secret과 로컬 환경값을 포함할 수 있습니다. |
| `*.pem`, `*.key`, `*.p12`, `*.pfx` | 인증서와 private key입니다. |
| local database files | 로컬 데이터와 개인정보를 포함할 수 있습니다. |
| dependency directories | 재설치 가능한 산출물입니다. |
| build outputs | 빌드 과정에서 다시 생성됩니다. |

## Usage Rules

- 이 브랜치는 공통 최소값만 제공합니다.
- 프로젝트 생성 후 기술 스택에 맞게 Toptal gitignore.io 결과를 병합합니다.
- 생성된 규칙을 그대로 덮어쓰기보다 기존 규칙과 중복, 충돌 여부를 확인합니다.
- secret 파일이 이미 commit되었다면 `.gitignore` 추가만으로는 해결되지 않습니다.
- 이미 올라간 secret은 즉시 폐기하고, Git history와 원격 저장소 노출 여부를 점검합니다.

## Recommended Import

이 설정은 `settings/editor-config`, `git/attributes` 다음에 적용하는 것을 권장합니다.

```bash
git merge --squash origin/git/ignore
git commit -m "init: add gitignore"
```
