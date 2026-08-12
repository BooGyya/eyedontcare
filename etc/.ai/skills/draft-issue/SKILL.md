---
name: draft-issue
description: Draft a GitHub Issue using this repository's bug, feature, or refactor template, including a Conventional Commits title prefix, valid default labels, required details, and triage information. Use when creating or improving an Issue draft.
---

# Draft Issue

먼저 다음 파일을 읽는다.

- `docs/issue-template-guide.md`
- `docs/label-strategy.md`
- `.github/ISSUE_TEMPLATE/bug_report.md`
- `.github/ISSUE_TEMPLATE/feature.md`
- `.github/ISSUE_TEMPLATE/refactor.md`
- `.github/labels.json`

## Workflow

1. 요청을 `bug`, `feature`, `refactor` 중 하나로 분류한다. 정보가 부족하면 질문 목록을 먼저 만든다.
2. 해당 템플릿의 제목 prefix와 기본 라벨을 사용한다.
3. 제목은 `fix:`, `feat:`, `refactor:`로 시작하고 대괄호 prefix를 새로 만들지 않는다.
4. 템플릿의 모든 필수 항목을 구체적인 내용으로 채운다.
5. 확인되지 않은 재현 방법, 영향 범위, 버전은 지어내지 않고 `확인 필요`로 표시한다.
6. 추가 라벨이 필요하면 `.github/labels.json`에 존재하는 라벨만 추천한다.

## Output

다음 순서로 출력한다.

1. 선택한 템플릿과 선택 이유
2. 제목
3. 라벨
4. 복사 가능한 Issue 본문
5. 작성 전에 확인할 누락 정보

GitHub에 Issue를 실제로 생성하거나 라벨을 변경하지 않는다.
