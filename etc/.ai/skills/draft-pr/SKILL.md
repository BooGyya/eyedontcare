---
name: draft-pr
description: Draft a GitHub Pull Request using this repository's branch-specific PR template, target branch, Conventional Commits title prefix, labels, change summary, tests, and review checklist. Use when preparing or reviewing a PR draft.
---

# Draft Pull Request

먼저 다음 파일을 읽는다.

- `docs/pr-template-guide.md`
- `docs/branch-strategy.md`
- `docs/label-strategy.md`
- `.github/PULL_REQUEST_TEMPLATE/` 아래의 관련 템플릿
- `.github/labels.json`

## Workflow

1. 현재 source branch와 요청된 target branch를 확인한다.
2. branch type에 맞는 템플릿을 선택한다.

| Source branch | Target branch | Template |
|---|---|---|
| `feat/*` | `dev` | `feat.md` |
| `bugfix/*` | `dev` | `bugfix.md` |
| `refactor/*` | `dev` | `refactor.md` |
| `docs/*` | `dev` | `docs.md` |
| `release/*` | `main` | `release.md` |
| `hotfix/*` | `main` | `hotfix.md` |

3. 제목 prefix와 추천 라벨을 템플릿·문서 기준으로 정한다.
4. 실제 diff, 커밋, 테스트 결과만 사용해 본문을 작성한다.
5. 관련 Issue 번호, 테스트 결과, 리뷰어 확인 사항, 배포·롤백 영향을 빠뜨리지 않는다.
6. 템플릿의 metadata comment는 사람이 참고하는 정보이며 GitHub가 자동으로 라벨이나 target branch를 적용한다고 설명하지 않는다.

## Output

선택 템플릿, source/target, 제목, 라벨, 복사 가능한 본문, 누락된 확인 사항 순서로 출력한다. GitHub에 PR을 생성하거나 라벨을 변경하지 않는다.
