/**
 * SeeSo(Eyedid) 시선 추적 SDK 래퍼.
 *
 * MediaPipe는 얼굴 메시와 홍채 위치만 주기 때문에 "화면의 어디를 보는지"는 홍채가 눈 안에서
 * 어디쯤 있는지로 **추정**할 수밖에 없고, 고개 각도·화면 거리·모니터 크기를 제대로 반영하지
 * 못해 정확도가 낮다. SeeSo는 시선 지점 추정 자체를 목적으로 만들어진 전용 SDK라 훨씬 정확하다
 * (연동 전 프로토타입의 그림그리기가 기본으로 쓰던 방식이 이것이다).
 *
 * 이 모듈은 SDK 호출만 감싸고, 좌표 변환·보정·게임 연결은 하지 않는다(`useSeeSoGaze` 담당).
 *
 * ## 좌표계
 * `onGaze`가 주는 `x`/`y`는 **브라우저 뷰포트 픽셀 좌표**다(스크린 좌표가 아니다). 캔버스 기준
 * 0~1 정규화는 대상 엘리먼트의 `getBoundingClientRect()`로 상위 레이어에서 처리한다.
 *
 * ## ⚠️ deinit()을 부르지 않는 이유
 * SeeSo는 Emscripten(WASM)으로 빌드되어 있는데, `deinit()`이 내부적으로 메인 브라우저 스레드를
 * 블로킹한다("Blocking on the main thread is very dangerous" 경고와 함께). 화면을 떠날 때 이걸
 * 호출하면 브라우저 탭이 그대로 응답 없음 상태에 빠진다. 그래서 **SDK 인스턴스를 페이지 수명
 * 동안 재사용**하고, 화면을 떠날 때는 추적만 멈춘다(`stopTracking`). 초기화(WASM 로딩 + 라이선스
 * 검증)가 비싼 작업이라 재진입이 빨라지는 이점도 있다.
 */

export interface SeeSoGazeInfo {
  x: number
  y: number
  trackingState: number
  leftOpenness?: number
  rightOpenness?: number
  timestamp?: number
}

/**
 * 추적 성공 상태 코드(SDK의 `TrackingState.SUCCESS`). 이 값이 아니면 좌표가 있더라도 신뢰할 수
 * 없으므로 버려야 한다.
 */
export const SEESO_TRACKING_SUCCESS = 0

/**
 * 보정 진행률이 이 시간 동안 오르지 않으면 "멈춤"으로 보고 표본 수집을 다시 요청한다.
 * 너무 짧으면 정상 수집 중에도 불필요하게 재요청하고, 너무 길면 사용자가 멈춘 화면을 오래 본다.
 */
const CALIBRATION_STALL_MS = 2000

/**
 * 재요청 한도. **시선이 잡히고 있는지에 따라 다르게 잡는다.**
 *
 * 보정이 멈추는 이유는 성격이 완전히 다른 두 가지인데, 예전에는 둘을 같은 한도로 묶어 놨다.
 *
 * 1. **사용자가 잠깐 다른 곳을 봤다** — 시선은 계속 잡히고 있다. SDK가 그 점의 진행률을 되돌리고
 *    새 수집 요청을 기다릴 뿐이라, 다시 점을 바라보면 이어서 진행된다. 사람이 한눈파는 시간은
 *    몇 초씩 걸리므로 여기서 몇 번 만에 포기해 버리면 정상 사용자를 실패로 처리하게 된다
 *    (실제로 "다른 곳을 보면 보정이 멈춘다"고 보고된 증상).
 * 2. **시선이 아예 안 잡힌다** — 자리를 비웠거나 엔진이 죽었다. 기다려도 회복되지 않으므로 빨리
 *    포기하고 다음 단계로 넘어가는 편이 낫다.
 */
const MAX_STALL_RECOVERIES_TRACKED = 15
const MAX_STALL_RECOVERIES_UNTRACKED = 2

/** 이 시간 안에 추적 성공 좌표를 받았으면 "사용자가 앞에 있다"고 본다. */
const GAZE_TRACKED_WITHIN_MS = 1500

/**
 * 이 좌표가 "추적 성공"인지. SeeSo는 시선을 놓치면 NaN이나 SUCCESS가 아닌 상태를 보낸다.
 * `trackingState`가 숫자가 아닌 SDK 버전에서는 좌표가 유한하면 성공으로 본다.
 */
