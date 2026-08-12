# Git Attributes Guide

이 문서는 프로젝트에서 공통으로 사용할 Git attributes 설정과 운영 기준을 정의합니다.

## Purpose

`.gitattributes`는 Git이 파일을 저장소에 넣거나 작업 디렉터리로 꺼낼 때 파일을 어떻게 처리할지 정하는 설정 파일입니다.

주요 목적은 다음과 같습니다.

- 운영체제별 줄 끝 차이로 생기는 불필요한 diff를 줄입니다.
- 저장소 안의 텍스트 파일을 LF 기준으로 관리합니다.
- Windows 전용 command script는 CRLF를 유지합니다.
- 이미지, 압축 파일, 문서 파일 같은 binary 파일을 텍스트로 diff하지 않게 합니다.

## EditorConfig vs Git Attributes

`.editorconfig`와 `.gitattributes`는 서로 다른 역할을 합니다.

| File | Role |
| --- | --- |
| `.editorconfig` | 에디터나 IDE가 파일을 저장할 때 포맷을 맞춥니다. |
| `.gitattributes` | Git이 파일을 commit, checkout, diff할 때 처리 방식을 맞춥니다. |

Windows, macOS, Linux 사용자가 함께 작업한다면 두 파일을 같이 사용하는 것을 권장합니다.

## Text Normalization

기본 설정은 모든 텍스트 파일을 LF 줄 끝 기준으로 정규화합니다.

```gitattributes
* text=auto eol=lf
```

각 항목의 의미는 다음과 같습니다.

| Attribute | Description |
| --- | --- |
| `text=auto` | Git이 텍스트 파일과 binary 파일을 자동으로 판별합니다. |
| `eol=lf` | 텍스트 파일을 작업 디렉터리와 저장소에서 LF 줄 끝으로 맞춥니다. |

이 설정을 두면 Windows 사용자가 작업하더라도 저장소에 들어가는 텍스트 파일의 줄 끝을 LF 기준으로 유지할 수 있습니다.

## Windows Command Scripts

Windows command script는 실행 환경 호환성을 위해 CRLF 줄 끝을 유지합니다.

```gitattributes
*.bat text eol=crlf
*.cmd text eol=crlf
```

PowerShell script는 기본 텍스트 규칙을 따릅니다.

## Binary Files

이미지, 압축 파일, Java archive, Office 문서, Figma 파일은 binary로 처리합니다.

```gitattributes
*.png binary
*.jpg binary
*.jpeg binary
*.gif binary
*.ico binary
*.webp binary
*.pdf binary
*.zip binary
*.gz binary
*.tar binary
*.jar binary
*.war binary
*.7z binary
*.doc binary
*.docx binary
*.ppt binary
*.pptx binary
*.xls binary
*.xlsx binary
*.fig binary
```

binary로 지정한 파일은 Git이 줄 끝 변환을 하지 않고, 일반 텍스트 diff 대상으로도 취급하지 않습니다.

## Renormalization

이미 프로젝트에 파일이 들어간 뒤 `.gitattributes`를 추가했다면 한 번 정규화 커밋이 필요할 수 있습니다.

```bash
git add --renormalize .
git status
git commit -m "chore: normalize line endings"
```

새 프로젝트를 시작할 때 이 브랜치를 먼저 적용하면 별도의 정규화 커밋 없이 시작할 수 있습니다.

## Usage Rules

- 이 설정은 `settings/editor-config`와 함께 적용하는 것을 권장합니다.
- 텍스트 파일은 기본적으로 LF를 사용합니다.
- Windows에서 직접 실행해야 하는 `.bat`, `.cmd` 파일만 CRLF 예외를 둡니다.
- 프로젝트에서 특별한 binary 포맷을 사용한다면 해당 확장자를 추가합니다.
- 줄 끝 변경만 발생한 대량 diff가 생기면 `.gitattributes` 적용 여부를 먼저 확인합니다.

## Recommended Import

이 설정은 `settings/editor-config` 다음에 적용하는 것을 권장합니다.

```bash
git merge --squash origin/git/attributes
git commit -m "init: add git attributes"
```
