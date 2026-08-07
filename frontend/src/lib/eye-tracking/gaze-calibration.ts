/**
 * 시선 좌표 캘리브레이션.
 *
 * `ai_game` 프로토타입의 `gaze-calibration.js`를 그대로 이식했다. `eye-engine.ts`가 눈 랜드마크
 * 기준으로 뽑아낸 "원시(raw) 시선 좌표"는 카메라 각도·눈 크기 개인차 때문에 화면 좌표와 어긋난다.
 * 이 모듈은 사용자가 화면의 지정된 지점들을 바라볼 때의 원시 좌표를 모아(`addPair`), 2차 다항
 * 최소자승 회귀로 "원시 좌표 → 화면 좌표" 보정 함수를 학습한다(`fit`).
 *
 * 순수 계산 로직이라 DOM에 의존하지 않는다 — 유닛 테스트 가능.
 */

export interface Point {
  x: number
  y: number
  confidence?: number
}

export interface CalibrationPair {
  raw: Point
  target: Point
}

export interface GazeCalibrationProfile {
  coeffX: number[]
  coeffY: number[]
  pairs: CalibrationPair[]
  rawCenter: Point
  rawScale: Point
  inputMode: string
  createdAt: string
}

export interface CalibrationEvaluation {
  sampleCount: number
  beforeMeanPx: number
  afterMeanPx: number
  improvementRatio: number
}

/**
 * 화면을 3x3으로 나눈 9점 캘리브레이션 타깃(빠른 보정용, 기본값).
 *
 * ⚠️ "9개 점 사이 거리가 가까워서 정확도가 떨어진다"는 피드백으로 0.12~0.88(전체의 76%)에서
 * 0.05~0.95(전체의 90%)로 넓혔다 — 화면 가장자리에 훨씬 가깝게 배치해, 실제 게임 중 시선이
 * 갈 수 있는 범위 전체를 더 잘 커버한다.
 */
export const CALIBRATION_TARGETS: readonly Point[] = Object.freeze([
  { x: 0.05, y: 0.05 },
  { x: 0.5, y: 0.05 },
  { x: 0.95, y: 0.05 },
  { x: 0.05, y: 0.5 },
  { x: 0.5, y: 0.5 },
  { x: 0.95, y: 0.5 },
  { x: 0.05, y: 0.95 },
  { x: 0.5, y: 0.95 },
  { x: 0.95, y: 0.95 },
])

/** 화면을 5x5로 나눈 25점 캘리브레이션 타깃(정밀 보정용 — 그림그리기처럼 픽셀 단위 정확도가 필요할 때). */
export const DENSE_CALIBRATION_TARGETS: readonly Point[] = Object.freeze(
  createGridTargets(5, 0.08, 0.92),
)

export class GazeCalibrator {
  pairs: CalibrationPair[]
  profile: GazeCalibrationProfile | null

  constructor() {
    this.pairs = []
    this.profile = null
  }

  addPair(raw: Point, target: Point): void {
    this.pairs.push({
      raw: { x: raw.x, y: raw.y },
      target: { x: target.x, y: target.y },
    })
  }

  clear(): void {
    this.pairs = []
    this.profile = null
  }

  setProfile(profile: GazeCalibrationProfile | null): void {
    this.profile = profile
    this.pairs = profile?.pairs ?? []
  }

  /** 최소 6개 샘플이 있어야 2차 다항식(6개 계수)을 풀 수 있다. */
  fit(): GazeCalibrationProfile {
    if (this.pairs.length < 6) {
      throw new Error(
        '시선 캘리브레이션에는 최소 6개 이상의 유효 샘플이 필요합니다.',
      )
    }

    const rawStats = getRawStats(this.pairs)
    const features = this.pairs.map((pair) => featureVector(pair.raw, rawStats))
    const targetX = this.pairs.map((pair) => pair.target.x)
    const targetY = this.pairs.map((pair) => pair.target.y)
    this.profile = {
      coeffX: solveLeastSquares(features, targetX),
      coeffY: solveLeastSquares(features, targetY),
      rawCenter: rawStats.center,
      rawScale: rawStats.scale,
      inputMode: 'normalized-user-facing-v2',
      pairs: [...this.pairs],
      createdAt: new Date().toISOString(),
    }
    return this.profile
  }