function isTrackedGaze(gaze: SeeSoGazeInfo): boolean {
  if (!Number.isFinite(gaze.x) || !Number.isFinite(gaze.y)) return false

  return (
    typeof gaze.trackingState !== 'number' ||
    gaze.trackingState === SEESO_TRACKING_SUCCESS
  )
}

export interface SeeSoProviderOptions {
  licenseKey: string
  /** 모니터 대각 길이(인치). SeeSo가 화면상 시선 위치를 계산하는 데 쓴다. */
  monitorSizeInch: number
  /** 얼굴과 화면 사이 거리(cm). */
  faceDistanceCm: number
  /** 카메라가 화면 위쪽에 있는지(노트북 내장캠 등은 true). */
  cameraOnTop?: boolean
  onGaze: (gaze: SeeSoGazeInfo) => void
}

/**
 * 페이지 전체에서 공유하는 SDK 인스턴스. 위 "deinit()을 부르지 않는 이유" 참고 — 한 번만
 * 초기화하고 계속 재사용한다.
 */
let sharedSdk: SeeSoSdk | null = null
/** 초기화가 진행 중일 때의 약속. 동시에 여러 번 start()가 불려도 한 번만 초기화되게 한다. */
let sharedInitPromise: Promise<SeeSoSdk> | null = null

async function ensureSdkInitialized(
  options: SeeSoProviderOptions,
): Promise<SeeSoSdk> {
  if (sharedSdk) return sharedSdk
  if (sharedInitPromise) return sharedInitPromise

  sharedInitPromise = (async () => {
    // SDK는 용량이 크고(약 450KB) 그림그리기에서만 쓰이므로 동적 import로 불러온다 — 다른
    // 게임만 하는 사용자는 이 코드를 아예 내려받지 않는다.
    const [{ default: EasySeeSo }, seesoModule] = await Promise.all([
      import('seeso/easy-seeso'),
      import('seeso'),
    ])
    const { UserStatusOption } = seesoModule as unknown as {
      UserStatusOption: new (
        attention: boolean,
        blink: boolean,
        drowsiness: boolean,
      ) => unknown
    }

    const sdk = new (EasySeeSo as unknown as new () => SeeSoSdk)()
    // (attention, blink, drowsiness) — 눈 깜빡임 정보만 켠다.
    const userStatusOption = new UserStatusOption(false, true, false)

    await new Promise<void>((resolve, reject) => {
      sdk.init(
        options.licenseKey,
        () => resolve(),
        () =>
          reject(
            new Error(
              'SeeSo 초기화 실패 — 라이선스 키, 등록된 도메인, 카메라 권한을 확인해 주세요.',
            ),
          ),
        userStatusOption,
      )
    })

    if (!sdk.checkMobile()) {
      sdk.setMonitorSize(options.monitorSizeInch)
      sdk.setFaceDistance(options.faceDistanceCm)
      sdk.setCameraPosition(
        globalThis.window.outerWidth / 2,
        options.cameraOnTop ?? true,
      )
    }

    sharedSdk = sdk
    return sdk
  })()

  try {
    return await sharedInitPromise
  } catch (error) {
    // 실패하면 다음 시도에서 다시 초기화할 수 있도록 약속을 비운다.
    sharedInitPromise = null
    throw error
  }
}

export class SeeSoGazeProvider {
  private readonly options: SeeSoProviderOptions
  private started = false
  /** stop() 이후 늦게 도착한 콜백을 무시하기 위한 플래그. */
  private disposed = false
  /** 마지막으로 **추적에 성공한** 좌표를 받은 시각(ms). 사용자가 앞에 있는지 판정에 쓴다. */
  private lastTrackedGazeAt = 0

  constructor(options: SeeSoProviderOptions) {
    this.options = options
  }

  get isStarted(): boolean {
    return this.started
  }

