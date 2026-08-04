/**
 * 눈 랜드마크 → 눈 상태/이벤트 판정 엔진.
 *
 * `ai_game` 프로토타입의 `eye-engine.js`를 그대로 이식했다. MediaPipe Face Landmarker가 매 프레임
 * 내려주는 468개 얼굴 랜드마크(+홍채 10개, 총 478개)만 입력으로 받으므로 DOM이나 카메라와는 무관한
 * 순수 로직이다 — 유닛 테스트가 가능하고, 서버(Node)에서도 그대로 돌릴 수 있다.
 */
import {
  DEFAULT_CALIBRATION_PROFILE,
  DEFAULT_EYE_DETECTION_CONFIG,
  type EyeCalibrationProfile,
  type EyeDetectionConfig,
} from './config'

const LEFT_EYE = Object.freeze({
  corners: [33, 133],
  upper: [160, 158],
  lower: [144, 153],
  iris: [468, 469, 470, 471, 472],
})

const RIGHT_EYE = Object.freeze({
  corners: [362, 263],
  upper: [385, 387],
  lower: [380, 373],
  iris: [473, 474, 475, 476, 477],
})

const EVENT_TYPES = [
  'BLINK',
  'DOUBLE_BLINK',
  'FAST_BLINK',
  'LONG_CLOSE',
  'LEFT_WINK',
  'RIGHT_WINK',
  'GAZE_MOVE',
  'FACE_LOST',
] as const

export type EyeEventType = (typeof EVENT_TYPES)[number]
export type EyeState = 'OPEN' | 'HALF_CLOSED' | 'CLOSED' | 'NOT_DETECTED'
export type CombinedEyeState =
  | 'BOTH_OPEN'
  | 'BOTH_HALF_CLOSED'
  | 'BOTH_CLOSED'
  | 'LEFT_CLOSED'
  | 'RIGHT_CLOSED'
  | 'UNKNOWN'

export interface Landmark {
  x: number
  y: number
  z?: number
}

export interface EyeEvent {
  type: EyeEventType
  occurredAt: number
  confidence: number
  durationMs?: number
  details?: Record<string, unknown>
}

export interface EyeRatios {
  left: number
  right: number
}

export interface GazeEstimate {
  x: number
  y: number
  confidence: number
}

export interface EyeFrameResult {
  faceDetected: boolean
  leftEyeState: EyeState
  rightEyeState: EyeState
  combinedState: CombinedEyeState
  ratios: EyeRatios
  confidence: number
  events: EyeEvent[]
  gaze: GazeEstimate | null
  landmarks: Landmark[] | null
}

interface ClosureTracker {
  startedAt: number
  lastChangedAt: number
  minLeftRatio: number
  minRightRatio: number
  sawBothClosed: boolean
  sawLeftClosed: boolean
  sawRightClosed: boolean
  emittedLongClose: boolean
  confidence: number
}

interface EyeShape {
  corners: readonly number[]
  upper: readonly number[]
  lower: readonly number[]
  iris?: readonly number[]
}

export class EyeInteractionEngine {
  config: EyeDetectionConfig
  profile: EyeCalibrationProfile
  lastResult: EyeFrameResult
  private lastEventAt: Map<EyeEventType, number>
  private currentClosure: ClosureTracker | null
  private lastBlinkAt: number
  private lastFrameAt: number
  private faceLostStartedAt: number | null
  private smoothedGaze: GazeEstimate | null

  constructor(
    options: {
      config?: Partial<EyeDetectionConfig>
      profile?: Partial<EyeCalibrationProfile>
    } = {},
  ) {
    this.config = { ...DEFAULT_EYE_DETECTION_CONFIG, ...options.config }
    this.profile = { ...DEFAULT_CALIBRATION_PROFILE, ...options.profile }
    this.lastResult = createEmptyResult()
    this.lastEventAt = new Map(
      EVENT_TYPES.map((type) => [type, Number.NEGATIVE_INFINITY]),
    )
    this.currentClosure = null
    this.lastBlinkAt = Number.NEGATIVE_INFINITY
    this.lastFrameAt = Number.NEGATIVE_INFINITY
    this.faceLostStartedAt = null
    this.smoothedGaze = null
  }

  setProfile(profile: Partial<EyeCalibrationProfile>): void {
    this.profile = { ...this.profile, ...profile }
  }

