/**
 * 리듬 게임 로직.
 *
 * `ai_game` 프로토타입의 `rhythm-core.js`를 그대로 이식했다. 기본값(30초 제한시간, 하트 5개,
 * 10콤보부터 1.5배·20콤보부터 2배 보너스)이 기획 확정본과 일치해 규칙 변경 없이 포팅했다.
 *
 * 왼쪽/오른쪽 눈 감음 이벤트를 `applyRhythmInput`에 넣어주고, 매 프레임 `updateRhythmRound`로
 * 노트 생성과 미스 판정을 진행시키면 된다.
 */

export const RHYTHM_LANES = ['LEFT_EYE', 'RIGHT_EYE'] as const
export const RHYTHM_INPUTS = ['LEFT_EYE', 'RIGHT_EYE', 'BOTH_EYES'] as const
export const DEFAULT_RHYTHM_BPM = 100
export const DEFAULT_RHYTHM_DURATION_MS = 30000
export const DEFAULT_RHYTHM_HEALTH = 5
export const NOTE_LOOKAHEAD_MS = 2400
export const NOTE_TRAVEL_MS = 2200
export const NOTE_TRAVEL_MIN_MS = 1200
export const NOTE_TRAVEL_MAX_MS = 5000
export const BEATMAP_LOOKAHEAD_BUFFER_MS = 500
export const DUAL_NOTE_CHANCE = 0.24
export const HIT_WINDOWS = { PERFECT: 90, GREAT: 160, GOOD: 260 } as const

export type RhythmLane = (typeof RHYTHM_LANES)[number]
export type RhythmInput = (typeof RHYTHM_INPUTS)[number]
export type RhythmPhase = 'ready' | 'running' | 'finished'
export type RhythmFinishReason =
  'TIME_UP' | 'MUSIC_END' | 'HEALTH_EMPTY' | 'MANUAL' | 'NONE'
export type RhythmNoteStatus = 'PENDING' | 'HIT' | 'MISS'
export type RhythmJudgement =
  'NONE' | 'PERFECT' | 'GREAT' | 'GOOD' | 'MISS' | 'EMPTY'

export interface RhythmNote {
  id: number
  lane: RhythmLane
  hitAt: number
  status: RhythmNoteStatus
  judgement: RhythmJudgement
  deltaMs: number
}

export interface RhythmBeatmapEntry {
  timeMs: number
  lanes: RhythmLane[]
  strength?: number
}

export interface RhythmGameState {
  phase: RhythmPhase
  finishReason: RhythmFinishReason
  bpm: number
  beatIntervalMs: number
  durationMs: number
  noteTravelMs: number
  startedAt: number
  endsAt: number
  remainingMs: number
  nextBeatAt: number
  beatmapEntries: RhythmBeatmapEntry[]
  nextBeatmapIndex: number
  nextNoteId: number
  notes: RhythmNote[]
  score: number
  combo: number
  maxCombo: number
  hits: number
  misses: number
  perfect: number
  great: number
  good: number
  maxHealth: number
  health: number
  lastJudgement: RhythmJudgement
  lastInput: RhythmInput | 'NONE'
  message: string
}

export interface RhythmInputResult {
  hit: boolean
  input: RhythmInput
  judgement: RhythmJudgement
  deltaMs: number
  scoreDelta: number
  notes: RhythmNote[]
}

interface RhythmOptions {
  bpm?: number
  durationMs?: number
  health?: number
  noteTravelMs?: number
  beatmapEntries?: RhythmBeatmapEntry[]
}

