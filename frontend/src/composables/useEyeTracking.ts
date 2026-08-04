import { computed, onScopeDispose, ref } from 'vue'
import { useLocalCamera } from './useLocalCamera'
import {
  loadFaceLandmarker,
  type FaceLandmarker,
} from '../lib/eye-tracking/mediapipe-adapter'
import {
  EyeInteractionEngine,
  type CombinedEyeState,
  type EyeEvent,
  type EyeFrameResult,
  type EyeState,
} from '../lib/eye-tracking/eye-engine'
import {
  DEFAULT_CALIBRATION_PROFILE,
  type EyeCalibrationProfile,
} from '../lib/eye-tracking/config'
import {
  CALIBRATION_TARGETS,
  GazeCalibrator,
  GazeSmoother,
  type CalibrationEvaluation,
  type GazeCalibrationProfile,
  type Point,
} from '../lib/eye-tracking/gaze-calibration'

export interface EyeSampleResult {
  success: boolean
  sampleCount: number
  /**
   * 실패 사유. `no_face`는 얼굴 인식 부족, `eyes_open`/`eyes_closed`는 요청한 단계와
   * 실제 눈 상태가 반대여서 거절된 경우(감아야 하는데 떴거나, 떠야 하는데 감음).
   */
  reason?: 'no_face' | 'eyes_open' | 'eyes_closed'
}

/**
 * 눈 감기 단계에서 측정한 감은 ratio가 (본인의) 뜬 ratio 대비 이 비율보다 작아야
 * "확실히 감았다"고 본다. 눈을 뜬 채로 감기 단계를 누르면 두 값이 비슷해져 거절된다.
 */
const CLOSED_EYE_SEPARATION = 0.7

export interface GazeCalibrationResult {
  profile: GazeCalibrationProfile
  evaluation: CalibrationEvaluation | null
}

/**
 * 웹캠 + MediaPipe Face Landmarker 기반 눈/시선 인식 컴포저블.
 *
 * `ai_game` 프로토타입의 `mediapipe-adapter.js` + `eye-engine.js` + `gaze-calibration.js`를 하나로
 * 묶어 Vue 컴포넌트가 쓰기 편한 형태로 감싼다. 카메라 자체(getUserMedia)는 기존
 * {@link useLocalCamera}를 그대로 재사용해 로직 중복을 피한다.
 *
 * 사용 순서:
 * 1. `videoRef`를 `<video>` 엘리먼트에 바인딩
 * 2. `start()` 호출 → 카메라 권한 요청 + MediaPipe 모델 로드(CDN, 수 초 소요)를 동시에 진행
 * 3. (캘리브레이션) `recordEyeSample('open')` → `recordEyeSample('closed')`로 눈 뜬/감은 기준을 기록
 * 4. (시선 좌표가 필요한 게임만) `beginGazeCalibration()` → 화면의 각 타깃을 보는 동안
 *    `addGazeCalibrationSample(target)` → `finishGazeCalibration()`
 * 5. 게임 중에는 `combinedState`/`lastEvent`/`screenGaze` 등 reactive 값을 그대로 구독
 * 6. 화면을 벗어나면 `stop()` (컴포넌트 unmount 시 자동으로도 호출됨)
 */