  resetTemporalState(): void {
    this.currentClosure = null
    this.lastBlinkAt = Number.NEGATIVE_INFINITY
    this.faceLostStartedAt = null
    this.smoothedGaze = null
    this.lastEventAt = new Map(
      EVENT_TYPES.map((type) => [type, Number.NEGATIVE_INFINITY]),
    )
  }

  processFrame(
    landmarks: Landmark[] | null | undefined,
    timestampMs: number,
  ): EyeFrameResult {
    if (!landmarks || landmarks.length < 468) {
      const events = this.handleFaceLost(timestampMs)
      this.lastResult = { ...createEmptyResult(), events }
      return this.lastResult
    }

    this.faceLostStartedAt = null
    const ratios: EyeRatios = {
      left: calculateEyeAspectRatio(landmarks, LEFT_EYE),
      right: calculateEyeAspectRatio(landmarks, RIGHT_EYE),
    }
    const leftEyeState = classifyEyeState(
      ratios.left,
      this.profile.openEyeRatioLeft,
      this.profile.closedEyeRatioLeft,
    )
    const rightEyeState = classifyEyeState(
      ratios.right,
      this.profile.openEyeRatioRight,
      this.profile.closedEyeRatioRight,
    )
    const combinedState = combineEyeStates(leftEyeState, rightEyeState)
    const confidence = calculateFrameConfidence(
      ratios,
      this.profile,
      leftEyeState,
      rightEyeState,
    )
    const gaze = this.estimateSmoothedGaze(landmarks)
    const events: EyeEvent[] = []

    if (confidence >= this.config.confidenceThreshold) {
      events.push(
        ...this.updateClosureState(
          combinedState,
          timestampMs,
          confidence,
          ratios,
        ),
      )
    } else {
      this.currentClosure = null
    }

    const gazeEvent = this.detectGazeMove(gaze, timestampMs)
    if (gazeEvent) {
      events.push(gazeEvent)
    }

    this.lastFrameAt = timestampMs
    this.lastResult = {
      faceDetected: true,
      leftEyeState,
      rightEyeState,
      combinedState,
      ratios,
      confidence,
      events,
      gaze,
      landmarks,
    }
    return this.lastResult
  }

  private handleFaceLost(timestampMs: number): EyeEvent[] {
    if (this.faceLostStartedAt === null) {
      this.faceLostStartedAt = timestampMs
    }
    this.currentClosure = null
    const lostDuration = timestampMs - this.faceLostStartedAt
    if (lostDuration >= this.config.faceLostThresholdMs) {
      const lastFaceLostAt =
        this.lastEventAt.get('FACE_LOST') ?? Number.NEGATIVE_INFINITY
      if (timestampMs - lastFaceLostAt < this.config.faceLostThresholdMs) {
        return []
      }
      const event = this.createEvent(
        'FACE_LOST',
        timestampMs,
        0.4,
        lostDuration,
      )
      return event ? [event] : []
    }
    return []
  }

