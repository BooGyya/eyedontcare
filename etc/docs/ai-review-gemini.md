# Gemini Code Review Guide

이 문서는 Gemini API를 사용해 Pull Request 코드 리뷰를 자동으로 실행하는 방법을
정의합니다.

## Workflow File

```text
.github/workflows/ai-review-gemini.yml
```

이 워크플로는 PR diff와 `docs` 폴더의 문서를 Gemini API로 전달하고, 리뷰 결과를
PR 코멘트로 작성합니다.

## Required Settings

GitHub repository settings에서 secret을 추가합니다.

```text
Settings -> Secrets and variables -> Actions -> Repository secrets
```

필수 secret:

| Name | Description |
| --- | --- |
| `GEMINI_API_KEY` | Gemini API key |

선택 variable:

| Name | Description | Default |
| --- | --- | --- |
| `GEMINI_REVIEW_MODEL` | 리뷰에 사용할 Gemini 모델 | `gemini-2.5-flash` |

## Review Scope

- PR diff를 기준으로 정확성, 보안, 검증 누락, 엣지 케이스, 테스트 공백을 검토합니다.
- `docs` 폴더의 Markdown, MDX, text 문서를 함께 읽고 문서화된 규칙과 요구사항을
  따르는지 확인합니다.
- 문서 기준과 관련된 지적은 해당 문서 경로를 함께 언급하도록 요청합니다.
- `docs` 문서는 최대 20개, 문서당 최대 8,000자, 전체 최대 40,000자까지만 리뷰
  컨텍스트에 포함합니다.

## Workflow Behavior

- 같은 PR에 새 커밋이 push되면 이전 Gemini 리뷰 실행은 취소하고 최신 실행만
  유지합니다.
- 이미지 파일만 변경된 PR은 리뷰를 실행하지 않습니다.
- Gemini API 호출이 실패하면 워크플로를 실패시키지 않고 warning을 남긴 뒤
  종료합니다.

## Usage Notes

- 이 브랜치는 Gemini API 전용입니다.
- OpenAI, Claude, Copilot API key와 호환되지 않습니다.
- 다른 provider를 사용하려면 해당 provider용 브랜치를 사용합니다.
- private repository에서는 코드 diff와 `docs` 문서가 Gemini API로 전송되는 점을
  팀과 합의합니다.
- 민감한 운영 정보, secret, 외부 공유가 어려운 정책 문서는 `docs`에 두지 않거나
  별도 관리합니다.