export function makeInitialRhythmState(
  options: RhythmOptions = {},
): RhythmGameState {
  const bpm = normalizeBpm(options.bpm ?? DEFAULT_RHYTHM_BPM)
  const durationMs = normalizeDurationMs(
    options.durationMs ?? DEFAULT_RHYTHM_DURATION_MS,
  )
  const health = normalizeHealth(options.health ?? DEFAULT_RHYTHM_HEALTH)
  const noteTravelMs = normalizeNoteTravelMs(
    options.noteTravelMs ?? NOTE_TRAVEL_MS,
  )
  const beatmapEntries = normalizeBeatmapEntries(options.beatmapEntries ?? [])
  return {
    phase: 'ready',
    finishReason: 'NONE',
    bpm,
    beatIntervalMs: bpmToIntervalMs(bpm),
    durationMs,
    noteTravelMs,
    startedAt: 0,
    endsAt: 0,
    remainingMs: durationMs,
    nextBeatAt: 0,
    beatmapEntries,
    nextBeatmapIndex: 0,
    nextNoteId: 1,
    notes: [],
    score: 0,
    combo: 0,
    maxCombo: 0,
    hits: 0,
    misses: 0,
    perfect: 0,
    great: 0,
    good: 0,
    maxHealth: health,
    health,
    lastJudgement: 'NONE',
    lastInput: 'NONE',
    message: '카운트다운이 끝나면 리듬 라운드가 시작됩니다.',
  }
}

export function startRhythmRound(
  state: RhythmGameState,
  now: number,
  options: RhythmOptions = {},
): void {
  const bpm = normalizeBpm(options.bpm ?? state.bpm)
  const durationMs = normalizeDurationMs(options.durationMs ?? state.durationMs)
  const health = normalizeHealth(options.health ?? state.maxHealth)
  const noteTravelMs = normalizeNoteTravelMs(
    options.noteTravelMs ?? state.noteTravelMs,
  )
  const beatmapEntries = normalizeBeatmapEntries(
    options.beatmapEntries ?? state.beatmapEntries,
  )
  state.phase = 'running'
  state.finishReason = 'NONE'
  state.bpm = bpm
  state.beatIntervalMs = bpmToIntervalMs(bpm)
  state.durationMs = durationMs
  state.noteTravelMs = noteTravelMs
  state.startedAt = now
  state.endsAt = now + durationMs
  state.remainingMs = durationMs
  state.nextBeatAt = now + noteTravelMs
  state.beatmapEntries = beatmapEntries
  state.nextBeatmapIndex = 0
  state.nextNoteId = 1
  state.notes = []
  state.score = 0
  state.combo = 0
  state.maxCombo = 0
  state.hits = 0
  state.misses = 0
  state.perfect = 0
  state.great = 0
  state.good = 0
  state.maxHealth = health
  state.health = health
  state.lastJudgement = 'NONE'
  state.lastInput = 'NONE'
  state.message = '리듬 라운드가 진행 중입니다.'
}

export function resetRhythmRound(state: RhythmGameState): void {
  Object.assign(
    state,
    makeInitialRhythmState({
      bpm: state.bpm,
      durationMs: state.durationMs,
      health: state.maxHealth,
      noteTravelMs: state.noteTravelMs,
      beatmapEntries: state.beatmapEntries,
    }),
  )
}

export function updateRhythmRound(
  state: RhythmGameState,
  now: number,
  random: () => number = Math.random,
): 'NONE' | 'UPDATED' | 'FINISHED' {
  if (state.phase !== 'running') {
    return 'NONE'
  }

  generateRhythmNotes(state, now, random)
  const changedByMiss = markExpiredNotes(state, now)
  state.remainingMs = Math.max(state.endsAt - now, 0)

  if (state.health <= 0) {
    finishRhythmRound(state, now, 'HEALTH_EMPTY')
    return 'FINISHED'
  }

  if (now >= state.endsAt) {
    finishRhythmRound(state, now, 'TIME_UP')
    return 'FINISHED'
  }

  return changedByMiss ? 'UPDATED' : 'NONE'
}

export function finishRhythmRound(
  state: RhythmGameState,
  now: number,
  reason: RhythmFinishReason = 'TIME_UP',
): void {
  if (state.phase === 'finished') {
    return
  }
  if (reason !== 'HEALTH_EMPTY' && reason !== 'MANUAL') {
    markExpiredNotes(state, Math.min(now, state.endsAt) + HIT_WINDOWS.GOOD + 1)
  }
  state.phase = 'finished'
  state.finishReason = reason
  state.remainingMs = 0
  state.combo = 0
  state.message = `${state.score}점 · 정확도 ${Math.round(getRhythmAccuracy(state))}%로 종료`
}

