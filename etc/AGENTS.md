# Project Instructions for Codex

이 저장소에서 작업할 때 적용하는 공통 지침입니다.

## Source of Truth

작업 영역에 따라 다음 문서를 먼저 읽습니다.

- 코드 작성·수정: `docs/code-convention.md`
- 브랜치 생성·PR 대상 확인: `docs/branch-strategy.md`
- 커밋 작성·정리: `docs/commit-strategy.md`
- Issue 작성: `docs/issue-template-guide.md`
- PR 작성: `docs/pr-template-guide.md`
- 라벨 선택: `docs/label-strategy.md`
- Secret·설정 파일: `docs/gitignore-guide.md`, `docs/editor-config.md`, `docs/git-attributes.md`

실제 프로젝트 설정 파일과 문서가 다르면 임의로 한쪽을 선택하지 말고 정책 충돌로 보고합니다.

## Required Workflow

- 작업을 시작하기 전에 `git status`와 현재 브랜치를 확인합니다.
- `main`에서 직접 작업하거나 직접 커밋하지 않습니다.
- 일반 작업은 `dev`에서 `feat/*`, `bugfix/*`, `refactor/*`, `docs/*` 브랜치를 만들어 진행합니다.
- 작업 전후에 관련 테스트와 기존 기능 영향 범위를 확인합니다.
- PR 전에는 `pre-pr-readiness`를 사용합니다.
- 코드 리뷰 전에는 `review-code-convention`과 `scan-secrets-and-config`를 사용합니다.
- 커밋 전에는 `review-commit-history`를 사용합니다.
- 스테이징된 변경사항을 커밋할 때는 `commit-staged-changes`를 사용하고, 계획을 먼저 제시합니다.

## Change Safety

- 사용자가 만든 변경사항을 덮어쓰거나 되돌리지 않습니다.
- 요청하지 않은 `reset`, `rebase`, `push`, 파일 삭제를 수행하지 않습니다.
- Secret, access token, 비밀번호, private key를 출력하거나 커밋하지 않습니다.
- `.gitignore`에 추가하는 것만으로 이미 추적된 Secret이 제거되었다고 판단하지 않습니다.
- 외부 AI 서비스로 전송되는 workflow가 있으면 민감정보 전송 가능성을 확인합니다.

## Project Skills

스킬은 `.agents/skills/`에서 사용할 수 있습니다.

| 요청 | 사용할 스킬 |
| --- | --- |
| 코드 규칙 검사 | `review-code-convention` |
| 브랜치 규칙 검사 | `validate-branch-policy` |
| 커밋 검사 | `review-commit-history` |
| Issue 초안 작성 | `draft-issue` |
| PR 초안 작성 | `draft-pr` |
| PR 전 종합 검사 | `pre-pr-readiness` |
| Secret·설정 검사 | `scan-secrets-and-config` |
| 스테이징 변경사항 커밋 | `commit-staged-changes` |

스킬은 프로젝트 규칙을 대신 결정하지 않습니다. 항상 저장소의 `docs/`와 `.github/` 파일을 기준으로 판단합니다.

## Response Format

검사 결과는 다음 상태를 사용합니다.

- `BLOCKER`: 커밋이나 PR 전에 반드시 해결해야 하는 문제
- `WARNING`: 영향도를 확인해야 하는 문제
- `INFO`: 참고 사항
- `PASS`: 확인을 통과한 항목

발견 사항에는 가능한 한 파일 경로, 줄 번호, 적용 규칙, 근거, 수정 방향을 포함합니다.