  private updateClosureState(
    combinedState: CombinedEyeState,
    timestampMs: number,
    confidence: number,
    ratios: EyeRatios,
  ): EyeEvent[] {
    const events: EyeEvent[] = []
    if (combinedState === 'BOTH_OPEN') {
      if (this.currentClosure) {
        const completedEvent = this.finalizeClosure(timestampMs, confidence)
        if (completedEvent) {
          events.push(completedEvent)
        }
      }
      this.currentClosure = null
      return events
    }

    if (combinedState === 'UNKNOWN') {
      return events
    }

    if (combinedState === 'BOTH_HALF_CLOSED') {
      if (!this.currentClosure) {
        this.currentClosure = makeClosureTracker(
          timestampMs,
          ratios,
          confidence,
        )
      }
      this.currentClosure.lastChangedAt = timestampMs
      this.currentClosure.minLeftRatio = Math.min(
        this.currentClosure.minLeftRatio,
        ratios.left,
      )
      this.currentClosure.minRightRatio = Math.min(
        this.currentClosure.minRightRatio,
        ratios.right,
      )
      this.currentClosure.confidence = Math.min(
        this.currentClosure.confidence,
        confidence,
      )
      return events
    }

    if (!this.currentClosure) {
      this.currentClosure = makeClosureTracker(timestampMs, ratios, confidence)
    }

    this.currentClosure.lastChangedAt = timestampMs
    this.currentClosure.minLeftRatio = Math.min(
      this.currentClosure.minLeftRatio,
      ratios.left,
    )
    this.currentClosure.minRightRatio = Math.min(
      this.currentClosure.minRightRatio,
      ratios.right,
    )
    this.currentClosure.confidence = Math.min(
      this.currentClosure.confidence,
      confidence,
    )

    if (combinedState === 'BOTH_CLOSED') {
      this.currentClosure.sawBothClosed = true
      this.currentClosure.sawLeftClosed = true
      this.currentClosure.sawRightClosed = true
    }
    if (combinedState === 'LEFT_CLOSED') {
      this.currentClosure.sawLeftClosed = true
    }
    if (combinedState === 'RIGHT_CLOSED') {
      this.currentClosure.sawRightClosed = true
    }

    const durationMs = timestampMs - this.currentClosure.startedAt
    if (
      this.currentClosure.sawBothClosed &&
      !this.currentClosure.emittedLongClose &&
      durationMs >= this.config.longCloseThresholdMs
    ) {
      const event = this.createEvent(
        'LONG_CLOSE',
        timestampMs,
        confidence,
        durationMs,
      )
      if (event) {
        events.push(event)
        this.currentClosure.emittedLongClose = true
      }
    }

    return events
  }

  private finalizeClosure(
    timestampMs: number,
    confidence: number,
  ): EyeEvent | null {
    if (!this.currentClosure) {
      return null
    }

    const durationMs = timestampMs - this.currentClosure.startedAt
    const closureConfidence = Math.min(
      confidence,
      this.currentClosure.confidence,
    )
    const isDurationValid =
      durationMs >= this.config.minBlinkDurationMs &&
      durationMs <= this.config.maxBlinkDurationMs

    if (this.currentClosure.emittedLongClose) {
      return null
    }

    if (!isDurationValid) {
      return null
    }

    if (this.currentClosure.sawBothClosed) {
      const gapFromLastBlink = timestampMs - this.lastBlinkAt
      this.lastBlinkAt = timestampMs
      if (
        gapFromLastBlink > 0 &&
        gapFromLastBlink <= this.config.doubleBlinkIntervalMs
      ) {
        return this.createEvent(
          'DOUBLE_BLINK',
          timestampMs,
          closureConfidence,
          durationMs,
        )
      }
      if (durationMs <= this.config.fastBlinkThresholdMs) {
        return this.createEvent(
          'FAST_BLINK',
          timestampMs,
          closureConfidence,
          durationMs,
        )
      }
      return this.createEvent(
        'BLINK',
        timestampMs,
        closureConfidence,
        durationMs,
      )
    }

    if (
      this.currentClosure.sawLeftClosed &&
      !this.currentClosure.sawRightClosed
    ) {
      return this.createEvent(
        'LEFT_WINK',
        timestampMs,
        closureConfidence,
        durationMs,
      )
    }

    if (
      this.currentClosure.sawRightClosed &&
      !this.currentClosure.sawLeftClosed
    ) {
      return this.createEvent(
        'RIGHT_WINK',
        timestampMs,
        closureConfidence,
        durationMs,
      )
    }

    return null
  }

  private createEvent(
    type: EyeEventType,
    timestampMs: number,
    confidence: number,
    durationMs?: number,
    details?: Record<string, unknown>,
  ): EyeEvent | null {
    const lastAt = this.lastEventAt.get(type) ?? Number.NEGATIVE_INFINITY
    if (timestampMs - lastAt < this.config.eventCooldownMs) {
      return null
    }
    this.lastEventAt.set(type, timestampMs)
    return {
      type,
      occurredAt: timestampMs,
      confidence: clamp(confidence, 0, 1),
      durationMs,
      details,
    }
  }

