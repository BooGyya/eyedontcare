import { describe, expect, it } from 'vitest'
import {
  addPointToStroke,
  applyDrawRoundResult,
  beginJudging,
  DRAWING_ALL_WORDS,
  DRAWING_BASE_SCORE,
  DRAWING_ROUND_DIFFICULTY,
  DRAWING_ROUND_DURATION_MS,
  DRAWING_TOTAL_ROUNDS,
  isDrawGameFinished,
  makeInitialDrawGameState,
  normalizeAnswer,
  pickWordsForGame,
  reportDrawJudgingError,
  startDrawRound,
  tickDrawRoundTimer,
  type VisionRecognition,
} from './draw-core'

function recognition(
  overrides: Partial<VisionRecognition> = {},
): VisionRecognition {
  return {
    label: '하트',
    confidence: 0.9,
    isTarget: true,
    reason: 'AI가 하트 모양을 인식했어요.',
    candidates: [...DRAWING_ALL_WORDS],
    ...overrides,
  }
}

describe('draw-core', () => {
  it('pickWordsForGame은 라운드 수만큼(EASY→MEDIUM→HARD 순서로) 단어를 뽑는다', () => {
    const words = pickWordsForGame(() => 0)
    expect(words).toHaveLength(DRAWING_TOTAL_ROUNDS)
    // random()이 항상 0이면 각 난이도 목록의 첫 단어가 뽑힌다.
    expect(words[0]).toBe('달') // EASY[0]
    expect(words[1]).toBe('사과') // MEDIUM[0]
    expect(words[2]).toBe('배') // HARD[0]
  })

  it('라운드가 올라갈수록 난이도가 EASY→MEDIUM→HARD 순서로 상승한다', () => {
    const state = makeInitialDrawGameState()
    const words = ['하트', '나무', '배']

    startDrawRound(state, words)
    expect(state.round).toBe(1)
    expect(state.difficulty).toBe('EASY')
    expect(state.prompt).toBe('하트')
    expect(state.remainingMs).toBe(DRAWING_ROUND_DURATION_MS)

    applyDrawRoundResult(state, recognition(), '')
    startDrawRound(state, words)
    expect(state.difficulty).toBe('MEDIUM')

    applyDrawRoundResult(state, recognition(), '')
    startDrawRound(state, words)
    expect(state.difficulty).toBe('HARD')
  })

  it('3라운드를 마치면 finished 상태가 된다', () => {
    const state = makeInitialDrawGameState()
    const words = ['하트', '나무', '배']

    for (let round = 0; round < DRAWING_ROUND_DIFFICULTY.length; round += 1) {
      startDrawRound(state, words)
      applyDrawRoundResult(state, recognition(), '')
    }

    expect(isDrawGameFinished(state)).toBe(true)
    expect(state.history).toHaveLength(3)
  })

  it('AI가 맞히면 난이도별 기본점수 + 시간보너스 + confidence보너스를 받는다', () => {
    const state = makeInitialDrawGameState()
    startDrawRound(state, ['하트', '나무', '자동차'])
    state.remainingMs = 50_000 // 50초 남음

    const result = applyDrawRoundResult(
      state,
      recognition({ confidence: 0.8 }),
      '',
    )

    // EASY 기본 100 + 시간보너스 round(50*1.2)=60 + confidence보너스 round(0.8*30)=24
    expect(result.score).toBe(DRAWING_BASE_SCORE.EASY + 60 + 24)
    expect(result.aiCorrect).toBe(true)
    expect(result.success).toBe(true)
  })

  it('AI는 못 맞혔지만 직접 입력한 정답이 맞으면 70% 부분점수 + 시간보너스를 받는다(confidence 보너스만 없음)', () => {
    const state = makeInitialDrawGameState()
    startDrawRound(state, ['하트', '나무', '자동차'])
    state.remainingMs = 50_000

    const result = applyDrawRoundResult(
      state,
      recognition({ isTarget: false, label: '동그라미', confidence: 0.3 }),
      '하트',
    )

    expect(result.aiCorrect).toBe(false)
    expect(result.answerCorrect).toBe(true)
    expect(result.success).toBe(true)
    // 70% 부분점수 round(100*0.7)=70 + 시간보너스 round(50*1.2)=60, confidence 보너스는 0
    expect(result.score).toBe(Math.round(DRAWING_BASE_SCORE.EASY * 0.7) + 60)
  })

  it('둘 다 틀리면 0점이다', () => {
    const state = makeInitialDrawGameState()
    startDrawRound(state, ['하트', '나무', '자동차'])

    const result = applyDrawRoundResult(
      state,
      recognition({ isTarget: false, label: '나무', confidence: 0.4 }),
      '별',
    )

    expect(result.success).toBe(false)
    expect(result.score).toBe(0)
  })

  it('tickDrawRoundTimer는 시간이 다 되면 true를 반환한다', () => {
    const state = makeInitialDrawGameState()
    startDrawRound(state, ['하트', '나무', '자동차'])

    expect(tickDrawRoundTimer(state, 1000)).toBe(false)
    expect(state.remainingMs).toBe(DRAWING_ROUND_DURATION_MS - 1000)

    expect(tickDrawRoundTimer(state, DRAWING_ROUND_DURATION_MS)).toBe(true)
    expect(state.remainingMs).toBe(0)
  })

  it('beginJudging/reportDrawJudgingError는 라운드 상태를 오가며 재시도할 수 있게 한다', () => {
    const state = makeInitialDrawGameState()
    startDrawRound(state, ['하트', '나무', '자동차'])

    beginJudging(state)
    expect(state.phase).toBe('judging')

    reportDrawJudgingError(state, 'AI 채점 서버에 연결할 수 없어요.')
    expect(state.phase).toBe('running')
    expect(state.errorMessage).toBe('AI 채점 서버에 연결할 수 없어요.')
  })

  it('addPointToStroke: 너무 멀리 떨어진 점은 새 획으로 끊는다', () => {
    const list: Parameters<typeof addPointToStroke>[0] = []
    let active = addPointToStroke(
      list,
      null,
      { x: 0.1, y: 0.1 },
      { color: '#000', width: 4 },
    )
    active = addPointToStroke(
      list,
      active,
      { x: 0.9, y: 0.9 },
      { color: '#000', width: 4 },
    )

    expect(list).toHaveLength(2) // 멀리 떨어져서 새 획이 생겼다
    expect(active?.points).toHaveLength(1)
  })

  it('addPointToStroke: allowBridge를 주면 멀리 떨어져도 같은 획으로 이어붙인다', () => {
    const list: Parameters<typeof addPointToStroke>[0] = []
    let active = addPointToStroke(
      list,
      null,
      { x: 0.1, y: 0.1 },
      { color: '#000', width: 4 },
    )
    active = addPointToStroke(
      list,
      active,
      { x: 0.9, y: 0.9 },
      { color: '#000', width: 4, allowBridge: true },
    )

    expect(list).toHaveLength(1)
    expect(active?.points).toHaveLength(2)
  })

  it('addPointToStroke: 펜 색이 바뀌면 진행 중인 획을 끊고 새 색의 획을 시작한다', () => {
    const list: Parameters<typeof addPointToStroke>[0] = []
    let active = addPointToStroke(
      list,
      null,
      { x: 0.5, y: 0.5 },
      { color: '#000', width: 4 },
    )
    // 같은 색이면 이어질 거리(0.22 미만)지만, 색이 다르므로 새 획이 생겨야 한다.
    active = addPointToStroke(
      list,
      active,
      { x: 0.52, y: 0.52 },
      { color: '#f00', width: 4 },
    )

    expect(list).toHaveLength(2)
    expect(list[0]?.color).toBe('#000')
    expect(active?.color).toBe('#f00')
    expect(active?.points).toHaveLength(1)
  })

  it('addPointToStroke: 손떨림 수준으로 가까운 점은 무시한다', () => {
    const list: Parameters<typeof addPointToStroke>[0] = []
    let active = addPointToStroke(
      list,
      null,
      { x: 0.5, y: 0.5 },
      { color: '#000', width: 4 },
    )
    active = addPointToStroke(
      list,
      active,
      { x: 0.5001, y: 0.5001 },
      { color: '#000', width: 4 },
    )

    expect(active?.points).toHaveLength(1)
  })

  it('normalizeAnswer는 공백/대소문자를 무시하고 비교한다', () => {
    expect(normalizeAnswer('  Heart ')).toBe(normalizeAnswer('heart'))
    expect(normalizeAnswer('하 트')).toBe(normalizeAnswer('하트'))
  })
})