export function useEyeTracking() {
  const camera = useLocalCamera()
  const engine = new EyeInteractionEngine()
  const gazeCalibrator = new GazeCalibrator()
  const gazeSmoother = new GazeSmoother()

  const isLoadingModel = ref(false)
  const isActive = ref(false)
  const modelError = ref<string | null>(null)

  const faceDetected = ref(false)
  const leftEyeState = ref<EyeState>('NOT_DETECTED')
  const rightEyeState = ref<EyeState>('NOT_DETECTED')
  const combinedState = ref<CombinedEyeState>('UNKNOWN')
  const leftRatio = ref(0)
  const rightRatio = ref(0)
  const confidence = ref(0)
  const fps = ref(0)
  /** 눈 랜드마크만으로 뽑은 원시(raw) 시선 좌표(0~1, 눈 안에서의 상대 위치). 화면 좌표가 아니다. */
  const rawGaze = ref<Point | null>(null)
  /** `finishGazeCalibration` 이전에는 rawGaze를 clamp만 한 값과 같다. 이후엔 보정된 화면 좌표(0~1). */
  const screenGaze = ref<Point | null>(null)
  const lastEvent = ref<EyeEvent | null>(null)
  const eventSequence = ref(0)

  let faceLandmarker: FaceLandmarker | null = null
  let rafHandle: number | null = null
  let lastVideoTime = -1
  let lastFrameAt = Number.NEGATIVE_INFINITY
  const eventListeners = new Set<(event: EyeEvent) => void>()

  /** BLINK/DOUBLE_BLINK/LEFT_WINK 등 이산 이벤트를 구독한다(눈싸움/깜빡이기/리듬 게임에서 사용). */
  function onEyeEvent(handler: (event: EyeEvent) => void): () => void {
    eventListeners.add(handler)
    return () => eventListeners.delete(handler)
  }

  function loop(now: number): void {
    rafHandle = globalThis.requestAnimationFrame(loop)
    const video = camera.videoRef.value
    if (!faceLandmarker || !video) return
    if (video.readyState < globalThis.HTMLMediaElement.HAVE_CURRENT_DATA) return
    if (video.currentTime === lastVideoTime) return
    lastVideoTime = video.currentTime

    const result = faceLandmarker.detectForVideo(video, now)
    const landmarks = result.faceLandmarks[0] ?? null
    const frame = engine.processFrame(landmarks, now)
    applyFrame(frame, now)
  }

  function applyFrame(frame: EyeFrameResult, now: number): void {
    faceDetected.value = frame.faceDetected
    leftEyeState.value = frame.leftEyeState
    rightEyeState.value = frame.rightEyeState
    combinedState.value = frame.combinedState
    leftRatio.value = frame.ratios.left
    rightRatio.value = frame.ratios.right
    confidence.value = frame.confidence
    fps.value =
      fps.value * 0.82 + (1000 / Math.max(now - lastFrameAt, 1)) * 0.18
    lastFrameAt = now

    const smoothed = gazeSmoother.update(frame.gaze)
    rawGaze.value = smoothed
    // 좌우 방향은 gaze 소스(eye-engine)에서 raw x를 한 번만 뒤집어 사용자 기준으로 정규화한다.
    // 여기서 다시 뒤집으면 이중 반전이 되어 커서가 시선과 반대로 움직이므로 그대로 사용한다.
    const predicted = gazeCalibrator.predict(smoothed)
    screenGaze.value = predicted
      ? { x: predicted.x, y: predicted.y, confidence: predicted.confidence }
      : null

    for (const event of frame.events) {
      lastEvent.value = event
      eventSequence.value += 1
      for (const listener of eventListeners) {
        listener(event)
      }
    }
  }

  /** 카메라 권한 요청 + MediaPipe 모델 로드를 함께 진행한다. 이미 켜져 있으면 즉시 true. */
  async function start(): Promise<boolean> {
    if (isActive.value) return true
    modelError.value = null
    isLoadingModel.value = true
    try {
      const [landmarker, stream] = await Promise.all([
        faceLandmarker ?? loadFaceLandmarker({ numFaces: 1 }),
        camera.start(),
      ])
      faceLandmarker = landmarker
      if (!stream) {
        modelError.value = camera.errorName.value ?? 'camera-unavailable'
        return false
      }
      isActive.value = true
      lastVideoTime = -1
      rafHandle = globalThis.requestAnimationFrame(loop)
      return true
    } catch (error) {
      camera.stop()
      modelError.value =
        error instanceof Error ? error.message : 'model-load-failed'
      return false
    } finally {
      isLoadingModel.value = false
    }
  }

  function stop(): void {
    if (rafHandle !== null) {
      globalThis.cancelAnimationFrame(rafHandle)
      rafHandle = null
    }
    camera.stop()
    isActive.value = false
    faceDetected.value = false
    combinedState.value = 'UNKNOWN'
    engine.resetTemporalState()
    gazeSmoother.reset()
  }

  // --- 눈 뜬/감은 기준(threshold) 캘리브레이션 ---
  // 사용자마다 눈 크기·조명·안경 착용 여부가 달라 "눈을 감았다"고 판단하는 기준(EAR)이 다르다.
  // 일정 시간 동안 눈을 뜨고/감고 있게 하고 그 사이 측정값의 절사평균(trimmed mean)을 기준으로 삼는다.
  async function recordEyeSample(
    kind: 'open' | 'closed',
    durationMs = 1200,
  ): Promise<EyeSampleResult> {
    const samples: { left: number; right: number }[] = []
    let closedFrameCount = 0
    const startedAt = globalThis.performance.now()
    while (globalThis.performance.now() - startedAt < durationMs) {
      await nextAnimationFrame()
      const latest = engine.lastResult
      if (latest.faceDetected && latest.confidence > 0.45) {
        samples.push(latest.ratios)
        if (latest.combinedState === 'BOTH_CLOSED') closedFrameCount += 1
      }
    }

    if (samples.length < 8) {
      return { success: false, sampleCount: samples.length, reason: 'no_face' }
    }

    const left = trimmedMean(samples.map((sample) => sample.left))
    const right = trimmedMean(samples.map((sample) => sample.right))

    // 요청한 단계와 실제 눈 상태가 반대이면 잘못된 기준이 저장되므로 거절한다(프로필 미변경).
    // - open : 대부분 프레임이 감긴 상태면 사용자가 눈을 감고 있었던 것
    // - closed: 감은 값이 방금 기록한 본인 뜬 값 대비 충분히 낮지 않으면 눈을 안 감은 것
    if (kind === 'open' && closedFrameCount > samples.length / 2) {
      return {
        success: false,
        sampleCount: samples.length,
        reason: 'eyes_closed',
      }
    }
    if (kind === 'closed') {
      const notClearlyClosed =
        left >= engine.profile.openEyeRatioLeft * CLOSED_EYE_SEPARATION ||
        right >= engine.profile.openEyeRatioRight * CLOSED_EYE_SEPARATION
      if (notClearlyClosed) {
        return {
          success: false,
          sampleCount: samples.length,
          reason: 'eyes_open',
        }
      }
    }

    const patch: Partial<EyeCalibrationProfile> =
      kind === 'open'
        ? { openEyeRatioLeft: left, openEyeRatioRight: right }
        : { closedEyeRatioLeft: left, closedEyeRatioRight: right }
    engine.setProfile({
      ...patch,
      cameraWidth:
        camera.videoRef.value?.videoWidth ?? engine.profile.cameraWidth,
      cameraHeight:
        camera.videoRef.value?.videoHeight ?? engine.profile.cameraHeight,
      createdAt: new Date().toISOString(),
    })
    return { success: true, sampleCount: samples.length }
  }

  function resetEyeBaseline(): void {
    engine.setProfile(DEFAULT_CALIBRATION_PROFILE)
  }

  /** 이전 세션에서 저장해 둔 프로필을 그대로 적용한다(재보정 없이 바로 게임 시작하고 싶을 때). */
  function applyEyeProfile(profile: Partial<EyeCalibrationProfile>): void {
    engine.setProfile(profile)
  }

  const eyeProfile = computed(() => engine.profile)

  // --- 시선 좌표(gaze point) 캘리브레이션 ---
  // 눈을 감았는지 여부와 달리, "화면의 어디를 보고 있는지"는 카메라 각도·모니터 크기·얼굴 거리에 따라
  // 사람마다 다르게 왜곡된다. 그림그리기/에어하키처럼 화면 좌표가 필요한 게임에서만 이 단계를 쓴다.
  function beginGazeCalibration(): void {
    gazeCalibrator.clear()
  }

  /** target: 0~1로 정규화한 화면상의 캘리브레이션 점 좌표. 현재 프레임의 원시 시선과 짝지어 기록한다. */
  function addGazeCalibrationSample(target: Point): boolean {
    const current = rawGaze.value
    if (!current) return false
    gazeCalibrator.addPair(current, target)
    return true
  }

  function finishGazeCalibration(): GazeCalibrationResult | null {
    try {
      const profile = gazeCalibrator.fit()
      const evaluation = gazeCalibrator.evaluate(
        camera.videoRef.value?.videoWidth ?? 1280,
        camera.videoRef.value?.videoHeight ?? 720,
      )
      return { profile, evaluation }
    } catch {
      return null
    }
  }

  function applyGazeProfile(profile: GazeCalibrationProfile | null): void {
    gazeCalibrator.setProfile(profile)
  }

  onScopeDispose(stop)

  return {
    videoRef: camera.videoRef,
    /** 내부적으로 열어 둔 원본 MediaStream. 다른 <video>(웹캠 프리뷰 등)에 같이 붙이거나 LiveKit
     * 송출 트랙(getVideoTracks()[0])을 꺼낼 때 재사용한다 — getUserMedia를 중복 호출하지 않기 위함. */
    stream: camera.stream,
    isActive,
    isLoadingModel,
    modelError,
    /** 카메라 트랙이 라이브인지. 브라우저/OS에서 캠을 끄면(track ended) false로 떨어진다. */
    cameraActive: camera.isActive,
    /** 카메라 종료/오류 사유('ended'·'unavailable'·DOMException name 등). */
    cameraError: camera.errorName,
    /** 카메라를 강제로 다시 획득한다(껐다 켜기 복구). 인식 루프는 유지된 채 스트림만 갱신된다. */
    restartCamera: camera.restart,

    faceDetected,
    leftEyeState,
    rightEyeState,
    combinedState,
    leftRatio,
    rightRatio,
    confidence,
    fps,
    rawGaze,
    screenGaze,
    lastEvent,
    eventSequence,
    onEyeEvent,

    start,
    stop,

    recordEyeSample,
    resetEyeBaseline,
    applyEyeProfile,
    eyeProfile,

    beginGazeCalibration,
    addGazeCalibrationSample,
    finishGazeCalibration,
    applyGazeProfile,
    gazeCalibrationTargets: CALIBRATION_TARGETS,
  }
}

function nextAnimationFrame(): Promise<number> {
  return new Promise((resolve) => globalThis.requestAnimationFrame(resolve))
}

function trimmedMean(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b)
  const trim = Math.floor(sorted.length * 0.15)
  const kept = sorted.slice(trim, sorted.length - trim || undefined)
  return kept.reduce((sum, value) => sum + value, 0) / kept.length
}