  private estimateSmoothedGaze(landmarks: Landmark[]): GazeEstimate | null {
    const left = estimateEyeGaze(landmarks, LEFT_EYE)
    const right = estimateEyeGaze(landmarks, RIGHT_EYE)
    if (!left || !right) {
      return null
    }
    const raw: GazeEstimate = {
      x: (left.x + right.x) / 2,
      y: (left.y + right.y) / 2,
      confidence: Math.min(left.confidence, right.confidence),
    }
    const smoothing = this.lastFrameAt > 0 ? 0.35 : 1
    this.smoothedGaze = this.smoothedGaze
      ? {
          x: lerp(this.smoothedGaze.x, raw.x, smoothing),
          y: lerp(this.smoothedGaze.y, raw.y, smoothing),
          confidence: raw.confidence,
        }
      : raw
    return this.smoothedGaze
  }

  private detectGazeMove(
    gaze: GazeEstimate | null,
    timestampMs: number,
  ): EyeEvent | null {
    if (!gaze || gaze.confidence < this.config.confidenceThreshold) {
      return null
    }
    const dx = gaze.x - 0.5
    const dy = gaze.y - 0.5
    const magnitude = Math.hypot(dx, dy)
    if (magnitude < this.config.gazeMoveThreshold) {
      return null
    }
    const lastAt = this.lastEventAt.get('GAZE_MOVE') ?? Number.NEGATIVE_INFINITY
    if (timestampMs - lastAt < this.config.gazeMoveCooldownMs) {
      return null
    }
    this.lastEventAt.set('GAZE_MOVE', timestampMs)
    return {
      type: 'GAZE_MOVE',
      occurredAt: timestampMs,
      confidence: gaze.confidence,
      details: {
        x: Number(gaze.x.toFixed(3)),
        y: Number(gaze.y.toFixed(3)),
        direction: gazeDirection(dx, dy),
      },
    }
  }
}

function makeClosureTracker(
  timestampMs: number,
  ratios: EyeRatios,
  confidence: number,
): ClosureTracker {
  return {
    startedAt: timestampMs,
    lastChangedAt: timestampMs,
    minLeftRatio: ratios.left,
    minRightRatio: ratios.right,
    sawBothClosed: false,
    sawLeftClosed: false,
    sawRightClosed: false,
    emittedLongClose: false,
    confidence,
  }
}

export function calculateEyeAspectRatio(
  landmarks: Landmark[],
  eye: EyeShape,
): number {
  const [outer, inner] = eye.corners.map((index) => landmarks[index])
  const [upperA, upperB] = eye.upper.map((index) => landmarks[index])
  const [lowerA, lowerB] = eye.lower.map((index) => landmarks[index])
  const horizontal = distance(outer, inner)
  if (!Number.isFinite(horizontal) || horizontal === 0) {
    return 0
  }
  const verticalA = distance(upperA, lowerA)
  const verticalB = distance(upperB, lowerB)
  return (verticalA + verticalB) / (2 * horizontal)
}

export function classifyEyeState(
  ratio: number,
  openRatio: number,
  closedRatio: number,
): EyeState {
  if (!Number.isFinite(ratio) || ratio <= 0) {
    return 'NOT_DETECTED'
  }
  const range = Math.max(openRatio - closedRatio, 0.05)
  const closedThreshold = closedRatio + range * 0.36
  const openThreshold = closedRatio + range * 0.54
  if (ratio <= closedThreshold) {
    return 'CLOSED'
  }
  if (ratio >= openThreshold) {
    return 'OPEN'
  }
  return 'HALF_CLOSED'
}

export function combineEyeStates(
  left: EyeState,
  right: EyeState,
): CombinedEyeState {
  if (left === 'NOT_DETECTED' || right === 'NOT_DETECTED') {
    return 'UNKNOWN'
  }
  if (left === 'OPEN' && right === 'OPEN') {
    return 'BOTH_OPEN'
  }
  if (left === 'CLOSED' && right === 'CLOSED') {
    return 'BOTH_CLOSED'
  }
  if (left === 'HALF_CLOSED' && right === 'HALF_CLOSED') {
    return 'BOTH_HALF_CLOSED'
  }
  if (left === 'CLOSED' && right !== 'CLOSED') {
    return 'LEFT_CLOSED'
  }
  if (right === 'CLOSED' && left !== 'CLOSED') {
    return 'RIGHT_CLOSED'
  }
  return 'UNKNOWN'
}

