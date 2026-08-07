import { onScopeDispose, ref, shallowRef } from 'vue'
import {
  SeeSoGazeProvider,
  SEESO_TRACKING_SUCCESS,
  type SeeSoGazeInfo,
} from '../lib/eye-tracking/seeso-gaze-provider'
import type { Point } from '../lib/eye-tracking/gaze-calibration'

/**
 * SeeSo(Eyedid) 시선 추적을 앱에서 쓰기 위한 컴포저블.
 *
 * ## 왜 필요한가
 * MediaPipe 기반 시선 추정은 홍채 위치로 화면 좌표를 유추하는 방식이라 정확도가 낮다. SeeSo는
 * 시선 지점 추정 전용 SDK라 훨씬 정확하고, 연동 전 프로토타입의 그림그리기도 이것을 기본으로
 * 썼다. 이 컴포저블은 SeeSo를 **원시 시선(raw gaze) 공급원으로만** 제공하고, 그 뒤의 9점 개인
 * 보정({@link GazeCalibrator})과 커서 평활은 기존 파이프라인을 그대로 쓴다.
 *
 * ## 폴백
 * 라이선스 키가 없거나(환경변수 미설정), 현재 도메인에 라이선스가 등록되지 않았거나, 카메라를
 * 열 수 없으면 {@link start}가 `false`를 돌려준다. 호출부는 이 경우 기존 MediaPipe 시선을 그대로
 * 쓰면 되고, 앱이 깨지지 않는다.
 *
 * ## 좌표계
 * SeeSo는 **브라우저 뷰포트 픽셀 좌표**를 준다. 이걸 뷰포트 크기로 나눠 0~1로 정규화한 값을
 * {@link viewportGaze}에 담는다.
 *
 * 왜 특정 엘리먼트가 아니라 뷰포트 기준인가 — 시선 오차는 눈·카메라·모니터의 기하학적 성질이라
 * 화면 전체를 하나의 좌표계로 두고 보정해야 보정 화면과 게임 화면의 크기가 달라도 같은 보정값을
 * 쓸 수 있다. 각 게임은 마지막에 `viewportPointToElement()`로 자기 영역 좌표로 바꾼다.
 * (자세한 배경은 `gaze-calibration.ts`의 해당 함수 주석 참고.)
 */

/** 라이선스 키는 저장소에 커밋하지 않는다 — `frontend/.env`에 두고 환경변수로 주입한다. */
const LICENSE_KEY = import.meta.env.VITE_SEESO_LICENSE_KEY as string | undefined

/** SeeSo 자체 보정(1단계) 결과를 저장해 재보정 없이 복원하기 위한 키. */
const SEESO_CALIBRATION_STORAGE_KEY = 'eyegame.seeso.calibration.v1'

/**
 * 모니터 크기·얼굴 거리 기본값. SeeSo가 시선을 화면 좌표로 환산할 때 쓴다. 정확히 몰라도 2단계
 * 개인 보정이 오차를 흡수하므로, 일반적인 데스크톱 사용 환경 기준값을 쓴다.
 */
const DEFAULT_MONITOR_SIZE_INCH = 24
const DEFAULT_FACE_DISTANCE_CM = 50

/**
 * 기준 엘리먼트 밖으로 얼마나 벗어난 시선까지 받아줄지(비율).
 *
 * 2단계 개인 보정이 적용되기 전에는 좁게 잡아 엉뚱한 값이 섞이지 않게 하고, 보정 후에는 넓게
 * 잡는다 — 보정이 화면 안쪽으로 끌어당겨 주므로 raw가 약간 밖으로 나가도 버리면 손해다.
 * (프로토타입도 0.15 → 0.45로 같은 전환을 했다.)
 */
export const SEESO_MARGIN_BEFORE_CALIBRATION = 0.15
export const SEESO_MARGIN_AFTER_CALIBRATION = 0.45

