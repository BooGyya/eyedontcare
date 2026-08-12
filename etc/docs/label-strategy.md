# GitHub Label Strategy

이 문서는 GitHub Issue와 Pull Request를 일관되게 분류하기 위한 라벨 전략을 정의합니다.

- 라벨은 작업 종류, 우선순위, 진행 상태, 담당 영역, 추가 확인 필요 여부를 표현합니다.
- 라벨 이름은 사람이 읽기 쉽고 필터링하기 쉬운 `<category>/<name>` 형식을 사용합니다.
- Issue Template과 Pull Request Template에서 사용하는 기본 라벨은 이 문서와 `.github/labels.json`에 정의된 라벨을 기준으로 합니다.
- Issue와 PR 제목 prefix는 PR 템플릿과 같은 Conventional Commits 형식인 `feat:`, `fix:`, `refactor:` 등을 사용합니다.

## Files

```text
.github/labels.json
.github/workflows/sync-labels.yml
docs/label-strategy.md
```

## Basic Rules

- 하나의 이슈나 PR에는 최소 하나의 `type/*` 라벨을 붙입니다.
- 새로 생성된 이슈는 기본적으로 `needs/triage` 상태에서 시작합니다.
- 작업 우선순위가 정해지면 `priority/*` 라벨을 붙입니다.
- 담당 영역이 명확하면 `area/*` 라벨을 붙입니다.
- 작업이 진행되면 `status/*` 라벨을 갱신합니다.
- 라벨을 새로 만들기 전에 기존 라벨로 표현할 수 있는지 먼저 확인합니다.
- 라벨 이름은 소문자와 하이픈을 사용합니다.
- `pinned`, `security`, `blocked`는 `github/stale-issues` workflow의 예외 라벨로 사용하므로 `<category>/<name>` 형식의 예외로 둡니다.

## Label Categories

| Category | Purpose | Examples |
| --- | --- | --- |
| `type/*` | 작업 종류 | `type/feature`, `type/bug`, `type/docs` |
| `priority/*` | 처리 우선순위 | `priority/high`, `priority/medium`, `priority/low` |
| `status/*` | 작업 진행 상태 | `status/todo`, `status/in-progress`, `status/review`, `status/stale` |
| `needs/*` | 추가 확인 필요 여부 | `needs/triage`, `needs/discussion`, `needs/reproduce` |
| `area/*` | 영향 영역 | `area/backend`, `area/frontend`, `area/data` |
| `level/*` | 작업 난이도 | `level/easy`, `level/medium`, `level/hard` |
| Stale exceptions | stale workflow 예외 | `pinned`, `security`, `blocked` |

## Type Labels

| Label | Usage | Title Prefix |
| --- | --- | --- |
| `type/feature` | 새로운 기능 또는 기능 개선 | `feat: ` |
| `type/bug` | 버그, 오류, 예상과 다른 동작 | `fix: ` |
| `type/refactor` | 기능 변경 없는 코드 구조 개선 | `refactor: ` |
| `type/docs` | 문서 추가 또는 수정 | `docs: ` |
| `type/hotfix` | 운영 환경 긴급 수정 | `fix: ` |
| `type/release` | 릴리즈 준비, 버전 업데이트 | `release: ` |
| `type/test` | 테스트 추가 또는 수정 | `test: ` |
| `type/chore` | 설정, 유지보수, 기타 작업 | `chore: ` |

## Priority Labels

| Label | Usage |
| --- | --- |
| `priority/high` | 운영 영향, 사용자 영향, 일정상 빠른 처리가 필요한 작업 |
| `priority/medium` | 일반적인 우선순위의 작업 |
| `priority/low` | 급하지 않거나 여유가 있을 때 처리할 작업 |

## Status Labels

| Label | Usage |
| --- | --- |
| `status/todo` | 작업 대기 상태 |
| `status/in-progress` | 담당자가 작업 중 |
| `status/review` | 리뷰 또는 확인 대기 |
| `status/done` | 완료된 작업 |
| `status/stale` | 오래 활동이 없어 stale workflow가 표시한 issue 또는 PR |

## Stale Exception Labels

`github/stale-issues` workflow에서 자동 stale 처리 예외로 사용하는 라벨입니다.

| Label | Usage |
| --- | --- |
| `pinned` | 의도적으로 열어두는 issue 또는 PR |
| `security` | 보안상 자동 close하면 안 되는 issue 또는 PR |
| `blocked` | 외부 의존성이나 의사결정을 기다리는 issue 또는 PR |