export function generateRhythmNotes(
  state: RhythmGameState,
  now: number,
  random: () => number = Math.random,
): number {
  if (state.phase !== 'running') {
    return 0
  }

  if (state.beatmapEntries.length > 0) {
    return generateBeatmapRhythmNotes(state, now)
  }

  const limit = Math.min(
    state.endsAt,
    now + Math.max(NOTE_LOOKAHEAD_MS, state.noteTravelMs + 200),
  )
  let generated = 0
  while (state.nextBeatAt <= limit) {
    const lanes = pickRandomNotePattern(random)
    for (const lane of lanes) {
      state.notes.push({
        id: state.nextNoteId,
        lane,
        hitAt: state.nextBeatAt,
        status: 'PENDING',
        judgement: 'NONE',
        deltaMs: 0,
      })
      state.nextNoteId += 1
      generated += 1
    }
    state.nextBeatAt += state.beatIntervalMs
  }
  return generated
}

export function generateBeatmapRhythmNotes(
  state: RhythmGameState,
  now: number,
): number {
  const limit = Math.min(
    state.endsAt,
    now +
      Math.max(
        NOTE_LOOKAHEAD_MS,
        state.noteTravelMs + BEATMAP_LOOKAHEAD_BUFFER_MS,
      ),
  )
  let generated = 0
  while (state.nextBeatmapIndex < state.beatmapEntries.length) {
    const entry = state.beatmapEntries[state.nextBeatmapIndex]
    const hitAt = state.startedAt + entry.timeMs
    if (hitAt > limit) {
      break
    }
    state.nextBeatmapIndex += 1
    if (hitAt > state.endsAt) {
      continue
    }
    for (const lane of entry.lanes) {
      state.notes.push({
        id: state.nextNoteId,
        lane,
        hitAt,
        status: 'PENDING',
        judgement: 'NONE',
        deltaMs: 0,
      })
      state.nextNoteId += 1
      generated += 1
    }
  }
  return generated
}

export function applyRhythmInput(
  state: RhythmGameState,
  input: RhythmInput,
  now: number,
): RhythmInputResult {
  if (state.phase !== 'running' || !isRhythmInput(input)) {
    return makeInputResult(false, input, 'EMPTY', 0, 0, [])
  }

  const targetLanes = inputToLanes(input)
  const hitNotes: RhythmNote[] = []
  let scoreDelta = 0
  let largestAbsDelta = 0
  let representativeDelta = 0

  for (const lane of targetLanes) {
    const candidate = findNearestPendingNote(state, lane, now)
    if (!candidate) {
      continue
    }

    const deltaMs = now - candidate.hitAt
    const judgement = judgeRhythmDelta(deltaMs)
    candidate.status = 'HIT'
    candidate.judgement = judgement
    candidate.deltaMs = deltaMs
    hitNotes.push(candidate)
    scoreDelta += scoreRhythmJudgement(judgement, state.combo + hitNotes.length)
    countJudgement(state, judgement)

    if (Math.abs(deltaMs) >= largestAbsDelta) {
      largestAbsDelta = Math.abs(deltaMs)
      representativeDelta = deltaMs
    }
  }

  state.lastInput = input

  if (hitNotes.length === 0) {
    state.lastJudgement = 'EMPTY'
    state.message = `${formatRhythmInput(input)} 입력했지만 가까운 노트가 없었어요.`
    return makeInputResult(false, input, 'EMPTY', 0, 0, [])
  }

  state.combo += hitNotes.length
  state.maxCombo = Math.max(state.maxCombo, state.combo)
  state.hits += hitNotes.length
  state.score += scoreDelta
  state.lastJudgement = combineJudgements(
    hitNotes.map((note) => note.judgement),
  )
  state.message = `${state.lastJudgement} ${formatSignedDelta(representativeDelta)} +${scoreDelta}`
  return makeInputResult(
    true,
    input,
    state.lastJudgement,
    representativeDelta,
    scoreDelta,
    hitNotes,
  )
}