  /**
   * SDK를 준비하고 추적을 시작한다.
   *
   * 라이선스가 현재 도메인에서 유효하지 않거나 카메라를 쓸 수 없으면 예외를 던진다. 호출부는
   * 이걸 잡아서 기존 MediaPipe 방식으로 폴백해야 한다.
   */
  async start(): Promise<void> {
    const sdk = await ensureSdkInitialized(this.options)
    if (this.disposed) return

    const started = await sdk.startTracking(
      (gazeInfo: SeeSoGazeInfo) => {
        if (this.disposed) return
        if (isTrackedGaze(gazeInfo)) this.lastTrackedGazeAt = Date.now()
        this.options.onGaze(gazeInfo)
      },
      () => {},
    )
    if (!started) {
      throw new Error(
        'SeeSo 추적 시작 실패 — 다른 앱이 카메라를 사용 중인지 확인해 주세요.',
      )
    }
    if (this.disposed) {
      // 시작을 기다리는 동안 정리 요청이 들어왔다 — 바로 되돌린다.
      this.stop()
      return
    }
    this.started = true
  }

  /**
   * SeeSo 자체 보정(1단계). 점 개수는 SDK가 1 또는 5만 지원한다.
   *
   * SDK가 다음 점 위치를 알려주면(`onPoint`) 화면에 그 점을 표시하고, 사용자의 시선이 자리잡을
   * 시간을 준 뒤 표본 수집을 시작해야 한다 — 프로토타입과 동일하게 650ms 후 수집한다.
   *
   * @returns 보정 데이터 문자열. 저장해 두면 다음 세션에서 {@link restoreCalibration}으로 복원할 수 있다.
   */
  async calibrate(handlers: {
    pointCount?: 1 | 5
    onPoint: (x: number, y: number) => void
    onProgress: (progress: number) => void
    /**
     * 진행이 멈춰 표본 수집을 다시 요청할 때.
     *
     * @param tracked 시선이 잡히고 있는지. true면 "잠깐 다른 곳을 봤다", false면 "얼굴이 안
     *   보인다"는 뜻이라, 사용자에게 안내할 내용이 완전히 다르다.
     */
    onStalled?: (attempt: number, maxAttempts: number, tracked: boolean) => void
  }): Promise<string> {
    const sdk = this.requireSdk()
    const pointCount = handlers.pointCount ?? 5

    return new Promise<string>((resolve, reject) => {
      let collectTimer = 0
      let stallWatchdog = 0
      let bestProgress = 0
      let lastAdvanceAt = Date.now()
      let stallRecoveries = 0

      /** 표본 수집을 요청한다. 재요청도 같은 경로를 쓴다. */
      function requestCollect(delayMs: number): void {
        globalThis.clearTimeout(collectTimer)
        collectTimer = globalThis.setTimeout(() => {
          lastAdvanceAt = Date.now()
          sdk.startCollectSamples()
        }, delayMs)
      }

      function cleanup(): void {
        globalThis.clearTimeout(collectTimer)
        globalThis.clearInterval(stallWatchdog)
      }

      /**
       * ⚠️ 진행이 멈추면 수집을 다시 요청한다.
       *
       * `startCollectSamples()`는 점마다 한 번만 호출하면 되는 게 원칙이지만, 수집 도중 사용자가
       * 다른 곳을 보면 SDK가 그 점의 진행률을 되돌리고 새 수집 요청을 기다리는 상태가 된다.
       * 재요청해주는 쪽이 없으면 다시 점을 바라봐도 진행률이 오르지 않고 영영 멈춘다(실제로
       * 보고된 증상). 일정 시간 진행이 없으면 수집을 다시 걸어 스스로 회복하게 한다.
       */
      stallWatchdog = globalThis.setInterval(() => {
        if (Date.now() - lastAdvanceAt < CALIBRATION_STALL_MS) return

        // 시선이 잡히고 있으면 사용자가 앞에 있다는 뜻이다 — 잠깐 다른 곳을 봤을 뿐이므로 다시
        // 점을 바라보면 이어서 진행된다. 넉넉히 기다린다. 시선이 아예 안 잡히면 회복 가능성이
        // 없으므로 빨리 접는다.
        const tracked = this.isGazeTracked()
        const limit = tracked
          ? MAX_STALL_RECOVERIES_TRACKED
          : MAX_STALL_RECOVERIES_UNTRACKED

        if (stallRecoveries >= limit) {
          cleanup()
          try {
            sdk.stopCalibration()
          } catch {
            // 이미 끝났거나 SDK가 지원하지 않는 경우. 중단 자체를 막을 이유는 없다.
          }
          reject(
            new Error(
              tracked
                ? 'SeeSo 보정이 더 진행되지 않아 중단했습니다. 다음 단계로 넘어갑니다.'
                : '시선이 잡히지 않아 SeeSo 보정을 중단했습니다.',
            ),
          )
          return
        }

        stallRecoveries += 1
        handlers.onStalled?.(stallRecoveries, limit, tracked)
        requestCollect(0)
      }, 500)

      const started = sdk.startCalibration(
        (x: number, y: number) => {
          handlers.onPoint(x, y)
          // 새 점으로 넘어갔다 — 진행 기준을 초기화하고, 시선이 자리잡을 시간을 준 뒤 수집한다.
          bestProgress = 0
          lastAdvanceAt = Date.now()
          requestCollect(650)
        },
        (progress: number) => {
          handlers.onProgress(progress)
          // 진행률이 실제로 **올라갔을 때만** 살아있는 것으로 본다. 되돌아간 뒤 멈춘 경우를
          // 잡아내야 하므로 하락은 갱신으로 치지 않는다.
          if (progress > bestProgress) {
            bestProgress = progress
            lastAdvanceAt = Date.now()
            // 다시 움직이기 시작했다 — 포기 카운터를 되돌린다.
            stallRecoveries = 0
          }
        },
        async (calibrationData: string) => {
          cleanup()
          try {
            await sdk.setCalibrationData(calibrationData)
            resolve(calibrationData)
          } catch (error) {
            reject(error)
          }
        },
        pointCount,
      )
      if (!started) {
        cleanup()
        reject(
          new Error(
            'SeeSo 보정을 시작하지 못했습니다. 잠시 후 다시 시도해 주세요.',
          ),
        )
      }
    })
  }