  /** 원시 시선 좌표를 보정된 화면 좌표(0~1)로 변환한다. 아직 fit 전이면 원시값을 clamp만 해서 돌려준다. */
  predict(raw: Point | null): Point | null {
    if (!raw) {
      return null
    }
    if (!this.profile) {
      return {
        x: clamp(raw.x, 0, 1),
        y: clamp(raw.y, 0, 1),
        confidence: raw.confidence,
      }
    }
    const vector = featureVector(raw, {
      center: this.profile.rawCenter,
      scale: this.profile.rawScale,
    })
    return {
      x: clamp(dot(this.profile.coeffX, vector), 0, 1),
      y: clamp(dot(this.profile.coeffY, vector), 0, 1),
      confidence: raw.confidence,
    }
  }

  /** 보정 전/후 평균 오차(px)를 계산해 캘리브레이션 품질을 사용자에게 보여줄 때 쓴다. */
  evaluate(width = 1, height = 1): CalibrationEvaluation | null {
    if (!this.profile || this.profile.pairs.length === 0) {
      return null
    }

    const before: number[] = []
    const after: number[] = []
    for (const pair of this.profile.pairs) {
      const corrected = this.predict(pair.raw)
      if (!corrected) {
        continue
      }
      before.push(distancePx(pair.raw, pair.target, width, height))
      after.push(distancePx(corrected, pair.target, width, height))
    }

    return {
      sampleCount: after.length,
      beforeMeanPx: mean(before),
      afterMeanPx: mean(after),
      improvementRatio: before.length
        ? 1 - mean(after) / Math.max(mean(before), 0.001)
        : 0,
    }
  }
}

/** 프레임 단위로 튀는 원시 시선 좌표를 지수평활 + 점프/최소이동 필터로 부드럽게 만든다. */
export class GazeSmoother {
  private alpha: number
  private maxJump: number
  private minMove: number
  private current: Point | null

  constructor(
    options: { alpha?: number; maxJump?: number; minMove?: number } = {},
  ) {
    this.alpha = options.alpha ?? 0.28
    this.maxJump = options.maxJump ?? 0.34
    this.minMove = options.minMove ?? 0.002
    this.current = null
  }

  reset(): void {
    this.current = null
  }

  update(point: Point | null): Point | null {
    if (!point) {
      return this.current
    }
    if (!this.current) {
      this.current = { ...point }
      return this.current
    }

    const jump = Math.hypot(point.x - this.current.x, point.y - this.current.y)
    if (jump > this.maxJump) {
      return this.current
    }
    if (jump < this.minMove) {
      return this.current
    }

    this.current = {
      x: lerp(this.current.x, point.x, this.alpha),
      y: lerp(this.current.y, point.y, this.alpha),
      confidence: point.confidence,
    }
    return this.current
  }
}

export function averageGazeSamples(samples: Point[]): Point | null {
  if (samples.length === 0) {
    return null
  }
  const sortedX = [...samples.map((sample) => sample.x)].sort((a, b) => a - b)
  const sortedY = [...samples.map((sample) => sample.y)].sort((a, b) => a - b)
  return {
    x: trimmedMean(sortedX),
    y: trimmedMean(sortedY),
    confidence:
      samples.reduce((sum, sample) => sum + (sample.confidence ?? 0.5), 0) /
      samples.length,
  }
}

function featureVector(
  point: Point,
  stats: { center: Point; scale: Point },
): number[] {
  const x = (point.x - stats.center.x) / Math.max(stats.scale.x, 0.025)
  const y = (point.y - stats.center.y) / Math.max(stats.scale.y, 0.025)
  return [1, x, y, x * y, x * x, y * y]
}