## Needs Labels

| Label | Usage |
| --- | --- |
| `needs/triage` | 최초 검토와 분류가 필요함 |
| `needs/discussion` | 팀 논의 또는 의사결정이 필요함 |
| `needs/reproduce` | 버그 재현 또는 추가 정보가 필요함 |

## Area Labels

| Label | Usage |
| --- | --- |
| `area/backend` | 백엔드 애플리케이션 |
| `area/frontend` | 프론트엔드 애플리케이션 |
| `area/data` | 데이터베이스, 데이터 파이프라인, 분석 작업 |
| `area/infra` | 배포, 인프라, CI/CD, 운영 설정 |
| `area/docs` | 문서 |

## Level Labels

| Label | Usage |
| --- | --- |
| `level/easy` | 처음 기여하기 좋거나 난이도가 낮은 작업 |
| `level/medium` | 일반적인 난이도의 작업 |
| `level/hard` | 복잡도, 영향 범위, 리스크가 큰 작업 |

## Issue Template Integration

Issue Template의 front matter에서 기본 라벨과 제목 prefix를 자동으로 지정합니다.

예시:

```yaml
title: "fix: "
labels: type/bug, needs/triage
```

권장 기본값:

| Template | Title Prefix | Default Labels |
| --- | --- | --- |
| `bug_report.md` | `fix: ` | `type/bug`, `needs/triage` |
| `feature.md` | `feat: ` | `type/feature`, `needs/triage` |
| `refactor.md` | `refactor: ` | `type/refactor`, `needs/triage` |

`[FEAT]`, `[BUG]`, `[REFACTOR]` 같은 대괄호 prefix는 사용하지 않습니다.

## Pull Request Template Integration

GitHub는 PR Template의 YAML front matter를 Issue Template처럼 자동 처리하지 않습니다.

따라서 PR 라벨 자동화가 필요하다면 다음 중 하나를 사용합니다.

- GitHub 화면에서 리뷰어가 직접 라벨을 지정합니다.
- GitHub Actions labeler를 사용해 브랜치명이나 변경 파일 기준으로 라벨을 붙입니다.

권장 매핑:

| Branch Type | Title Prefix | Recommended Labels |
| --- | --- | --- |
| `feat/*` | `feat: ` | `type/feature` |
| `bugfix/*` | `fix: ` | `type/bug` |
| `refactor/*` | `refactor: ` | `type/refactor` |
| `docs/*` | `docs: ` | `type/docs` |
| `release/*` | `release: ` | `type/release` |
| `hotfix/*` | `fix: ` | `type/hotfix`, `priority/high` |

## Label Sync

`.github/labels.json`은 GitHub 라벨 목록을 정의하는 데이터 파일입니다.

이 파일을 추가하는 것만으로 GitHub 라벨이 자동 생성되지는 않습니다. 이 브랜치는 `.github/workflows/sync-labels.yml`을 함께 제공해서 저장소에 import된 뒤 다음 방식으로 라벨을 실제 GitHub 라벨 목록에 반영합니다.

- `github/labels`를 `dev`에 병합하거나 최종 `main`에 반영할 때 `.github/labels.json` 또는 `.github/workflows/sync-labels.yml` 변경이 push되면 자동 동기화합니다.
- GitHub Actions 화면에서 `Sync GitHub Labels` workflow를 수동 실행할 수 있습니다.
- `.github/labels.json`에 있는 라벨은 생성하거나 갱신합니다.
- `.github/labels.json`에 없는 기존 라벨은 삭제합니다.

기본 라벨만 보인다면 `.github/labels.json` 파일만 추가된 상태이고 label sync workflow가 아직 실행되지 않은 상태입니다.

## Triage Flow

새 이슈가 생성되면 다음 순서로 정리합니다.

1. `needs/triage` 라벨이 붙은 이슈를 확인합니다.
2. 이슈 내용이 충분한지 확인합니다.
3. 작업 종류에 맞는 `type/*` 라벨을 확인합니다.
4. 영향 영역에 맞는 `area/*` 라벨을 추가합니다.
5. 우선순위가 정해지면 `priority/*` 라벨을 추가합니다.
6. 작업 가능 상태가 되면 `needs/triage`를 제거하고 `status/todo`를 추가합니다.

## Examples

버그 이슈:

```text
type/bug
area/backend
priority/high
needs/reproduce
```

기능 요청:

```text
type/feature
area/frontend
priority/medium
status/todo
```

문서 수정 PR:

```text
type/docs
area/docs
status/review
```