  /** 이전에 저장해 둔 SeeSo 보정 데이터를 복원한다(재보정 없이 바로 사용). */
  async restoreCalibration(calibrationData: string): Promise<void> {
    await this.requireSdk().setCalibrationData(calibrationData)
  }

  /**
   * 추적을 멈춘다.
   *
   * ⚠️ 여기서 `deinit()`을 호출하면 안 된다 — Emscripten 런타임이 메인 브라우저 스레드를
   * 블로킹해 페이지가 응답 없음 상태로 굳는다. SDK 인스턴스는 그대로 두고 다음 진입 때
   * 재사용한다(카메라는 `stopTracking()`이 해제한다).
   */
  stop(): void {
    this.disposed = true
    if (!this.started) return
    this.started = false
    try {
      sharedSdk?.stopTracking()
    } catch {
      // 이미 멈춰 있을 수 있다. 정리 실패가 화면 이동을 막으면 안 된다.
    }
  }

  /** 지금 사용자의 시선이 잡히고 있는지(= 카메라 앞에 있고 눈이 보이는지). */
  private isGazeTracked(): boolean {
    return (
      this.lastTrackedGazeAt > 0 &&
      Date.now() - this.lastTrackedGazeAt <= GAZE_TRACKED_WITHIN_MS
    )
  }

  private requireSdk(): SeeSoSdk {
    if (!sharedSdk || !this.started) {
      throw new Error('SeeSo가 시작되지 않았습니다.')
    }
    return sharedSdk
  }
}

/** SDK가 타입 정의를 제공하지 않아, 이 앱이 실제로 쓰는 메서드만 최소한으로 선언한다. */
interface SeeSoSdk {
  init(
    licenseKey: string,
    onSuccess: () => void,
    onError: () => void,
    userStatusOption: unknown,
  ): void
  checkMobile(): boolean
  setMonitorSize(inch: number): void
  setFaceDistance(cm: number): void
  setCameraPosition(x: number, isTop: boolean): void
  startTracking(
    onGaze: (gazeInfo: SeeSoGazeInfo) => void,
    onDebug: () => void,
  ): Promise<boolean>
  startCalibration(
    onPoint: (x: number, y: number) => void,
    onProgress: (progress: number) => void,
    onComplete: (calibrationData: string) => void,
    pointCount: number,
  ): boolean
  startCollectSamples(): void
  /** 진행 중인 보정을 중단한다. 멈춘 보정에서 빠져나올 때 쓴다. */
  stopCalibration(): boolean
  setCalibrationData(data: string): Promise<void>
  stopTracking(): void
}
