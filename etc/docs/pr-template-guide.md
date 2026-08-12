# Pull Request Template Guide

이 문서는 PR 생성 시 브랜치 타입에 맞는 Pull Request 템플릿을 선택하는 기준을 정리합니다.

## Template Files

PR 템플릿은 `.github/PULL_REQUEST_TEMPLATE/` 아래에서 관리합니다.

```text
.github/
  PULL_REQUEST_TEMPLATE/
    feat.md
    bugfix.md
    hotfix.md
    refactor.md
    docs.md
    release.md
```

## Branch And Template Mapping

| Branch Type | Target Branch | Template |
| --- | --- | --- |
| `feat/*` | `dev` | `feat.md` |
| `bugfix/*` | `dev` | `bugfix.md` |
| `refactor/*` | `dev` | `refactor.md` |
| `docs/*` | `dev` | `docs.md` |
| `release/*` | `main` | `release.md` |
| `hotfix/*` | `main` | `hotfix.md` |

## Usage

GitHub는 브랜치 이름만 보고 PR 템플릿을 자동 선택하지 않습니다.
PR 작성자는 브랜치 타입에 맞는 템플릿을 선택해야 합니다.

PR 템플릿 상단의 metadata comment는 사람이 템플릿 목적, 권장 PR 제목 prefix, 기본 라벨, target branch를 확인하기 위한 정보입니다.
GitHub는 Issue Template과 달리 PR Template의 YAML front matter를 자동 라벨이나 제목으로 처리하지 않습니다.

metadata comment의 `title` 값은 `github/semantic-pr`의 Conventional Commits 검사와 맞춘 참고용 prefix입니다.
예를 들어 기능 PR은 `feat: ...`, 버그 수정과 긴급 수정 PR은 `fix: ...`, 릴리즈 PR은 `release: ...` 형식으로 작성합니다.

예시:

```text
feat/login        -> feat.md
bugfix/login-form -> bugfix.md
hotfix/payment    -> hotfix.md
refactor/auth     -> refactor.md
docs/api-guide    -> docs.md
release/1.0.0     -> release.md
```

## URL Parameter

GitHub PR 생성 URL에 `template` 파라미터를 추가하면 특정 템플릿을 직접 열 수 있습니다.

```text
?template=feat.md
?template=bugfix.md
?template=hotfix.md
?template=refactor.md
?template=docs.md
?template=release.md
```

예시:

```text
https://github.com/<owner>/<repo>/compare/dev...feat/login?quick_pull=1&template=feat.md
```

## Review Rules

- PR 제목은 변경 내용을 명확히 설명합니다.
- PR 본문에는 변경사항, 테스트 결과, 리뷰어가 확인해야 할 내용을 작성합니다.
- PR은 브랜치 전략의 병합 흐름에 맞는 target branch로 생성합니다.
- 리뷰 승인 후 병합하고, 병합이 끝난 작업 브랜치는 삭제합니다.
