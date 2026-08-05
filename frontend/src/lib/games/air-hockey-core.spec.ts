import { describe, expect, it } from 'vitest'
import {
  AIR_HOCKEY_HEIGHT,
  AIR_HOCKEY_MATCH_DURATION_MS,
  AIR_HOCKEY_WIDTH,
  applyStrike,
  circleCircleOverlap,
  determineAirHockeyWinner,
  getGoalResult,
  launchPuck,
  makeInitialAirHockeyState,
  resetPuckForServe,
  resolveMalletCollision,
  scoreGoal,
  startAirHockeyMatch,
  updateAirHockeyMatch,
} from './air-hockey-core'

describe('air-hockey-core: 물리 엔진 (프로토타입에서 변경 없음)', () => {
  it('초기 상태는 화면 중앙에서 시작한다', () => {
    const state = makeInitialAirHockeyState()
    expect(state.bottom.x).toBe(AIR_HOCKEY_WIDTH / 2)
    expect(state.top.y < AIR_HOCKEY_HEIGHT / 2).toBe(true)
    expect(state.bottom.y > AIR_HOCKEY_HEIGHT / 2).toBe(true)
    expect(state.puck.held).toBe(true)
  })

  it('서브 위치를 잡고 발사하면 속도가 붙는다', () => {
    const state = makeInitialAirHockeyState()
    resetPuckForServe(state)
    expect(state.puck.x).toBe(state.bottom.x)
    expect(state.puck.y < state.bottom.y).toBe(true)

    launchPuck(state, 'bottom')
    expect(state.puck.held).toBe(false)
    expect(state.puck.vy < 0).toBe(true)
  })

  it('원판 겹침 판정', () => {
    expect(
      circleCircleOverlap({ x: 10, y: 10, r: 10 }, { x: 24, y: 10, r: 5 }),
    ).toBe(true)
    expect(
      circleCircleOverlap({ x: 10, y: 10, r: 3 }, { x: 24, y: 10, r: 5 }),
    ).toBe(false)
  })

  it('말렛과 충돌하면 퍽이 반대 방향으로 튕긴다', () => {
    const state = makeInitialAirHockeyState()
    state.puck.held = false
    state.puck.x = state.bottom.x
    state.puck.y = state.bottom.y - 18
    state.puck.vx = 0
    state.puck.vy = 360

    expect(resolveMalletCollision(state, 'bottom')).toBe(true)
    expect(state.puck.vy < 0).toBe(true)
  })

  it('패들이 퍽에 닿을 만큼 가까우면 타격으로 퍽이 가속된다', () => {
    const state = makeInitialAirHockeyState()
    state.puck.held = false
    state.puck.x = state.bottom.x
    state.puck.y = state.bottom.y - 50
    state.puck.vx = 0
    state.puck.vy = 120

    expect(applyStrike(state, 'bottom', 1000)).toBe(true)
    expect(state.puck.vy < 0).toBe(true)
  })

  it('패들이 퍽에서 멀면 타격해도 퍽이 움직이지 않는다', () => {
    const state = makeInitialAirHockeyState()
    state.puck.held = false
    state.puck.x = state.bottom.x
    state.puck.y = state.bottom.y - 300
    state.puck.vx = 0
    state.puck.vy = 120

    applyStrike(state, 'bottom', 1000)
    expect(state.puck.vy).toBe(120)
    expect(state.puck.vx).toBe(0)
  })

  it('골대 안으로 들어가면 골, 벽이면 wall로 판정한다', () => {
    const goalState = makeInitialAirHockeyState()
    goalState.puck.held = false
    goalState.puck.x = AIR_HOCKEY_WIDTH / 2
    goalState.puck.y = 4
    expect(getGoalResult(goalState)).toBe('bottom')

    const wallState = makeInitialAirHockeyState()
    wallState.puck.held = false
    wallState.puck.x = 80
    wallState.puck.y = 4
    expect(getGoalResult(wallState)).toBe('wall')
  })
})

describe('air-hockey-core: 종료 조건 (기획 확정본 — 5점 선취 대신 1분 제한시간)', () => {
  it('경기 시작 시 1분짜리 타이머가 걸린다', () => {
    const state = makeInitialAirHockeyState()
    startAirHockeyMatch(state, 0)
    expect(state.remainingMs).toBe(AIR_HOCKEY_MATCH_DURATION_MS)
    expect(state.remainingMs).toBe(60000)
    expect(state.gameOver).toBe(false)
  })

  it('골을 넣어도(5점을 넘어도) 시간이 남아있으면 게임이 끝나지 않는다', () => {
    const state = makeInitialAirHockeyState()
    startAirHockeyMatch(state, 0)

    for (let i = 0; i < 7; i += 1) {
      scoreGoal(state, 'bottom', 1000 * i)
    }

    expect(state.bottom.score).toBe(7)
    expect(updateAirHockeyMatch(state, 30_000)).toBe(false)
    expect(state.gameOver).toBe(false)
  })

  it('1분이 지나면 점수가 더 높은 쪽이 승리로 종료된다', () => {
    const state = makeInitialAirHockeyState()
    startAirHockeyMatch(state, 0)
    scoreGoal(state, 'bottom', 10_000)
    scoreGoal(state, 'bottom', 20_000)
    scoreGoal(state, 'top', 30_000)

    expect(updateAirHockeyMatch(state, 60_000)).toBe(true)
    expect(state.gameOver).toBe(true)
    expect(state.winner).toBe('bottom')
  })

  it('시간 종료 시 점수가 같으면 무승부로 처리된다', () => {
    const state = makeInitialAirHockeyState()
    startAirHockeyMatch(state, 0)
    scoreGoal(state, 'bottom', 10_000)
    scoreGoal(state, 'top', 20_000)

    updateAirHockeyMatch(state, 60_000)
    expect(determineAirHockeyWinner(state)).toBe('draw')
    expect(state.winner).toBe('draw')
  })
})
