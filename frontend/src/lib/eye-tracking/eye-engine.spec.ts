import { describe, expect, it } from 'vitest'
import { EyeInteractionEngine, type Landmark } from './eye-engine'

/**
 * `LEFT_EYE`/`RIGHT_EYE`(eye-engine.ts)가 참조하는 인덱스에만 의미 있는 좌표를 채운
 * 478개짜리 랜드마크 배열을 만든다. 나머지 인덱스는 게이즈/눈 상태 계산에 안 쓰이므로 0으로
 * 둬도 무방하다.
 *
 * 눈 하나당: 가로(코너 사이)=1.0, 세로 간격을 조절해서 EAR(눈 세로/가로 비율)을 만든다.
 * 기본 프로필 기준 openRatio=0.22/closedRatio=0.08이므로, 뜬 눈은 ratio≈0.25, 감은 눈은
 * ratio≈0.03으로 만들어 확실히 OPEN/CLOSED로 분류되게 한다.
 */
function makeLandmarks(options: {
  leftOpen: boolean
  rightOpen: boolean
}): Landmark[] {
  const landmarks: Landmark[] = Array.from({ length: 478 }, () => ({
    x: 0,
    y: 0,
  }))

  function setEye(
    corners: [number, number],
    upper: [number, number],
    lower: [number, number],
    open: boolean,
  ) {
    const half = open ? 0.125 : 0.015 // sum of two verticals ≈ 2*half → ratio ≈ 2*half
    landmarks[corners[0]] = { x: 0, y: 0 }
    landmarks[corners[1]] = { x: 1, y: 0 }
    landmarks[upper[0]] = { x: 0.5, y: -half }
    landmarks[upper[1]] = { x: 0.5, y: -half }
    landmarks[lower[0]] = { x: 0.5, y: half }
    landmarks[lower[1]] = { x: 0.5, y: half }
  }

  setEye([33, 133], [160, 158], [144, 153], options.leftOpen)
  setEye([362, 263], [385, 387], [380, 373], options.rightOpen)

  // iris 포인트(468~477)는 게이즈 추정에만 쓰이니 코너 근처에 아무 값이나 둔다.
  for (let index = 468; index < 478; index += 1) {
    landmarks[index] = { x: 0.5, y: 0 }
  }

  return landmarks
}

const BOTH_OPEN = makeLandmarks({ leftOpen: true, rightOpen: true })
const LEFT_CLOSED = makeLandmarks({ leftOpen: false, rightOpen: true })

describe('eye-engine: EyeInteractionEngine', () => {
  it('버그 수정: LEFT_WINK 이벤트 시각은 눈을 뜬 시점이 아니라 감기 시작한 시점이다', () => {
    // 리듬게임이 event.occurredAt을 그대로 판정 시각으로 쓰기 때문에, 이 시각이 "눈을 감기
    // 시작한 순간"과 최대한 가까워야 판정이 정확하다. 예전엔 "눈을 다시 뜬 시점"을 썼어서,
    // 감고 있던 시간(최소 70ms + 실제 눈 감은 시간)만큼 판정이 항상 늦었다.
    const engine = new EyeInteractionEngine()

    engine.processFrame(BOTH_OPEN, 1000) // 기준 프레임(양쪽 다 뜸)
    engine.processFrame(LEFT_CLOSED, 1050) // 왼쪽 감기 시작(closure.startedAt = 1050)
    engine.processFrame(LEFT_CLOSED, 1100) // 계속 감고 있음
    const result = engine.processFrame(BOTH_OPEN, 1150) // 다시 뜸 → 이벤트 확정 시점

    const leftWink = result.events.find((event) => event.type === 'LEFT_WINK')
    expect(leftWink).toBeDefined()
    // 감기 시작 시점(1050)이어야 한다 — 뜬 시점(1150)이면 예전 버그가 되돌아온 것이다.
    expect(leftWink?.occurredAt).toBe(1050)
    // durationMs(실제 감은 시간)는 여전히 정확해야 한다: 1150 - 1050 = 100.
    expect(leftWink?.durationMs).toBe(100)
  })

  it('양쪽 다 뜬 상태가 계속되면 아무 이벤트도 안 난다', () => {
    const engine = new EyeInteractionEngine()
    engine.processFrame(BOTH_OPEN, 1000)
    const result = engine.processFrame(BOTH_OPEN, 1050)
    expect(result.events).toHaveLength(0)
    expect(result.combinedState).toBe('BOTH_OPEN')
  })

  it('얼굴이 인식되지 않으면(랜드마크 부족) faceDetected가 false다', () => {
    const engine = new EyeInteractionEngine()
    const result = engine.processFrame([], 1000)
    expect(result.faceDetected).toBe(false)
    expect(result.combinedState).toBe('UNKNOWN')
  })
})
