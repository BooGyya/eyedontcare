# EditorConfig Guide

이 문서는 프로젝트에서 공통으로 사용할 EditorConfig 설정과 운영 기준을 정의합니다.

## Purpose

`.editorconfig`는 개발자가 사용하는 에디터나 IDE가 달라도 기본적인 파일 포맷을 일관되게 유지하기 위한 설정 파일입니다.

주요 목적은 다음과 같습니다.

- 파일 인코딩을 UTF-8로 통일합니다.
- 줄 끝 문자를 LF로 통일합니다.
- 기본 들여쓰기를 space 2칸으로 맞춥니다.
- 파일 마지막에 newline을 추가합니다.
- 코드 파일의 trailing whitespace를 제거합니다.

## Default Rules

기본 규칙은 모든 파일에 적용됩니다.

```ini
[*]
charset = utf-8
end_of_line = lf
indent_style = space
indent_size = 2
insert_final_newline = true
trim_trailing_whitespace = true
```

각 항목의 의미는 다음과 같습니다.

| Field | Description |
| --- | --- |
| `charset` | 파일 인코딩을 지정합니다. |
| `end_of_line` | 줄 끝 문자를 지정합니다. |
| `indent_style` | 들여쓰기 방식을 지정합니다. |
| `indent_size` | 들여쓰기 크기를 지정합니다. |
| `insert_final_newline` | 파일 마지막에 newline을 추가합니다. |
| `trim_trailing_whitespace` | 줄 끝 공백을 제거합니다. |

## Language Overrides

일부 언어는 일반적인 관례에 맞춰 기본 들여쓰기 크기를 조정합니다.

```ini
[*.{java,kt,kts,py}]
indent_size = 4
```

적용 대상은 다음과 같습니다.

| Pattern | Description |
| --- | --- |
| `*.java` | Java source file |
| `*.kt` | Kotlin source file |
| `*.kts` | Kotlin script file |
| `*.py` | Python source file |

## Markdown

Markdown 문서는 줄 끝 공백이 문법적으로 의미를 가질 수 있으므로 trailing whitespace 제거를 비활성화합니다.

```ini
[*.{md,markdown}]
trim_trailing_whitespace = false
```

## Windows Scripts

Windows batch script는 실행 환경 호환성을 위해 CRLF 줄 끝을 허용합니다.

```ini
[*.{bat,cmd}]
end_of_line = crlf
```

PowerShell script는 기본 규칙을 따릅니다.

## Usage Rules

- 이 설정은 코드 포맷터를 대체하지 않습니다.
- Prettier, Black, Spotless, Checkstyle 같은 포맷터가 있다면 해당 도구의 규칙을 우선합니다.
- 프로젝트별로 특정 언어나 프레임워크의 포맷 규칙이 있다면 `.editorconfig`에 override를 추가합니다.
- 이미 존재하는 파일의 인코딩이나 줄 끝이 깨져 있다면 에디터에서 UTF-8과 LF로 변환한 뒤 저장합니다.

## Recommended Import

이 설정은 다른 템플릿 브랜치보다 먼저 적용하는 것을 권장합니다.

```bash
git merge --squash origin/settings/editor-config
git commit -m "init: add editor config"
```