export function judgeRhythmDelta(deltaMs: number): RhythmJudgement {
  const absoluteDelta = Math.abs(deltaMs)
  if (absoluteDelta <= HIT_WINDOWS.PERFECT) {
    return 'PERFECT'
  }
  if (absoluteDelta <= HIT_WINDOWS.GREAT) {
    return 'GREAT'
  }
  if (absoluteDelta <= HIT_WINDOWS.GOOD) {
    return 'GOOD'
  }
  return 'MISS'
}

/** 콤보 10 이상이면 1.5배, 20 이상이면 2배 — 기획서의 "10번 맞추면 콤보 보너스 시작"과 일치. */
export function scoreRhythmJudgement(
  judgement: RhythmJudgement,
  combo: number,
): number {
  const base =
    judgement === 'PERFECT'
      ? 100
      : judgement === 'GREAT'
        ? 70
        : judgement === 'GOOD'
          ? 40
          : 0
  if (base === 0) {
    return 0
  }
  const multiplier = combo >= 20 ? 2 : combo >= 10 ? 1.5 : 1
  return Math.round(base * multiplier)
}

export function getRhythmAccuracy(state: RhythmGameState): number {
  const total = state.hits + state.misses
  if (total === 0) {
    return 0
  }
  return (state.hits / total) * 100
}

export function getRhythmGrade(
  state: RhythmGameState,
): 'S' | 'A' | 'B' | 'C' | 'D' {
  const accuracy = getRhythmAccuracy(state)
  if (accuracy >= 95 && state.health > 0) {
    return 'S'
  }
  if (accuracy >= 85 && state.health > 0) {
    return 'A'
  }
  if (accuracy >= 70) {
    return 'B'
  }
  if (accuracy >= 50) {
    return 'C'
  }
  return 'D'
}

export function pickRandomNotePattern(
  random: () => number = Math.random,
): RhythmLane[] {
  const patternRoll = random()
  if (patternRoll < DUAL_NOTE_CHANCE) {
    return ['LEFT_EYE', 'RIGHT_EYE']
  }
  return [pickRandomLane(random)]
}

export function pickRandomLane(random: () => number = Math.random): RhythmLane {
  return random() < 0.5 ? 'LEFT_EYE' : 'RIGHT_EYE'
}

export function bpmToIntervalMs(bpm: number): number {
  return 60000 / normalizeBpm(bpm)
}

export function normalizeBpm(bpm: number): number {
  if (!Number.isFinite(bpm)) {
    return DEFAULT_RHYTHM_BPM
  }
  return Math.min(Math.max(Math.round(bpm), 60), 180)
}

export function normalizeDurationMs(durationMs: number): number {
  if (!Number.isFinite(durationMs)) {
    return DEFAULT_RHYTHM_DURATION_MS
  }
  return Math.min(Math.max(Math.round(durationMs), 5000), 10 * 60 * 1000)
}

export function normalizeHealth(health: number): number {
  if (!Number.isFinite(health)) {
    return DEFAULT_RHYTHM_HEALTH
  }
  return Math.min(Math.max(Math.round(health), 1), 10)
}

export function normalizeNoteTravelMs(noteTravelMs: number): number {
  if (!Number.isFinite(noteTravelMs)) {
    return NOTE_TRAVEL_MS
  }
  return Math.min(
    Math.max(Math.round(noteTravelMs), NOTE_TRAVEL_MIN_MS),
    NOTE_TRAVEL_MAX_MS,
  )
}

