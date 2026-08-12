# Project Instructions for Claude Code

이 저장소에서 작업할 때 다음 프로젝트 규칙과 스킬을 적용합니다.

## Skill Routing

사용자의 요청이 다음 항목에 해당하면 관련 스킬을 먼저 사용합니다.

| 요청 | 사용할 스킬 |
| --- | --- |
| 코드 컨벤션·diff 리뷰 | `/review-code-convention` |
| 브랜치명·PR 대상 검사 | `/validate-branch-policy` |
| 커밋 메시지·커밋 단위 리뷰 | `/review-commit-history` |
| Issue 초안 작성 | `/draft-issue` |
| PR 초안 작성 | `/draft-pr` |
| PR 준비 상태 확인 | `/pre-pr-readiness` |
| Secret·설정 검사 | `/scan-secrets-and-config` |
| 스테이징 변경사항 커밋 | `/commit-staged-changes` |

스킬은 `.claude/skills/`에서 발견할 수 있습니다. 스킬을 실행할 때는 공통 원본과 프로젝트 문서를 함께 읽습니다.

## Project Rules

작업에 필요한 문서를 먼저 읽습니다.

- 코드: `docs/code-convention.md`
- 브랜치: `docs/branch-strategy.md`
- 커밋: `docs/commit-strategy.md`
- Issue: `docs/issue-template-guide.md`
- PR: `docs/pr-template-guide.md`
- 라벨: `docs/label-strategy.md`
- 파일·설정·Secret: `docs/gitignore-guide.md`, `docs/editor-config.md`, `docs/git-attributes.md`

문서와 실제 설정이 다르면 추측하지 말고 충돌을 보고합니다.

## Required Behavior

- 작업 시작 전에 현재 브랜치와 `git status`를 확인합니다.
- `main`에 직접 작업하거나 직접 커밋하지 않습니다.
- 일반 작업은 `dev` 기준 작업 브랜치에서 진행합니다.
- 사용자의 기존 변경사항을 삭제·덮어쓰기하지 않습니다.
- 요청하지 않은 `reset`, `rebase`, `push`, 파일 삭제를 수행하지 않습니다.
- Secret이나 민감정보를 출력·커밋·외부 서비스 전송 대상으로 만들지 않습니다.
- `commit-staged-changes`는 커밋 계획을 먼저 보여주고 명시적 승인을 받은 뒤 실행합니다.
- 커밋 후에는 `git status`와 생성된 커밋을 확인하지만 자동으로 push하지 않습니다.

검사 결과에는 `BLOCKER`, `WARNING`, `INFO`, `PASS` 상태를 사용하고, 파일 경로와 근거를 함께 제시합니다.
