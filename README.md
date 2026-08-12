<div align="center">

<img src="frontend/src/assets/images/brand/logo.png" alt="eye dont care 로고" width="320"/>

### 눈으로 놀고, 잠깐의 휴식, 큰 즐거움

**웹캠 하나로 시선과 눈 깜빡임이 컨트롤러가 되는 캐주얼 미니게임 플랫폼**

SSAFY 15기 공통 프로젝트 · 2026.07.06 ~ 2026.08.14

![Vue](https://img.shields.io/badge/Vue_3-4FC08D?style=flat-square&logo=vuedotjs&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=flat-square&logo=typescript&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_4-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java_25-007396?style=flat-square&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white)
![LiveKit](https://img.shields.io/badge/OpenVidu_(LiveKit)-1F8CEB?style=flat-square&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)
![Jenkins](https://img.shields.io/badge/Jenkins-D24939?style=flat-square&logo=jenkins&logoColor=white)

<img src="docs/portfolio/assets/main-page.gif" alt="메인 페이지" width="720"/>

</div>

---

## 목차

1. [프로젝트 소개](#프로젝트-소개)
2. [주요 기능](#주요-기능)
3. [화면 구성](#화면-구성)
4. [기술 스택](#기술-스택)
5. [시스템 아키텍처](#시스템-아키텍처)
6. [프로젝트 폴더 구조](#프로젝트-폴더-구조)
7. [팀원 소개](#팀원-소개)
8. [협업 방식](#협업-방식)
9. [프로젝트 산출물](#프로젝트-산출물)
10. [프로젝트 결과물](#프로젝트-결과물)

---

## 프로젝트 소개

**eye dont care**는 특수 장비 없이 **일반 웹캠만으로 눈(시선·깜빡임)이 곧 컨트롤러가 되는** 웹 미니게임 플랫폼입니다.

- **기획 배경** — 시선 추적은 보통 전용 하드웨어가 필요한 기술입니다. 우리는 "브라우저 + 웹캠"만으로 이 경험을 누구나 즐길 수 있게 만들어, 설치도 장비도 필요 없는 새로운 조작 방식의 캐주얼 게임을 목표로 했습니다.
- **대상** — 웹캠 달린 PC만 있으면 되는 캐주얼 게이머 전반. 게임당 약 1분, 짧고 가볍게 즐기는 경험을 전제로 합니다.
- **차별점** — 키보드·마우스 게임 포털이나 전용 하드웨어 기반 시선 추적 제품이 흉내 낼 수 없는 **"설치 없이, 장비 없이, 눈으로 조작"**.

| | |
| --- | --- |
| **서비스명** | eye dont care |
| **프로젝트 기간** | 2026.07.06 ~ 2026.08.14 (6주, SSAFY 15기 공통 프로젝트) |
| **팀** | B102 (6명) |
| **배포** | AWS EC2 · https://eyedontcare.shop (프로젝트 종료로 서버 회수됨) |

---

## 주요 기능

### 👁️ 시선 추적 & 캘리브레이션

- 게임 시작 전 **눈 뜬 상태/감은 상태 기록 → 시선 보정**으로 이어지는 캘리브레이션 플로우
- **SeeSo SDK** 기반 시선 추적 + 라이선스 미설정 환경에서는 **MediaPipe 얼굴 랜드마크 기반 시선 추정으로 자동 폴백**하는 어댑터 구조
- 캘리브레이션 상태는 세션 내 유지되어, 연속으로 다른 게임을 플레이할 때 재보정 없이 바로 시작

### 🎮 미니게임 5종

| 게임 | 조작 방식 | 설명 |
| --- | --- | --- |
| **Eye Show Speed** (눈 깜빡이기) | 깜빡임 인식 | 20초 안에 정확하게 깜빡여 미션 완수 |
| **Eye Hockey** (에어하키) | 시선 이동 | 시선으로 패들을 움직여 공을 받아치는 대전 게임 |
| **Eye See** (눈싸움) | 눈 뜬 상태 유지 | 깜빡이면 지는 클래식 눈싸움 |
| **Eye Draw** (눈으로 그리기) | 시선 궤적 | 시선으로 그림을 그리면 **AI(GPT-4o)가 채점** |
| **Blink the Beat** (리듬 게임) | 좌/우 눈 깜빡임 | 분홍 노트는 왼쪽 눈, 파랑 노트는 오른쪽 눈으로 입력하는 리듬 게임 |

### ⚔️ 플레이 모드

- **혼자하기** — 싱글 플레이, 기록은 랭킹에 반영
- **친구와 대결** — 방 생성 후 초대코드로 입장, WebRTC로 상대 화면을 보며 실시간 대전
- **랜덤 매칭** — 매칭 큐 기반 실시간 상대 찾기 (WebSocket)
- **AI 대결** — 일부 게임은 AI 상대와 플레이

### 🏆 랭킹 · 길드 · 프로필

- 게임별 주간 랭킹 (TOP3 포디움 + 순위 변동 표시)
- 길드(소모임) 생성·참여코드 입장·후기 게시글·댓글·멤버 관리
- 프로필에서 최근 경기 기록·게임별 전적 조회

### 🔐 인증

- 이메일 회원가입/로그인 (JWT 액세스·리프레시 토큰)
- **카카오 소셜 로그인** (OAuth 2.0)
- **게스트 세션** — 회원가입 없이 바로 플레이 가능

---

## 화면 구성

### 온보딩 & 인증

| 메인 (비로그인) | 로그인 | 회원가입 |
| --- | --- | --- |
| ![메인](docs/portfolio/assets/page-home.png) | ![로그인](docs/portfolio/assets/page-login.png) | ![회원가입](docs/portfolio/assets/page-signup.png) |

### 시선 추적 캘리브레이션 — 이 서비스의 핵심 기술

게임 입장 → 웹캠 연결 → 눈 뜬/감은 상태 기록 → 시선 보정 → 준비 완료까지의 실제 흐름입니다.

<div align="center">
<img src="docs/portfolio/assets/calibration.gif" alt="캘리브레이션 및 시선 보정" width="720"/>
</div>

| 웹캠 연결 안내 | 눈 상태 기록 |
| --- | --- |
| ![게임 준비](docs/portfolio/assets/page-game-ready.png) | ![캘리브레이션](docs/portfolio/assets/page-calibration.png) |

### 게임 플레이

| 오락실 (게임 목록) | 게임 상세 (모드 선택) |
| --- | --- |
| ![게임 목록](docs/portfolio/assets/page-games.png) | ![게임 상세](docs/portfolio/assets/page-game-detail-blink.png) |

**Eye Show Speed (눈 깜빡이기)** — 20초 안에 정확하게 깜빡이기

<div align="center"><img src="docs/portfolio/assets/gameplay-blink.gif" alt="눈 깜빡이기 플레이" width="720"/></div>

**Eye Hockey (에어하키)** — 시선으로 패들 조작

<div align="center"><img src="docs/portfolio/assets/gameplay-airhockey.gif" alt="에어하키 플레이" width="720"/></div>

**Eye See (눈싸움)** — 깜빡이면 패배

<div align="center"><img src="docs/portfolio/assets/gameplay-stare.gif" alt="눈싸움 플레이" width="720"/></div>

**Eye Draw (눈으로 그리기)** — 시선 궤적으로 그림을 그리면 AI가 채점

<div align="center"><img src="docs/portfolio/assets/gameplay-draw.gif" alt="눈으로 그리기 플레이" width="720"/></div>

**Blink the Beat (리듬 게임)** — 좌/우 눈 깜빡임으로 노트 입력

<div align="center"><img src="docs/portfolio/assets/gameplay-rhythm.gif" alt="리듬 게임 플레이" width="720"/></div>

| 게임 결과 (완료) | 게임 결과 (실패) |
| --- | --- |
| ![결과 완료](docs/portfolio/assets/page-result-blink.png) | ![결과 실패](docs/portfolio/assets/page-result-rhythm.png) |

### 랭킹 · 길드 · 프로필

| 랭킹 (게임별 탭) | 길드 목록 |
| --- | --- |
| ![랭킹](docs/portfolio/assets/page-ranking.png) | ![길드](docs/portfolio/assets/page-guild.png) |

| 길드 상세 (게시글·멤버) | 프로필 (경기 기록) |
| --- | --- |
| ![길드 상세](docs/portfolio/assets/page-guild-detail.png) | ![프로필](docs/portfolio/assets/page-profile.png) |

### 반응형 (모바일)

| 홈 | 게임 목록 |
| --- | --- |
| <img src="docs/portfolio/assets/mobile-home.png" width="280"/> | <img src="docs/portfolio/assets/mobile-games.png" width="280"/> |

---

## 기술 스택

### 💻 Frontend

**개발 언어**

![TypeScript](https://img.shields.io/badge/typescript-3178C6?style=for-the-badge&logo=typescript&logoColor=white)

**프레임워크 · 라이브러리**

![Vue.js](https://img.shields.io/badge/vue.js_3-4FC08D?style=for-the-badge&logo=vuedotjs&logoColor=white)
![Pinia](https://img.shields.io/badge/pinia-FFD859?style=for-the-badge&logo=vuedotjs&logoColor=35495E)
![Vue Router](https://img.shields.io/badge/vue_router-4FC08D?style=for-the-badge&logo=vuedotjs&logoColor=white)
![SeeSo](https://img.shields.io/badge/SeeSo_SDK-5A4FCF?style=for-the-badge)
![MediaPipe](https://img.shields.io/badge/mediapipe-0097A7?style=for-the-badge&logo=mediapipe&logoColor=white)
![LiveKit](https://img.shields.io/badge/livekit_client-1F8CEB?style=for-the-badge)

**빌드 툴 · 테스트**

![Vite](https://img.shields.io/badge/vite-646CFF?style=for-the-badge&logo=vite&logoColor=white)
![Vitest](https://img.shields.io/badge/vitest-6E9F18?style=for-the-badge&logo=vitest&logoColor=white)
![ESLint](https://img.shields.io/badge/eslint-4B32C3?style=for-the-badge&logo=eslint&logoColor=white)
![Prettier](https://img.shields.io/badge/prettier-F7B93E?style=for-the-badge&logo=prettier&logoColor=black)

### ⚙️ Backend

**개발 언어**

![Java](https://img.shields.io/badge/java_25-007396?style=for-the-badge&logo=openjdk&logoColor=white)

**프레임워크 · 라이브러리**

![Spring Boot](https://img.shields.io/badge/spring_boot_4-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/spring_security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white)
![Spring Data JPA](https://img.shields.io/badge/spring_data_jpa-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![WebSocket](https://img.shields.io/badge/websocket-333333?style=for-the-badge)
![JWT](https://img.shields.io/badge/jwt-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white)
![Kakao](https://img.shields.io/badge/kakao_oauth-FFCD00?style=for-the-badge&logo=kakaotalk&logoColor=black)
![LiveKit](https://img.shields.io/badge/livekit_server-1F8CEB?style=for-the-badge)

**데이터베이스 · 캐시**

![PostgreSQL](https://img.shields.io/badge/postgresql_15-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/redis_7-DC382D?style=for-the-badge&logo=redis&logoColor=white)

**빌드 툴**

![Gradle](https://img.shields.io/badge/gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)

### 🔧 Infra

**클라우드**

![AWS EC2](https://img.shields.io/badge/aws_ec2-FF9900?style=for-the-badge&logo=amazonec2&logoColor=white)

**컨테이너**

![Docker](https://img.shields.io/badge/docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Docker Compose](https://img.shields.io/badge/docker_compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)

**프록시 · 미디어 서버**

![NGINX](https://img.shields.io/badge/nginx-009639?style=for-the-badge&logo=nginx&logoColor=white)
![OpenVidu](https://img.shields.io/badge/openvidu_3_(livekit)-1F8CEB?style=for-the-badge)

**CI/CD**

![Jenkins](https://img.shields.io/badge/jenkins-D24939?style=for-the-badge&logo=jenkins&logoColor=white)
![GitLab](https://img.shields.io/badge/gitlab-FC6D26?style=for-the-badge&logo=gitlab&logoColor=white)
![Mattermost](https://img.shields.io/badge/mattermost-0058CC?style=for-the-badge&logo=mattermost&logoColor=white)

**운영 환경**

![Linux](https://img.shields.io/badge/linux-FCC624?style=for-the-badge&logo=linux&logoColor=black)
![Ubuntu](https://img.shields.io/badge/ubuntu-E95420?style=for-the-badge&logo=ubuntu&logoColor=white)

### 📊 Data Pipeline

![Apache Airflow](https://img.shields.io/badge/apache_airflow-017CEE?style=for-the-badge&logo=apacheairflow&logoColor=white)
![dbt](https://img.shields.io/badge/dbt-FF694B?style=for-the-badge&logo=dbt&logoColor=white)

### 🤝 협업 도구

![Notion](https://img.shields.io/badge/notion-000000?style=for-the-badge&logo=notion&logoColor=white)
![Figma](https://img.shields.io/badge/figma-F24E1E?style=for-the-badge&logo=figma&logoColor=white)
![Mattermost](https://img.shields.io/badge/mattermost-0058CC?style=for-the-badge&logo=mattermost&logoColor=white)

<details>
<summary><b>📌 기술 선택 이유 (펼쳐보기)</b></summary>

#### Frontend

| 기술 | 버전 | 선택 이유 |
| --- | --- | --- |
| Vue 3 + TypeScript | 3.5 | Composition API 기반 컴포넌트 설계, 타입 안정성 |
| Vite / Vitest | 7.x | 빠른 개발 서버·빌드, 컴포넌트 단위 테스트 |
| Pinia | 4.x | 인증·게임 상태 등 전역 상태 관리 |
| SeeSo SDK + MediaPipe | - | 웹캠 기반 시선 추적. SeeSo를 기본으로 하되 라이선스 없는 환경에서 MediaPipe 폴백 — 어떤 환경에서도 동작 보장 |
| livekit-client | 2.x | 친구 대결 시 상대 화면을 보여주는 WebRTC 미디어 스트림 |

#### Backend

| 기술 | 버전 | 선택 이유 |
| --- | --- | --- |
| Java / Spring Boot | 25 / 4.1 | WebMVC + WebSocket + Security를 한 프레임워크에서, 최신 버전 활용 |
| Spring Data JPA + PostgreSQL | 15 | 사용자·게임 결과·길드 등 관계형 도메인 모델링 |
| Spring Data Redis | 7 | 게스트 세션, 매칭 큐, 대기방 상태 등 휘발성 실시간 상태 저장 |
| JWT (jjwt) + Kakao OAuth | - | 무상태 인증 + 소셜 로그인, 게스트 세션까지 3가지 인증 경로 통합 |
| livekit-server SDK | - | WebRTC 미디어 토큰 발급 (OpenVidu 3 연동) |

#### Infra & Data

| 기술 | 용도 |
| --- | --- |
| AWS EC2 + Docker Compose | frontend·backend·PostgreSQL·Redis 컨테이너 스택 운영 |
| nginx | TLS 종료 + 리버스 프록시 (`/` → frontend, `/api`·WebSocket → backend) |
| OpenVidu 3 (LiveKit) | WebRTC SFU 미디어 서버 (별도 컨테이너 스택, wss 7443) |
| Jenkins | GitLab main 병합 시 자동 빌드·배포 (CI/CD), Mattermost 알림 |
| Airflow + dbt | 게임 결과 배치 ELT — 운영 DB를 `analytics` 스키마로 집계(유저×게임 통계, 일별 지표)하고 점수 사본과 원본 JSONB를 대조하는 감사(reconcile) 파이프라인 |

</details>

---

## 시스템 아키텍처

```mermaid
flowchart LR
    subgraph Client["브라우저 (웹캠)"]
        FE["Vue 3 SPA<br/>SeeSo / MediaPipe 시선 추적"]
    end

    subgraph EC2["AWS EC2 (Docker)"]
        NGINX["nginx<br/>TLS · 리버스 프록시"]
        BE["Spring Boot 4<br/>REST + WebSocket"]
        PG[("PostgreSQL 15")]
        RD[("Redis 7")]
        OV["OpenVidu 3 (LiveKit)<br/>WebRTC SFU"]
        JK["Jenkins CI/CD"]
        DBT["Airflow + dbt<br/>배치 ELT"]
    end

    KAKAO["Kakao OAuth"]
    GMS["GPT-4o<br/>(그림 채점)"]
    GITLAB["GitLab"]

    FE -- "HTTPS /api" --> NGINX --> BE
    FE -- "WSS (매칭·대기방·인게임)" --> NGINX
    FE -- "WSS 7443 (미디어)" --> OV
    BE --> PG
    BE --> RD
    BE -- "토큰 발급" --> OV
    BE --> KAKAO
    BE --> GMS
    GITLAB -- "main 병합 웹훅" --> JK -- "빌드·배포" --> BE
    PG --> DBT -- "analytics 스키마" --> PG
```

### ERD

<div align="center">
<img src="docs/portfolio/assets/erd.png" alt="ERD" width="900"/>
</div>

- 운영 테이블: users, social_accounts, guest_sessions, games, games_results, participants, waiting_rooms, matchmaking_entries, groups, group_posts/comments/members 등
- 분석 테이블(dbt 생성): fact_game_play, agg_user_game_stats, game_daily_stats, reconcile_score

---

## 프로젝트 폴더 구조

```
.
├── frontend/          # Vue 3 + TypeScript SPA
├── backend/           # Spring Boot 4 (Java 25)
├── pipeline/          # Airflow DAG + dbt 모델 (배치 ELT)
├── exec/              # 빌드·배포 매뉴얼, DB 덤프, 시연 시나리오
├── docs/              # 컨벤션 문서 + 포트폴리오 산출물
├── compose.dev.yml    # 로컬 개발용 Docker Compose
├── compose.prod.yml   # 운영 배포용 Docker Compose
└── Jenkinsfile        # CI/CD 파이프라인
```

### Front-end

<details>
<summary>FE</summary>

```
FrontEnd
├─ api
│  ├─ auth.ts
│  ├─ authTokens.ts
│  ├─ draw.ts
│  ├─ game.ts
│  ├─ gameResult.ts
│  ├─ group.ts
│  ├─ guestSession.ts
│  ├─ http.ts
│  ├─ identity.ts
│  ├─ jwt.ts
│  ├─ match.ts
│  ├─ ranking.ts
│  ├─ user.ts
│  └─ waitingRoom.ts
├─ assets
│  ├─ images/  (46 files)
│  ├─ styles/  (3 files)
│  ├─ hero.png
│  ├─ vite.svg
│  └─ vue.svg
├─ components
│  ├─ auth
│  │  └─ AuthDialog.vue
│  ├─ common
│  │  ├─ PageHeader.vue
│  │  ├─ PolicyDialog.vue
│  │  └─ SegmentedTabs.vue
│  ├─ feedback
│  │  └─ ToastMessage.vue
│  ├─ games
│  │  ├─ DrawPromptIcon.vue
│  │  ├─ GameCard.vue
│  │  ├─ GameComingSoonCard.vue
│  │  ├─ GameMediaControls.vue
│  │  ├─ GamePlayShell.vue
│  │  ├─ GameResultShell.vue
│  │  ├─ GameRoomDialog.vue
│  │  └─ GameStartCountdownModal.vue
│  ├─ groups
│  │  ├─ CommunityDialog.vue
│  │  └─ CommunityGroupCard.vue
│  ├─ home
│  │  └─ WeeklyRankingCard.vue
│  ├─ layout
│  │  ├─ AppFooter.vue
│  │  ├─ AppHeader.vue
│  │  ├─ AppLayout.vue
│  │  ├─ PrimaryNavigation.vue
│  │  └─ ProfileMenu.vue
│  └─ ranking
│     └─ RankingList.vue
├─ composables
│  ├─ useEyeTracking.ts
│  ├─ useGameResultSubmission.ts
│  ├─ useGameSessionSocket.ts
│  ├─ useInviteRoomLifecycle.ts
│  ├─ useLiveKitRoom.ts
│  ├─ useLocalCamera.ts
│  ├─ useMatchSocket.ts
│  ├─ useRandomRematchLifecycle.ts
│  ├─ useSeeSoGaze.ts
│  ├─ useToast.ts
│  └─ useWaitingRoomSocket.ts
├─ lib
│  ├─ eye-tracking
│  │  ├─ config.ts
│  │  ├─ eye-engine.ts
│  │  ├─ gaze-calibration.ts
│  │  ├─ mediapipe-adapter.ts
│  │  └─ seeso-gaze-provider.ts
│  ├─ games
│  │  ├─ air-hockey-core.ts
│  │  ├─ audio-beatmap.ts
│  │  ├─ blink-core.ts
│  │  ├─ draw-core.ts
│  │  ├─ rhythm-core.ts
│  │  └─ stare-core.ts
│  └─ sound
│     └─ calibration-sound.ts
├─ mocks
│  ├─ community.ts
│  ├─ footer.ts
│  ├─ game-details.ts
│  ├─ gameplay.ts
│  ├─ home.ts
│  ├─ pages.ts
│  └─ profile.ts
├─ pages
│  ├─ AccountPage.vue
│  ├─ CommunityDetailPage.vue
│  ├─ CommunityPage.vue
│  ├─ GameDetailPage.vue
│  ├─ GamePlayPage.vue
│  ├─ GameReadyPage.vue
│  ├─ GameResultPage.vue
│  ├─ GamesPage.vue
│  ├─ HomePage.vue
│  ├─ KakaoCallbackPage.vue
│  ├─ PendingPage.vue
│  ├─ ProfilePage.vue
│  └─ RankingPage.vue
├─ router
│  └─ index.ts
├─ stores
│  ├─ auth.ts
│  ├─ calibration.ts
│  ├─ lastGameResult.ts
│  ├─ mediaSession.ts
│  └─ mediaSettings.ts
├─ types
│  ├─ auth.ts
│  ├─ community.ts
│  ├─ footer.ts
│  ├─ game-detail.ts
│  ├─ gameplay.ts
│  ├─ gameResult.ts
│  ├─ gameSession.ts
│  ├─ home.ts
│  ├─ matchmaking.ts
│  ├─ media.ts
│  ├─ pages.ts
│  ├─ profile.ts
│  ├─ seeso.d.ts
│  └─ waitingRoom.ts
├─ utils
│  ├─ password.ts
│  └─ soloPlayEntry.ts
├─ App.vue
├─ main.ts
├─ style.css
└─ vite-env.d.ts
```

</details>

### Back-end

<details>
<summary>BE</summary>

```
BackEnd (org.ssafy.b102.backend)
├─ auth
│  ├─ controller
│  ├─ dto
│  ├─ exception
│  ├─ kakao
│  ├─ repository
│  ├─ service
│  └─ AuthSuccessCode.java
├─ game
│  ├─ config
│  ├─ controller
│  ├─ dto
│  ├─ entity
│  ├─ exception
│  ├─ repository
│  ├─ service
│  └─ GameSuccessCode.java
├─ gameresult
│  ├─ controller
│  ├─ dto
│  ├─ entity
│  ├─ event
│  ├─ exception
│  ├─ repository
│  ├─ service
│  └─ GameResultSuccessCode.java
├─ gamesession
│  └─ websocket
├─ global
│  ├─ common
│  ├─ config
│  ├─ error
│  ├─ openvidu
│  ├─ security
│  └─ validation
├─ group
│  ├─ controller
│  ├─ dto
│  ├─ entity
│  ├─ exception
│  ├─ repository
│  ├─ service
│  ├─ support
│  └─ GroupSuccessCode.java
├─ guest
│  ├─ config
│  ├─ controller
│  ├─ dto
│  ├─ entity
│  ├─ exception
│  ├─ repository
│  ├─ service
│  ├─ support
│  └─ GuestSessionSuccessCode.java
├─ matchmaking
│  ├─ controller
│  ├─ dto
│  ├─ entity
│  ├─ exception
│  ├─ repository
│  ├─ service
│  ├─ support
│  ├─ websocket
│  └─ MatchmakingSuccessCode.java
├─ ping
│  ├─ controller
│  ├─ dto
│  ├─ exception
│  └─ service
├─ ranking
│  ├─ controller
│  ├─ dto
│  ├─ entity
│  ├─ event
│  ├─ exception
│  ├─ repository
│  ├─ service
│  ├─ support
│  └─ RankingSuccessCode.java
├─ user
│  ├─ controller
│  ├─ dto
│  ├─ entity
│  ├─ enums
│  ├─ exception
│  ├─ repository
│  ├─ service
│  ├─ util
│  └─ UserSuccessCode.java
├─ waitingroom
│  ├─ config
│  ├─ controller
│  ├─ dto
│  ├─ entity
│  ├─ exception
│  ├─ repository
│  ├─ service
│  ├─ support
│  ├─ websocket
│  └─ WaitingRoomSuccessCode.java
└─ BackendApplication.java
```

</details>

### Data Pipeline

<details>
<summary>Pipeline</summary>

```
Pipeline
├─ airflow
│  ├─ dags
│  │  └─ game_analytics_pipeline.py
│  ├─ docker-compose.airflow.yml
│  ├─ Dockerfile
│  └─ README.md
├─ dbt
│  ├─ models
│  │  ├─ marts
│  │  │  ├─ _marts.yml
│  │  │  ├─ agg_user_game_stats.sql
│  │  │  ├─ fact_game_play.sql
│  │  │  ├─ game_daily_stats.sql
│  │  │  └─ reconcile_score.sql
│  │  └─ staging
│  │     ├─ _sources.yml
│  │     ├─ stg_game_results.sql
│  │     └─ stg_participants.sql
│  ├─ .gitignore
│  ├─ dbt_project.yml
│  └─ profiles.yml
├─ sample
│  └─ sample_game_data.sql
└─ README.md
```

</details>

---

## 팀원 소개

| 이름 | 역할 | 담당 | GitHub |
| --- | --- | --- | --- |
| 정재현 | 팀장 · Infra · Backend | EC2·Docker·Jenkins CI/CD 인프라 구축, 백엔드 개발 | [@pastjung](https://github.com/pastjung) |
| 장미지 | Backend | 백엔드 API 개발 | [@assokk](https://github.com/assokk) |
| 김진광 | Backend · Data | 백엔드 개발, Airflow·dbt 데이터 파이프라인 | [@Kim-jin-gwang](https://github.com/Kim-jin-gwang) |
| **김보경** | **Frontend** | **프론트엔드 개발** | [@BooGyya](https://github.com/BooGyya) |
| 김태은 | Frontend | 프론트엔드 개발 | [@allieun](https://github.com/allieun) |
| 박호진 | AI | 시선 추적·AI 기능 | - |

---

## 협업 방식

- **브랜치 전략** — Git-Flow 응용: `main`(배포) / `dev`(통합) / `feat|fix|docs/*`(작업) / `release/*` / `hotfix/*`. 모든 병합은 PR 기반, 팀원 전원 approve 원칙.
- **커밋 컨벤션** — Conventional Commits (`feat:`, `fix:`, `docs:` …), 목적 단위 원자적 커밋. 릴리즈는 `release/1.0.x` 브랜치로 버전 관리 (최종 1.0.10).
- **코드 리뷰** — PR 템플릿·라벨 체계 + **Gemini AI 자동 코드 리뷰** 워크플로우 병행.
- **CI/CD** — GitLab `main` 병합 → Jenkins 웹훅 트리거 → Docker 이미지 빌드 → 운영 스택 배포 → **Mattermost 알림**.
- **문서화** — 코드·브랜치·커밋·PR·라벨·설정 컨벤션을 전부 `docs/`에 명문화하고 AI 에이전트 스킬로도 자동화 (`.claude/skills/`).

---

## 프로젝트 산출물

| 산출물 | 위치 |
| --- | --- |
| 기능 정의서 | [docs/portfolio/기능정의서.pdf](docs/portfolio/기능정의서.pdf) |
| API 명세서 | [docs/portfolio/API명세서.csv](docs/portfolio/API명세서.csv) |
| ERD | [docs/portfolio/assets/erd.png](docs/portfolio/assets/erd.png) |
| 화면 설계 (Figma) | [Figma 링크](https://www.figma.com/design/3wzhT210350qYgt4mw90lm/SSAFY-1st-PJT?node-id=0-1&p=f&t=iytsHui8eEIgpOlG-0) |
| 빌드·배포 매뉴얼 | [exec/01_빌드_및_배포_매뉴얼.md](exec/01_빌드_및_배포_매뉴얼.md) |
| 시연 시나리오 | [exec/04_시연_시나리오.md](exec/04_시연_시나리오.md) |

### 화면 설계 과정

Figma에서 1차 로우파이 와이어프레임으로 화면 흐름을 확정한 뒤, 브랜드 룩을 입힌 2차 와이어프레임으로 발전시켜 실제 구현의 기준으로 삼았습니다.

| 1차 와이어프레임 (화면 흐름 설계) | 2차 와이어프레임 (브랜드 적용) |
| --- | --- |
| ![1차 와이어프레임](docs/portfolio/assets/wireframe-v1.jpg) | ![2차 와이어프레임](docs/portfolio/assets/wireframe-v2.jpg) |

## 프로젝트 결과물

- **영상 포트폴리오** — [docs/portfolio/영상포트폴리오_B102.mp4](docs/portfolio/영상포트폴리오_B102.mp4) (서비스 소개 영상)
- **배포 서비스** — https://eyedontcare.shop (프로젝트 종료로 서버 회수됨, 위 화면 캡쳐·GIF가 실제 배포 환경에서 촬영한 기록입니다)

---

<div align="center">

**eye dont care** — SSAFY 15기 공통 프로젝트 B102

</div>