function getRawStats(pairs: CalibrationPair[]): {
  center: Point
  scale: Point
} {
  const xs = pairs.map((pair) => pair.raw.x)
  const ys = pairs.map((pair) => pair.raw.y)
  const minX = Math.min(...xs)
  const maxX = Math.max(...xs)
  const minY = Math.min(...ys)
  const maxY = Math.max(...ys)
  return {
    center: {
      x: (minX + maxX) / 2,
      y: (minY + maxY) / 2,
    },
    scale: {
      x: Math.max((maxX - minX) / 2, 0.025),
      y: Math.max((maxY - minY) / 2, 0.025),
    },
  }
}

function solveLeastSquares(features: number[][], target: number[]): number[] {
  const size = features[0].length
  const matrix: number[][] = Array.from({ length: size }, () =>
    Array.from({ length: size }, () => 0),
  )
  const rhs: number[] = Array.from({ length: size }, () => 0)

  for (let rowIndex = 0; rowIndex < features.length; rowIndex += 1) {
    const row = features[rowIndex]
    for (let i = 0; i < size; i += 1) {
      rhs[i] += row[i] * target[rowIndex]
      for (let j = 0; j < size; j += 1) {
        matrix[i][j] += row[i] * row[j]
      }
    }
  }

  for (let i = 0; i < size; i += 1) {
    matrix[i][i] += 0.0001
  }

  return gaussianSolve(matrix, rhs)
}

function gaussianSolve(matrix: number[][], rhs: number[]): number[] {
  const n = rhs.length
  const augmented = matrix.map((row, index) => [...row, rhs[index]])

  for (let pivot = 0; pivot < n; pivot += 1) {
    let maxRow = pivot
    for (let row = pivot + 1; row < n; row += 1) {
      if (
        Math.abs(augmented[row][pivot]) > Math.abs(augmented[maxRow][pivot])
      ) {
        maxRow = row
      }
    }
    ;[augmented[pivot], augmented[maxRow]] = [
      augmented[maxRow],
      augmented[pivot],
    ]

    const divisor = augmented[pivot][pivot] || 1e-8
    for (let column = pivot; column <= n; column += 1) {
      augmented[pivot][column] /= divisor
    }

    for (let row = 0; row < n; row += 1) {
      if (row === pivot) {
        continue
      }
      const factor = augmented[row][pivot]
      for (let column = pivot; column <= n; column += 1) {
        augmented[row][column] -= factor * augmented[pivot][column]
      }
    }
  }

  return augmented.map((row) => row[n])
}

function dot(coefficients: number[], vector: number[]): number {
  return coefficients.reduce(
    (sum, coefficient, index) => sum + coefficient * vector[index],
    0,
  )
}

function trimmedMean(sortedValues: number[]): number {
  const trim = Math.floor(sortedValues.length * 0.15)
  const kept = sortedValues.slice(trim, sortedValues.length - trim || undefined)
  return kept.reduce((sum, value) => sum + value, 0) / kept.length
}

function lerp(start: number, end: number, amount: number): number {
  return start + (end - start) * amount
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

function createGridTargets(size: number, min: number, max: number): Point[] {
  const targets: Point[] = []
  for (let row = 0; row < size; row += 1) {
    for (let column = 0; column < size; column += 1) {
      targets.push({
        x: min + ((max - min) * column) / Math.max(size - 1, 1),
        y: min + ((max - min) * row) / Math.max(size - 1, 1),
      })
    }
  }
  return targets
}

function distancePx(a: Point, b: Point, width: number, height: number): number {
  return Math.hypot((a.x - b.x) * width, (a.y - b.y) * height)
}

function mean(values: number[]): number {
  if (values.length === 0) {
    return 0
  }
  return values.reduce((sum, value) => sum + value, 0) / values.length
}
