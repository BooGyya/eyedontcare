---
name: validate-branch-policy
description: Validate the current Git branch name, branch purpose, source branch, PR target branch, and merge flow against this repository's Git branch strategy. Use when creating, reviewing, renaming, or preparing to merge a branch.
---

# Validate Branch Policy

먼저 `docs/branch-strategy.md`와 `docs/commit-strategy.md`를 읽는다.

## Workflow

1. 현재 브랜치, 기본 브랜치, 원격 추적 브랜치, 최근 커밋을 확인한다.
2. 브랜치 이름을 다음 형식으로 검증한다.

```text
<type>/<description>
<type>/<issue-number>-<description>
```

3. type이 `feat`, `bugfix`, `refactor`, `docs`, `release`, `hotfix` 중 하나인지 확인한다.
4. 설명 부분에 영문 소문자, 숫자, 하이픈만 사용하는지 확인한다.
5. 브랜치의 생성 기준과 PR 대상 브랜치를 확인한다.

| 브랜치 | 기준 | 기본 대상 |
|---|---|---|
| `feat/*` | `dev` | `dev` |
| `bugfix/*` | `dev` | `dev` |
| `refactor/*` | `dev` | `dev` |
| `docs/*` | `dev` | `dev` |
| `release/*` | `dev` | `main` |
| `hotfix/*` | `main` | `main` |

6. `main` 직접 작업, 기능 브랜치의 `main` 직접 병합, release/hotfix의 후속 `dev` 반영 누락을 경고한다.
7. 원격 정보나 기준 브랜치를 확인할 수 없으면 실패로 단정하지 말고 `확인 불가`로 보고한다.

## Output

```text
PASS      branch name
PASS      source branch
WARNING   PR target branch
BLOCKER   direct work on main
```

검사 스킬이므로 브랜치를 생성·삭제·전환·push하지 않는다.
