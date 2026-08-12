# Contributing Guide

이 문서는 저장소에 코드, 문서, 설정을 추가하거나 수정할 때 따라야 하는 절차를 설명합니다.

## Before You Start

1. 작업 목적을 Issue로 정리하거나 기존 Issue를 확인합니다.
2. 현재 브랜치와 작업 상태를 확인합니다.

~~~bash
git status
git branch --show-current
~~~

3. 작업에 필요한 프로젝트 문서를 읽습니다.
4. `main`에서는 직접 작업하지 않습니다.

## Create a Branch

일반 작업은 `dev`를 기준으로 작업 브랜치를 만듭니다.

~~~bash
git switch dev
git pull origin dev
git switch -c feat/<description>
~~~

브랜치 종류:

| 작업 | 브랜치 예시 | PR 대상 |
| --- | --- | --- |
| 기능 | `feat/login` | `dev` |
| 일반 버그 | `bugfix/login-validation` | `dev` |
| 리팩토링 | `refactor/auth-service` | `dev` |
| 문서 | `docs/api-guide` | `dev` |
| 릴리즈 | `release/1.0.0` | `main` |
| 긴급 수정 | `hotfix/payment-timeout` | `main` |

브랜치 이름은 영문 소문자, 숫자, 하이픈을 사용합니다.

## Develop and Test

코드 변경 시 다음 원칙을 지킵니다.

- 함수, 클래스, 모듈은 하나의 책임을 중심으로 구성합니다.
- 프로젝트 포매터와 린터 설정을 우선합니다.
- API, 에러 응답, 로그 형식을 기존 규칙과 맞춥니다.
- 환경변수와 Secret을 코드에 직접 넣지 않습니다.
- 버그 수정에는 가능하면 재현 테스트를 추가합니다.
- 리팩토링 후 기존 테스트가 통과하는지 확인합니다.
- 기능 변경과 대규모 포맷팅 변경을 섞지 않습니다.

코드 작업 후에는 다음 AI 스킬을 사용할 수 있습니다.

~~~text
review-code-convention
scan-secrets-and-config
~~~

## Commit Changes

커밋 메시지는 다음 형식을 사용합니다.

~~~text
<type>(<scope>): <subject>
~~~

예시:

~~~text
feat(auth): add login API
fix(auth): handle expired token
test(auth): add login test
docs: update API guide
~~~

하나의 커밋에는 하나의 작업 목적만 포함합니다.

스테이징된 변경사항을 목적별로 나눠야 한다면 다음 스킬을 사용합니다.

~~~text
commit-staged-changes를 사용해서 커밋 계획을 만들어줘.
~~~

스킬이 제안한 계획을 확인한 뒤 명시적으로 승인해야 실제 커밋이 실행됩니다.
Unstaged 변경사항, Secret, 관련 없는 파일은 커밋에 포함하지 않습니다.

## Create a Pull Request

PR을 만들기 전에 다음을 확인합니다.

1. 브랜치 이름과 target branch 확인
2. 변경사항과 커밋 단위 확인
3. 코드 컨벤션과 Secret 검사
4. 브랜치 종류에 맞는 테스트 수행
5. 관련 Issue 연결
6. PR 템플릿 선택
7. PR 본문 작성

종합 확인에는 다음 스킬을 사용합니다.

~~~text
pre-pr-readiness로 PR 준비 상태를 확인해줘.
draft-pr로 PR 초안을 작성해줘.
~~~

PR 본문에는 다음 내용을 포함합니다.

- 변경사항 요약
- 변경 이유
- 변경 파일·모듈·API
- 테스트 결과
- 관련 Issue
- 리뷰어가 확인할 내용
- 배포 영향과 롤백 방법

## Issue Guidelines

Issue 종류에 맞는 템플릿을 사용합니다.

- 버그: `bug_report.md`
- 기능: `feature.md`
- 리팩토링: `refactor.md`

Issue 작성 시 재현 경로, 기대 결과, 현재 결과, 영향 범위를 구체적으로 작성합니다.
초안 작성에는 다음 스킬을 사용할 수 있습니다.

~~~text
draft-issue를 사용해서 기능 요청 Issue를 작성해줘.
~~~

## Review and Merge

- 모든 병합은 PR을 통해 진행합니다.
- PR target branch가 브랜치 전략과 맞는지 확인합니다.
- 필요한 리뷰와 CI 검사를 통과한 뒤 병합합니다.
- 병합 후 작업 브랜치를 삭제합니다.
- `release/*`와 `hotfix/*`의 변경사항은 필요한 경우 `dev`에도 반영합니다.

## AI Usage and Security

- AI에게 전달하기 전에 Secret, 개인정보, 운영 전용 정보를 제거합니다.
- AI가 제안한 코드는 프로젝트 규칙과 테스트로 검증합니다.
- AI가 파일을 수정하거나 Git 상태를 변경할 때 변경 범위를 확인합니다.
- `commit-staged-changes`는 승인 전까지 커밋을 실행하지 않습니다.
- Gemini 리뷰 workflow처럼 외부 API로 diff가 전송되는 경우 민감정보가 없는지 확인합니다.

## Contribution Checklist

- [ ] 작업 브랜치가 올바른 기준 브랜치에서 생성되었습니다.
- [ ] 변경 범위가 하나의 작업 목적에 맞습니다.
- [ ] 코드 컨벤션과 포매터 규칙을 확인했습니다.
- [ ] Secret과 민감정보가 포함되지 않았습니다.
- [ ] 관련 테스트를 실행했습니다.
- [ ] 커밋 메시지와 커밋 단위가 적절합니다.
- [ ] 관련 Issue를 연결했습니다.
- [ ] 브랜치 종류에 맞는 PR 템플릿을 선택했습니다.
- [ ] PR 본문에 변경사항과 테스트 결과를 작성했습니다.
- [ ] 리뷰어가 확인해야 할 위험이나 제약을 작성했습니다.

## Reference Documents

- [코드 컨벤션](docs/code-convention.md)
- [브랜치 전략](docs/branch-strategy.md)
- [커밋 전략](docs/commit-strategy.md)
- [Issue 템플릿 가이드](docs/issue-template-guide.md)
- [PR 템플릿 가이드](docs/pr-template-guide.md)
- [라벨 전략](docs/label-strategy.md)
- [AI 스킬 가이드](docs/skill-guide.md)
