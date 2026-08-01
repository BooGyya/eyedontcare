/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 카카오 REST API 키(인가 요청용 client_id). */
  readonly VITE_KAKAO_CLIENT_ID?: string
  /** 카카오 로그인 리다이렉트 URI. 백엔드 KAKAO_REDIRECT_URI와 일치해야 한다. */
  readonly VITE_KAKAO_REDIRECT_URI?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
