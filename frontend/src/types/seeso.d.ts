/**
 * SeeSo(Eyedid) SDK 타입 선언.
 *
 * `seeso` 패키지는 타입 정의를 제공하지 않아(`@types/seeso`도 없다) 그대로 import하면
 * `noImplicitAny`에 걸려 빌드가 실패한다. 이 앱이 실제로 호출하는 API만 최소한으로 선언한다.
 * 실제 사용은 `src/lib/eye-tracking/seeso-gaze-provider.ts`가 감싼다.
 */

declare module 'seeso' {
  /** 어떤 사용자 상태 콜백을 켤지 지정한다. (attention, blink, drowsiness) */
  export class UserStatusOption {
    constructor(attention: boolean, blink: boolean, drowsiness: boolean)
  }

  /** 추적 품질 상태 코드. */
  export const TrackingState: {
    readonly SUCCESS: number
    readonly LOW_CONFIDENCE: number
    readonly UNSUPPORTED: number
    readonly FACE_MISSING: number
  }
}

declare module 'seeso/easy-seeso' {
  interface EasySeeSoGazeInfo {
    x: number
    y: number
    trackingState: number
    leftOpenness?: number
    rightOpenness?: number
    timestamp?: number
  }

  export default class EasySeeSo {
    init(
      licenseKey: string,
      onSuccess: () => void,
      onError: () => void,
      userStatusOption?: unknown,
    ): void
    checkMobile(): boolean
    setMonitorSize(inch: number): void
    setFaceDistance(cm: number): void
    setCameraPosition(x: number, isTop: boolean): void
    startTracking(
      onGaze: (gazeInfo: EasySeeSoGazeInfo) => void,
      onDebug: (
        fps: number,
        latencyMin: number,
        latencyMax: number,
        latencyAvg: number,
      ) => void,
    ): Promise<boolean>
    startCalibration(
      onNextPoint: (x: number, y: number) => void,
      onProgress: (progress: number) => void,
      onComplete: (calibrationData: string) => void,
      pointCount: number,
    ): boolean
    startCollectSamples(): void
    setCalibrationData(data: string): Promise<void>
    showImage(): void
    hideImage(): void
    stopTracking(): void
    deinit(): void
  }
}