export function normalizeBeatmapEntries(
  entries: RhythmBeatmapEntry[],
): RhythmBeatmapEntry[] {
  if (!Array.isArray(entries)) {
    return []
  }
  return entries
    .map((entry) => ({
      timeMs: Math.max(Math.round(entry.timeMs), 0),
      lanes: Array.isArray(entry.lanes)
        ? entry.lanes.filter(
            (lane) => lane === 'LEFT_EYE' || lane === 'RIGHT_EYE',
          )
        : [],
      strength: Number.isFinite(entry.strength) ? Number(entry.strength) : 0,
    }))
    .filter((entry) => entry.lanes.length > 0)
    .sort((a, b) => a.timeMs - b.timeMs)
}

export function formatRhythmTime(milliseconds: number): string {
  const safe = Math.max(Math.ceil(milliseconds / 1000), 0)
  const minutes = Math.floor(safe / 60)
  const seconds = safe % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

export function formatRhythmLane(lane: RhythmLane | 'NONE'): string {
  if (lane === 'LEFT_EYE') {
    return '왼쪽 눈'
  }
  if (lane === 'RIGHT_EYE') {
    return '오른쪽 눈'
  }
  return '-'
}

export function formatRhythmInput(input: RhythmInput | 'NONE'): string {
  if (input === 'BOTH_EYES') {
    return '양쪽 눈'
  }
  return formatRhythmLane(input === 'NONE' ? 'NONE' : input)
}

export function formatSignedDelta(deltaMs: number): string {
  const sign = deltaMs >= 0 ? '+' : '-'
  return `${sign}${Math.abs(Math.round(deltaMs))}ms`
}

export function inputToLanes(input: RhythmInput): RhythmLane[] {
  if (input === 'BOTH_EYES') {
    return ['LEFT_EYE', 'RIGHT_EYE']
  }
  return [input]
}

function markExpiredNotes(state: RhythmGameState, now: number): boolean {
  let changed = false
  for (const note of state.notes) {
    if (note.status === 'PENDING' && now - note.hitAt > HIT_WINDOWS.GOOD) {
      note.status = 'MISS'
      note.judgement = 'MISS'
      note.deltaMs = now - note.hitAt
      state.misses += 1
      state.health = Math.max(state.health - 1, 0)
      state.combo = 0
      state.lastJudgement = 'MISS'
      state.lastInput = note.lane
      state.message = `MISS ${formatRhythmLane(note.lane)}`
      changed = true
    }
  }
  return changed
}

function findNearestPendingNote(
  state: RhythmGameState,
  lane: RhythmLane,
  now: number,
): RhythmNote | null {
  let nearest: RhythmNote | null = null
  let nearestDelta = Number.POSITIVE_INFINITY
  for (const note of state.notes) {
    if (note.status !== 'PENDING' || note.lane !== lane) {
      continue
    }
    const absoluteDelta = Math.abs(now - note.hitAt)
    if (absoluteDelta <= HIT_WINDOWS.GOOD && absoluteDelta < nearestDelta) {
      nearest = note
      nearestDelta = absoluteDelta
    }
  }
  return nearest
}

function countJudgement(
  state: RhythmGameState,
  judgement: RhythmJudgement,
): void {
  if (judgement === 'PERFECT') {
    state.perfect += 1
  } else if (judgement === 'GREAT') {
    state.great += 1
  } else if (judgement === 'GOOD') {
    state.good += 1
  }
}

function combineJudgements(judgements: RhythmJudgement[]): RhythmJudgement {
  if (judgements.length === 0) {
    return 'EMPTY'
  }
  if (judgements.includes('GOOD')) {
    return 'GOOD'
  }
  if (judgements.includes('GREAT')) {
    return 'GREAT'
  }
  return 'PERFECT'
}

function isRhythmInput(input: unknown): input is RhythmInput {
  return input === 'LEFT_EYE' || input === 'RIGHT_EYE' || input === 'BOTH_EYES'
}

function makeInputResult(
  hit: boolean,
  input: RhythmInput,
  judgement: RhythmJudgement,
  deltaMs: number,
  scoreDelta: number,
  notes: RhythmNote[],
): RhythmInputResult {
  return { hit, input, judgement, deltaMs, scoreDelta, notes }
}
