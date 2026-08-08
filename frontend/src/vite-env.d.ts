/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 카카오 REST API 키(인가 요청용 client_id). */
  readonly VITE_KAKAO_CLIENT_ID?: string
  /** 카카오 로그인 리다이렉트 URI. 백엔드 KAKAO_REDIRECT_URI와 일치해야 한다. */
  readonly VITE_KAKAO_REDIRECT_URI?: string
  /**
   * SeeSo(Eyedid) 시선 추적 SDK 라이선스 키. console.seeso.io에서 발급하며 등록된 도메인에서만
   * 동작한다. 비워 두면 SeeSo를 시도하지 않고 기존 MediaPipe 시선 추정으로 폴백한다.
   */
  readonly VITE_SEESO_LICENSE_KEY?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
