# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

캐주얼 게이머 전반. 연령 불문, 웹캠이 달린 PC(또는 노트북)만 있으면 누구나 브라우저에서 바로 플레이할 수 있는 가벼운 게임 유저가 대상이다. 한국어 서비스이며, 별도 설치나 특수 장비 없이 짧은 시간(게임당 약 1분) 즐기는 상황을 전제한다.

## Product Purpose

"eye-dont-care"는 순수 엔터테인먼트 캐주얼 게임 플랫폼이다. 웹캠 기반 시선 추적·눈 깜빡임 인식이라는 신선한 조작 방식을 내세워, 브라우저에서 간편하게 즐기는 미니게임 모음을 제공한다. 성공은 사용자가 부담 없이 접속해 게임을 즐기고, 랭킹·대결을 통해 다시 찾아오는 것이다.

## Positioning

특수 장비 없이 일반 웹캠만으로 눈(시선·깜빡임)이 곧 컨트롤러가 되는 웹 게임 플랫폼. 키보드·마우스 게임 포털이나 전용 하드웨어 기반 시선 추적 제품이 흉내 낼 수 없는 "설치 없이, 장비 없이, 눈으로 조작"이 핵심 차별점이다.

## Operating Context

- 브라우저 + 웹캠 환경에서 플레이. 게임 시작 전 준비(Ready) 단계를 거쳐 플레이 → 결과 화면으로 이어진다.
- 미니게임 5종이 라우트로 존재한다: `air`(Eye Hockey 에어 하키), `hold`(Eye See 눈싸움), `draw`(Eye Draw 눈으로 그리기), `rhythm`(Blink the Beat 리듬 게임), `blink`(Eye Show Speed 눈 깜빡이기).
- 플레이 모드: 혼자하기(랭킹 반영), 친구와 대결(방 생성·초대코드 입장), 랜덤 매칭, 일부 게임은 AI 대결.
- 주간 랭킹, 커뮤니티(소모임), 프로필, 알림, 설정 화면이 있다. 디스코드 연동·소모임 코드 입장은 "다음 단계 준비 예정"으로 명시된 미구현 기능이다.
- 팀 저장소는 SSAFY 프로젝트(S15P11B102) 구조로, 프론트엔드(`frontend/`)와 백엔드(`backend/`)가 분리되어 있고 Git 브랜치·커밋·PR 정책이 `docs/`에 문서화되어 있다.

## Capabilities and Constraints

- 시선·깜빡임 인식은 웹캠 기반이며 특수 장비가 필요 없다(사용자 확인). 구체 라이브러리(MediaPipe 등)는 저장소에서 아직 확인되지 않음 — 미확정 사실로 남긴다.
- 프론트엔드: Vue 3 + TypeScript + Vite, Pinia, Vue Router. 현재 화면 데이터는 `frontend/src/mocks/`의 목 데이터로 구동되며 실제 API 연동은 진행 중이다(`VITE_API_BASE_URL=/api`).
- 백엔드: Spring(Gradle) + PostgreSQL, Docker Compose 구성.
- 용어: 게임 ID는 `air/hold/draw/rhythm/blink`를 사용하고, 화면 표기는 "영문 게임명 (한글 설명)" 병기 형식을 따른다. 점수 단위는 게임별로 다르다(회·초·점).
- UI 문구는 한국어 해요체의 밝고 친근한 톤을 사용한다.

## Brand Commitments

- **web_prototype 디자인이 기준이다(사용자 확인, 구속력 있음).** 원본은 `C:\Users\SSAFY\Downloads\web_prototype`(index.html, styles.css, app.js, assets/)이며, 향후 디자인 작업은 이 프로토타입의 룩을 따른다.
- 프로토타입 PNG 에셋은 `frontend/src/assets/images/`(brand/games/illustrations/profiles)에 복사되어 있다: 로고, 눈 마스코트, 게임별 카드·메인 이미지, 프로필 아바타 8종, 일러스트(팀워크·소모임·디스코드·카카오톡).
- 서비스명: eye-dont-care (패키지명 기준). 화면 표기는 로고 에셋을 따른다.

## Evidence on Hand

- 디자인 원본: `C:\Users\SSAFY\Downloads\web_prototype` (룩의 단일 출처).
- 이미지 에셋: `frontend/src/assets/images/` 전체.
- 게임 규칙·모드·가이드 문구: `frontend/src/mocks/game-details.ts`, `gameplay.ts`, `gameResults.ts`.
- `frontend/src/mocks/`의 랭킹 기록·닉네임·점수는 전부 자리표시용 목 데이터다. 실제 사용자·실측 데이터가 아니므로 실제 성과·후기처럼 표현하지 않는다.

## Product Principles

1. **눈이 컨트롤러다** — 모든 게임 조작과 UI 판단은 "웹캠만으로 눈으로 조작한다"는 전제를 깨지 않는다.
2. **바로 플레이** — 설치·장비·긴 온보딩 없이 접속 후 최단 경로로 게임에 도달하게 한다.
3. **가볍고 유쾌하게** — 캐주얼 엔터테인먼트가 목적이므로 진지한 훈련·의료 뉘앙스를 만들지 않는다.
4. **경쟁이 재방문을 만든다** — 랭킹·대결·기록 갱신을 눈에 띄게 유지한다.
5. **프로토타입 룩 존중** — 시각 작업은 web_prototype의 확정된 브랜드 세계 안에서 다듬는다.

## Accessibility & Inclusion

제품 고유 요구사항은 아직 확정되지 않았다. 설정 화면에 "접근성 설정" 항목이 계획되어 있으나 내용 미정. 눈 조작이 핵심인 제품 특성상, 웹캠 권한 안내와 시선 인식이 어려운 사용자에 대한 대응은 향후 결정이 필요한 열린 항목이다.
