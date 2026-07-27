# Frontend

Vue 3, Vite, TypeScript 기반의 프런트엔드 개발 환경입니다. 현재는 화면과 API를 포함하지 않은 기반 구성만 제공합니다.

## Requirements

- Node.js 22 이상, 25 미만
- npm 10 이상

## Commands

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

환경변수는 `.env.example`을 복사해 `.env.local`에서 설정합니다.
