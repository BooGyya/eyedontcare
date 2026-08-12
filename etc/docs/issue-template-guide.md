# Issue Template Guide

이 문서는 GitHub Issue Template의 종류와 사용 기준을 정의합니다.

## Template Files

Issue Template은 `.github/ISSUE_TEMPLATE/` 아래에서 관리합니다.

```text
.github/
  ISSUE_TEMPLATE/
    bug_report.md
    feature.md
    refactor.md
    config.yml
```

## Template Types

| Template | Usage | Default Labels |
| --- | --- | --- |
| `bug_report.md` | 버그, 오류, 예상과 다른 동작 제보 | `type/bug`, `needs/triage` |
| `feature.md` | 새로운 기능 또는 개선 아이디어 제안 | `type/feature`, `needs/triage` |
| `refactor.md` | 기능 변경 없는 코드 구조 개선 제안 | `type/refactor`, `needs/triage` |

## `config.yml`

`config.yml`은 개별 Issue Template이 아니라 GitHub의 Issue 생성 화면을 설정하는 파일입니다.

GitHub에서 사용자가 `New issue`를 누르면 `.github/ISSUE_TEMPLATE/` 아래의 템플릿 파일과 `config.yml`을 읽어 이슈 생성 화면을 구성합니다.

현재 설정 예시는 다음과 같습니다.

```yaml
blank_issues_enabled: true
contact_links:
  - name: 질문 또는 논의
    url: https://github.com/<owner>/<repository>/discussions
    about: 버그나 기능 요청이 아닌 질문은 Discussions을 사용합니다.
```

각 항목의 의미는 다음과 같습니다.

| Field | Description |
| --- | --- |
| `blank_issues_enabled` | 템플릿을 사용하지 않는 빈 이슈 작성을 허용할지 정합니다. |
| `contact_links` | 이슈 작성 화면에 외부 링크를 표시합니다. |
| `contact_links.name` | GitHub Issue 생성 화면에 표시될 링크 이름입니다. |
| `contact_links.url` | 사용자가 링크를 클릭했을 때 이동할 URL입니다. |
| `contact_links.about` | 링크의 목적을 설명하는 짧은 안내 문구입니다. |

`contact_links`는 Discussions 페이지를 자동으로 생성하지 않습니다. 이미 존재하는 페이지나 기능으로 이동하는 링크만 보여줍니다.

따라서 Discussions 링크를 사용하려면 GitHub 저장소에서 Discussions 기능을 활성화하고, `https://github.com/<owner>/<repository>/discussions`의 `<owner>`와 `<repository>`를 실제 저장소 정보로 바꿔야 합니다.

## GitHub Discussions

GitHub Discussions는 저장소 안에서 질문, 답변, 아이디어, 공지, 설계 논의를 관리하는 게시판 기능입니다.

Discussions 기능을 켜는 방법은 다음과 같습니다.

1. GitHub 저장소 상단의 `Settings`로 이동합니다.
2. 왼쪽 메뉴에서 `General`을 선택합니다.
3. `Features` 섹션으로 이동합니다.
4. `Discussions` 체크박스를 켭니다.

기능을 켜면 저장소 상단 메뉴에 `Discussions` 탭이 표시됩니다.

## Front Matter

Issue Template은 GitHub가 YAML front matter를 인식합니다.

예시:

```yaml
---
name: 버그 리포트
about: 프로그램의 오류나 예상과 다른 동작을 제보합니다.
title: "[BUG] "
labels: type/bug, needs/triage
assignees: ""
---
```

각 항목의 의미는 다음과 같습니다.

| Field | Description |
| --- | --- |
| `name` | GitHub Issue 생성 화면에 표시되는 템플릿 이름입니다. |
| `about` | 템플릿 설명입니다. |
| `title` | Issue 제목에 자동으로 들어갈 prefix입니다. |
| `labels` | Issue 생성 시 자동으로 붙일 라벨입니다. |
| `assignees` | Issue 생성 시 자동으로 지정할 담당자입니다. |

## Label Requirement

front matter에 적은 `labels`의 라벨은 GitHub 저장소에 존재해야 합니다.

라벨이 없다면 다음 중 하나를 먼저 적용합니다.

- GitHub 화면에서 라벨을 직접 생성합니다.
- `.github/labels.json`과 label sync workflow를 사용해 라벨을 동기화합니다.

## Usage Rules

- 버그 제보는 `bug_report.md`를 사용합니다.
- 기능 제안은 `feature.md`를 사용합니다.
- 기능 변경 없는 구조 개선 제안은 `refactor.md`를 사용합니다.
- 이슈 작성자는 재현 경로, 기대 결과, 현재 결과를 가능한 한 구체적으로 작성합니다.
- 관리자는 이슈를 확인한 뒤 `needs/triage`를 제거하고 우선순위나 담당 영역 라벨을 추가합니다.
