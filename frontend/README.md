# Frontend

Vue 3, Vite, TypeScript 기반의 프런트엔드입니다. 현재 화면 데이터는
`src/mocks/`를 사용하며 실제 API 연동은 순차적으로 진행합니다.

## Requirements

- Node.js 22 이상, 25 미만
- npm 10 이상

## Local development

전체 로컬 서비스는 저장소 루트에서 실행합니다.

```powershell
Copy-Item .env.example .env
docker compose -f compose.dev.yml up --build
```

브라우저 접속 주소는 `http://localhost:3102`입니다. 프런트엔드 HTTP
클라이언트는 `/api`, WebSocket 클라이언트는 `/ws` 경로를 사용합니다.
`backend:8080`은 Compose 네트워크 내부에서만 사용하는 주소입니다.

## Frontend checks

다음 명령은 `frontend/`에서 실행합니다.

```bash
npm install
npm run dev
npm run lint
npm run format:check
npm run type-check
npm run test
npm run build
```

## Directory structure

```text
src/
  pages/       # Router 단위 화면
  router/      # Vue Router 설정
  stores/      # Pinia store
  components/  # 재사용 가능한 공용 UI
  features/    # 도메인 기능별 UI와 로직
  composables/ # 상태 및 동작 재사용
  utils/       # 순수 유틸리티 함수
```