export function useSeeSoGaze() {
  /** 라이선스 키가 설정되어 있는지. false면 SeeSo를 아예 시도하지 않는다. */
  const isConfigured = Boolean(LICENSE_KEY)
  const isRunning = ref(false)
  const isStarting = ref(false)
  /** 시작에 실패한 이유(사용자 안내용). 실패해도 앱은 MediaPipe로 계속 동작한다. */
  const error = ref<string | null>(null)
  /** 뷰포트 기준으로 정규화한 최신 시선(0~1). 범위를 크게 벗어나면 null. */
  const viewportGaze = shallowRef<Point | null>(null)

  let provider: SeeSoGazeProvider | null = null
  let margin = SEESO_MARGIN_BEFORE_CALIBRATION
  /**
   * `stop()`이 호출됐는지. SDK 초기화는 WASM 로딩 때문에 수 초가 걸리는데, 그 사이 사용자가
   * 화면을 떠나면 `stop()`이 먼저 실행되고 초기화가 나중에 끝난다. 이 플래그가 없으면 이미 떠난
   * 화면을 위해 SeeSo가 계속 살아남아 카메라를 붙잡고 heartbeat를 폴링한다.
   */
  let disposed = false

  /** 2단계 개인 보정 적용 여부에 따라 허용 범위를 넓히거나 좁힌다. */
  function setMargin(next: number): void {
    margin = next
  }

  function handleGaze(gaze: SeeSoGazeInfo): void {
    // ⚠️ 먼저 유한한 수인지 확인해야 한다. SeeSo는 시선을 놓치면 좌표로 NaN을 내보내는데,
    // NaN은 어떤 비교 연산도 전부 false라 아래 범위 검사(`x < -margin` 등)를 그대로 통과한다.
    // 그러면 NaN이 그대로 흘러가 커서가 사라지고, 더 나쁘게는 보정 표본으로 기록되어 보정
    // 프로필 전체가 NaN으로 오염된다(그 뒤로는 무엇을 해도 커서가 안 나온다).
    if (!Number.isFinite(gaze.x) || !Number.isFinite(gaze.y)) {
      viewportGaze.value = null
      return
    }
    // 추적 품질이 SUCCESS(0)가 아니면 좌표가 있어도 신뢰할 수 없다. 다른 값 체계를 쓰는 SDK
    // 버전에서 모든 프레임이 막히지 않도록, 숫자가 아닐 때는 통과시킨다.
    if (
      typeof gaze.trackingState === 'number' &&
      gaze.trackingState !== SEESO_TRACKING_SUCCESS
    ) {
      viewportGaze.value = null
      return
    }

    const viewportWidth = globalThis.window.innerWidth || 1
    const viewportHeight = globalThis.window.innerHeight || 1
    const x = gaze.x / viewportWidth
    const y = gaze.y / viewportHeight
    if (x < -margin || x > 1 + margin || y < -margin || y > 1 + margin) {
      viewportGaze.value = null
      return
    }
    viewportGaze.value = { x, y, confidence: 1 }
  }

  /**
   * SeeSo를 시작한다. 성공하면 true.
   *
   * 실패(키 없음/도메인 미등록/카메라 점유 등)해도 예외를 던지지 않고 false를 돌려준다 —
   * 호출부가 조용히 MediaPipe로 폴백할 수 있게 하기 위함이다.
   */
  async function start(): Promise<boolean> {
    if (isRunning.value) return true
    if (!LICENSE_KEY) {
      error.value = 'SeeSo 라이선스 키가 설정되지 않았습니다.'
      return false
    }
    if (isStarting.value) return false

    isStarting.value = true
    error.value = null
    disposed = false
    try {
      provider = new SeeSoGazeProvider({
        licenseKey: LICENSE_KEY,
        monitorSizeInch: DEFAULT_MONITOR_SIZE_INCH,
        faceDistanceCm: DEFAULT_FACE_DISTANCE_CM,
        onGaze: handleGaze,
      })
      await provider.start()
      // 초기화를 기다리는 동안 화면을 떠났다면(stop이 먼저 실행됨) 되돌린다. 프로바이더도
      // 자체적으로 늦은 콜백을 막지만, 여기서도 상태를 정리해 둔다.
      if (disposed) {
        provider.stop()
        provider = null
        return false
      }
      await restoreSavedCalibration()
      if (disposed) {
        provider.stop()
        provider = null
        return false
      }
      isRunning.value = true
      return true
    } catch (caught) {
      error.value =
        caught instanceof Error
          ? caught.message
          : 'SeeSo를 시작하지 못했습니다.'
      // 초기화 도중 실패해도 부분적으로 살아 있을 수 있으므로 반드시 정리한다.
      provider?.stop()
      provider = null
      return false
    } finally {
      isStarting.value = false
    }
  }

  function stop(): void {
    disposed = true
    provider?.stop()
    provider = null
    isRunning.value = false
    viewportGaze.value = null
  }

  /**
   * SeeSo 자체 보정(1단계, 5점). SDK가 지정한 점 위치를 콜백으로 알려주므로 화면에 표시해야 한다.
   * 성공하면 결과를 localStorage에 저장해 다음 세션에서 재보정 없이 쓴다.
   */
  async function calibrate(handlers: {
    onPoint: (x: number, y: number) => void
    onProgress: (progress: number) => void
  }): Promise<boolean> {
    if (!provider || !isRunning.value) return false
    try {
      const data = await provider.calibrate({
        pointCount: 5,
        onPoint: handlers.onPoint,
        onProgress: handlers.onProgress,
      })
      try {
        globalThis.localStorage?.setItem(SEESO_CALIBRATION_STORAGE_KEY, data)
      } catch {
        // 저장 실패(용량 초과·프라이빗 모드 등)는 보정 자체를 무효화하지 않는다.
      }
      return true
    } catch (caught) {
      error.value =
        caught instanceof Error ? caught.message : 'SeeSo 보정에 실패했습니다.'
      return false
    }
  }

  async function restoreSavedCalibration(): Promise<void> {
    if (!provider) return
    let saved: string | null
    try {
      saved = globalThis.localStorage?.getItem(SEESO_CALIBRATION_STORAGE_KEY)
    } catch {
      return
    }
    if (!saved) return
    try {
      await provider.restoreCalibration(saved)
    } catch {
      // 저장된 데이터가 손상됐거나 SDK 버전이 바뀐 경우 — 새로 보정하면 되므로 무시한다.
    }
  }

  onScopeDispose(stop)

  return {
    isConfigured,
    isRunning,
    isStarting,
    error,
    viewportGaze,
    setMargin,
    start,
    stop,
    calibrate,
  }
}