function calculateFrameConfidence(
  ratios: EyeRatios,
  profile: EyeCalibrationProfile,
  leftState: EyeState,
  rightState: EyeState,
): number {
  const left = eyeStateConfidence(
    ratios.left,
    profile.openEyeRatioLeft,
    profile.closedEyeRatioLeft,
    leftState,
  )
  const right = eyeStateConfidence(
    ratios.right,
    profile.openEyeRatioRight,
    profile.closedEyeRatioRight,
    rightState,
  )
  return clamp((left + right) / 2, 0, 1)
}

function eyeStateConfidence(
  ratio: number,
  openRatio: number,
  closedRatio: number,
  state: EyeState,
): number {
  const range = Math.max(openRatio - closedRatio, 0.05)
  if (state === 'OPEN') {
    return clamp((ratio - closedRatio) / range, 0, 1)
  }
  if (state === 'CLOSED') {
    return clamp((openRatio - ratio) / range, 0, 1)
  }
  if (state === 'HALF_CLOSED') {
    const middle = closedRatio + range * 0.5
    return 1 - clamp(Math.abs(ratio - middle) / (range * 0.5), 0, 1) * 0.45
  }
  return 0
}

function estimateEyeGaze(
  landmarks: Landmark[],
  eye: EyeShape,
): GazeEstimate | null {
  const irisIndices = eye.iris ?? []
  if (
    irisIndices.length === 0 ||
    landmarks.length <= Math.max(...irisIndices)
  ) {
    return null
  }
  const irisPoints = irisIndices
    .map((index) => landmarks[index])
    .filter((point): point is Landmark => Boolean(point))
  if (irisPoints.length === 0) {
    return null
  }
  const irisCenter = averagePoint(irisPoints)
  const outline = [...eye.corners, ...eye.upper, ...eye.lower]
    .map((index) => landmarks[index])
    .filter((point): point is Landmark => Boolean(point))
  const bounds = boundingBox(outline)
  const width = Math.max(bounds.maxX - bounds.minX, 0.001)
  const height = Math.max(bounds.maxY - bounds.minY, 0.001)
  return {
    x: clamp((irisCenter.x - bounds.minX) / width, 0, 1),
    y: clamp((irisCenter.y - bounds.minY) / height, 0, 1),
    confidence: clamp(Math.min(width / 0.04, height / 0.012), 0, 1),
  }
}

function averagePoint(points: Landmark[]): Landmark {
  return points.reduce(
    (acc, point) => ({
      x: acc.x + point.x / points.length,
      y: acc.y + point.y / points.length,
    }),
    { x: 0, y: 0 },
  )
}

function boundingBox(points: Landmark[]): {
  minX: number
  minY: number
  maxX: number
  maxY: number
} {
  return points.reduce(
    (box, point) => ({
      minX: Math.min(box.minX, point.x),
      minY: Math.min(box.minY, point.y),
      maxX: Math.max(box.maxX, point.x),
      maxY: Math.max(box.maxY, point.y),
    }),
    {
      minX: Number.POSITIVE_INFINITY,
      minY: Number.POSITIVE_INFINITY,
      maxX: Number.NEGATIVE_INFINITY,
      maxY: Number.NEGATIVE_INFINITY,
    },
  )
}

function distance(a: Landmark, b: Landmark): number {
  return Math.hypot(a.x - b.x, a.y - b.y)
}

function createEmptyResult(): EyeFrameResult {
  return {
    faceDetected: false,
    leftEyeState: 'NOT_DETECTED',
    rightEyeState: 'NOT_DETECTED',
    combinedState: 'UNKNOWN',
    ratios: { left: 0, right: 0 },
    confidence: 0,
    events: [],
    gaze: null,
    landmarks: null,
  }
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

function lerp(start: number, end: number, amount: number): number {
  return start + (end - start) * amount
}

function gazeDirection(
  dx: number,
  dy: number,
): 'LEFT' | 'RIGHT' | 'UP' | 'DOWN' {
  if (Math.abs(dx) > Math.abs(dy)) {
    return dx > 0 ? 'RIGHT' : 'LEFT'
  }
  return dy > 0 ? 'DOWN' : 'UP'
}

/** 이벤트 카운트에 반영해야 하는(눈 깜빡이기 게임 등에서 "1회"로 세는) 이벤트 타입인지 판별한다. */
export function isCountableBlinkEvent(type: EyeEventType): boolean {
  return type === 'BLINK' || type === 'FAST_BLINK' || type === 'DOUBLE_BLINK'
}
