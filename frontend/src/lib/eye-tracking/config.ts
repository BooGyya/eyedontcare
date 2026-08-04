/**
 * 시선/눈 인식 엔진 기본 설정값.
 *
 * `ai_game` 프로토타입(`config.js`)에서 그대로 가져온 값이다. 실제 사용자 테스트로 튜닝된
 * 숫자들이므로 임의로 바꾸지 않는다 — 바꿀 필요가 있으면 캘리브레이션 단계(눈 뜬/감은 샘플 기록)로
 * 사용자별 프로필을 덮어써서 조정한다.
 */

/**
 * MediaPipe `@mediapipe/tasks-vision` 패키지 버전.
 *
 * 프로토타입은 "latest"를 썼지만, 프로덕션에서는 예고 없이 새 버전이 배포되면서 동작이 바뀌는 걸
 * 막기 위해 특정 버전으로 고정해야 한다. 배포 전 npm에서 최신 안정 버전을 확인하고 갱신할 것.
 */
export const MEDIAPIPE_VERSION = '0.10.14'

export const MODEL_URL =
  'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task'

export interface EyeDetectionConfig {
  /** 이 시간(ms)보다 짧게 감았다 뜨면 깜빡임으로 인정하지 않는다(노이즈 제거). */
  minBlinkDurationMs: number
  /** 이 시간(ms)보다 길게 감고 있으면 "깜빡임"이 아니라 LONG_CLOSE로 분류한다. */
  maxBlinkDurationMs: number
  /** 이 시간(ms) 이내로 짧게 감았다 뜨면 FAST_BLINK로 분류한다. */
  fastBlinkThresholdMs: number
  /** 이 시간(ms) 이상 계속 감고 있으면 LONG_CLOSE 이벤트를 발생시킨다. */
  longCloseThresholdMs: number
  /** 직전 깜빡임 이후 이 시간(ms) 이내에 다시 감으면 DOUBLE_BLINK로 분류한다. */
  doubleBlinkIntervalMs: number
  /** 같은 종류 이벤트를 이 시간(ms) 이내에 중복 발생시키지 않는다. */
  eventCooldownMs: number
  /** 얼굴 인식이 이 시간(ms) 이상 끊기면 FACE_LOST 이벤트를 발생시킨다. */
  faceLostThresholdMs: number
  /** 이 값 미만의 프레임 신뢰도는 눈 상태 판정에 반영하지 않는다. */
  confidenceThreshold: number
  /** 화면 중심 대비 이 값(0~1 정규화) 이상 시선이 움직여야 GAZE_MOVE 이벤트로 인정한다. */
  gazeMoveThreshold: number
  /** GAZE_MOVE 이벤트의 최소 재발생 간격(ms). */
  gazeMoveCooldownMs: number
}

export const DEFAULT_EYE_DETECTION_CONFIG: EyeDetectionConfig = Object.freeze({
  minBlinkDurationMs: 70,
  maxBlinkDurationMs: 520,
  fastBlinkThresholdMs: 130,
  longCloseThresholdMs: 850,
  doubleBlinkIntervalMs: 430,
  eventCooldownMs: 120,
  faceLostThresholdMs: 650,
  confidenceThreshold: 0.52,
  gazeMoveThreshold: 0.1,
  gazeMoveCooldownMs: 240,
})

export interface EyeCalibrationProfile {
  userId: string
  /** 사용자가 눈을 자연스럽게 뜨고 있을 때의 왼쪽 눈 종횡비(EAR) 기준값. */
  openEyeRatioLeft: number
  openEyeRatioRight: number
  /** 사용자가 눈을 감았을 때의 왼쪽 눈 종횡비(EAR) 기준값. */
  closedEyeRatioLeft: number
  closedEyeRatioRight: number
  blinkDurationAverageMs: number
  cameraWidth: number
  cameraHeight: number
  createdAt: string
}

export const DEFAULT_CALIBRATION_PROFILE: EyeCalibrationProfile = Object.freeze(
  {
    userId: 'local-debug',
    openEyeRatioLeft: 0.22,
    openEyeRatioRight: 0.22,
    closedEyeRatioLeft: 0.08,
    closedEyeRatioRight: 0.08,
    blinkDurationAverageMs: 180,
    cameraWidth: 1280,
    cameraHeight: 720,
    createdAt: 'default',
  },
)
