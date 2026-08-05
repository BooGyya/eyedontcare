<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import opponentProfileImage from '../assets/images/profiles/profile-smile.png'
import failedProfileImage from '../assets/images/profiles/profile-game-failed.png'
import blinkRecordMissedImage from '../assets/images/profiles/profile-blink-record-missed.png'
import airAiRobotImage from '../assets/images/games/game-air-ai-robot.png'
import airAiRobotLoseImage from '../assets/images/games/game-air-ai-robot-lose.png'
import duelLoserImage from '../assets/images/profiles/profile-duel-loser.png'
import duelWinnerImage from '../assets/images/profiles/profile-duel-winner.png'
import duelWinnerBannerImage from '../assets/images/profiles/profile-duel-winner-banner.png'
import drawResultBannerImage from '../assets/images/profiles/profile-draw-result-banner.png'
import drawResultMainImage from '../assets/images/profiles/profile-main-character-versus-draw.png'
import drawResultRivalImage from '../assets/images/profiles/profile-rival-character-versus-draw.png'
import GameResultShell from '../components/games/GameResultShell.vue'
import { gameDetails, isGameDetailId } from '../mocks/game-details'
import type { GameDetailId } from '../types/game-detail'
import type {
  GameResultDetailResponse,
  GameResultPlayMode,
} from '../types/gameResult'
import type { GameSessionMode } from '../types/gameplay'
import { useAuthStore } from '../stores/auth'
import {
  useLastGameResultStore,
  type LastGameResult,
  type LastGameOutcome,
} from '../stores/lastGameResult'
import { useGameSessionSocket } from '../composables/useGameSessionSocket'
import { resolveIdentity } from '../api/identity'
import { useToast } from '../composables/useToast'
import { DRAWING_DIFFICULTY_LABEL } from '../lib/games/draw-core'
import { getResult } from '../api/gameResult'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const gameModeLabels: Record<GameSessionMode, string> = {
  solo: '솔로 모드',
  ai: 'AI 대결',
  friends: '친구와 대결',
  random: '랜덤 매칭',
}
const game = computed(() => {
  const id = String(route.params.gameId ?? '')
  return isGameDetailId(id) ? gameDetails[id] : undefined
})
const mode = computed<GameSessionMode>(() => {
  const value = String(route.query.mode ?? 'solo')
  return ['solo', 'ai', 'friends', 'random'].includes(value)
    ? (value as GameSessionMode)
    : 'solo'
})

// 결과 화면은 게임 종료 시 저장한 실제 결과만 렌더링한다.
const lastResultStore = useLastGameResultStore()
const routeResultId = computed(() => {
  const value = Number(route.query.resultId)
  return Number.isInteger(value) && value > 0 ? value : undefined
})
const hasRealResult = computed(
  () =>
    game.value !== undefined &&
    lastResultStore.current !== null &&
    (lastResultStore.isFor(game.value.id, mode.value) ||
      lastResultStore.current.resultId === routeResultId.value),
)
const result = computed(() => {
  if (hasRealResult.value && lastResultStore.current)
    return lastResultStore.current
  return undefined
})

const persistedResultLoading = ref(false)

const GAME_SLUG_BY_NAME: Record<string, GameDetailId> = {
  EYEFIGHT: 'hold',
  BLINK: 'blink',
  DRAWING: 'draw',
  RHYTHM: 'rhythm',
  HOCKEY: 'air',
}

const MODE_BY_PLAY_MODE: Record<GameResultPlayMode, GameSessionMode> = {
  SOLO: 'solo',
  AI: 'ai',
  INVITE: 'friends',
  RANDOM: 'random',
}

const RESULT_STAT_LABELS: Record<string, string> = {
  survivalTimeMs: '생존 시간',
  blinkCount: '깜빡임 횟수',
  maxCombo: '최대 콤보',
  remainingHearts: '남은 하트',
}

function asRecord(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object'
    ? (value as Record<string, unknown>)
    : {}
}

function asOutcome(value: unknown): LastGameOutcome {
  return value === 'WIN' || value === 'LOSE' || value === 'DRAW'
    ? value
    : 'COMPLETED'
}

function formatPersistedValue(key: string, value: unknown): string {
  if (key === 'survivalTimeMs' && typeof value === 'number') {
    return `${(value / 1000).toFixed(1)}초`
  }
  if (key === 'blinkCount' && typeof value === 'number') {
    return `${value}`
  }
  if (typeof value === 'number') {
    return value.toLocaleString('ko-KR')
  }
  return String(value ?? '-')
}

function toLastGameResult(
  detail: GameResultDetailResponse,
): LastGameResult | null {
  const gameId = GAME_SLUG_BY_NAME[detail.gameName]
  const mode = MODE_BY_PLAY_MODE[detail.playMode]
  if (!gameId || !mode) return null

  const ownParticipant = detail.participants.find(
    (participant) => participant.slotNo === 1,
  )
  const gameResult = asRecord(detail.gameResult)
  const ownPayload = asRecord(gameResult[String(ownParticipant?.slotNo ?? 1)])
  const opponentPayload = asRecord(gameResult['2'])
  const score = ownPayload.score
  const opponentScore = opponentPayload.score ?? ownPayload.opponentScore
  const persistedOpponentNickname =
    typeof ownPayload.opponentNickname === 'string' &&
    ownPayload.opponentNickname.trim()
      ? ownPayload.opponentNickname.trim()
      : undefined
  const opponentParticipant = detail.participants.find(
    (participant) => participant.slotNo !== ownParticipant?.slotNo,
  )
  const opponentDisplayName =
    opponentParticipant?.participantType === 'USER'
      ? opponentParticipant.displayName
      : opponentParticipant?.participantType === 'GUEST'
        ? '게스트 플레이어'
        : opponentParticipant?.participantType === 'BOT'
          ? 'AI 플레이어'
          : undefined
  const stats = Object.entries(ownPayload)
    .filter(
      ([key]) =>
        key !== 'score' &&
        key !== 'drawRounds' &&
        key !== 'opponentScore' &&
        key !== 'opponentNickname',
    )
    .map(([key, value]) => ({
      label: RESULT_STAT_LABELS[key] ?? key,
      value: formatPersistedValue(key, value),
      opponentValue: undefined,
    }))

  const drawRounds = Array.isArray(ownPayload.drawRounds)
    ? (ownPayload.drawRounds as LastGameResult['drawRounds'])
    : undefined

  return {
    gameId,
    mode,
    outcome: asOutcome(ownParticipant?.outcome),
    headline: '게임 결과를 확인해보세요.',
    summary: '저장된 플레이 결과입니다.',
    scoreLabel: '최종 점수',
    score: formatPersistedValue('score', score),
    opponentScore:
      opponentScore === undefined
        ? undefined
        : formatPersistedValue('score', opponentScore),
    opponentNickname: persistedOpponentNickname ?? opponentDisplayName,
    stats,
    drawRounds,
    resultId: detail.resultId,
  }
}

async function loadPersistedResult(): Promise<void> {
  if (hasRealResult.value) return
  const resultId = routeResultId.value
  if (resultId === undefined) return

  persistedResultLoading.value = true
  try {
    const detail = await getResult(resultId)
    const restored = toLastGameResult(detail)
    if (restored) lastResultStore.set(restored)
  } catch {
    // 결과 조회 실패 시 빈 상태를 유지해 임의의 점수를 표시하지 않는다.
  } finally {
    persistedResultLoading.value = false
  }
}
const visibleResultStats = computed(() =>
  (result.value?.stats ?? []).filter(
    (stat) =>
      (game.value?.id !== 'blink' || stat.label !== '깜빡임 횟수') &&
      (game.value?.id !== 'blink' || stat.label !== '플레이 시간') &&
      (game.value?.id !== 'rhythm' || stat.label !== '정확도'),
  ),
)
/** 결과가 없는 직접 URL 접근은 빈 상태를 표시한다. */
const outcome = computed(() => lastResultStore.current?.outcome ?? 'UNKNOWN')
const outcomeHeadline = computed(() => {
  switch (outcome.value) {
    case 'WIN':
      return 'YOU WIN!'
    case 'LOSE':
      return 'YOU LOSE'
    case 'DRAW':
      return 'DRAW'
    default:
      return '결과 확인 중'
  }
})
const outcomeSummary = computed(() => {
  switch (outcome.value) {
    case 'WIN':
      return '멋진 플레이로 이번 대결을 이겼어요.'
    case 'LOSE':
      return '아쉽게 졌어요. 다음엔 더 잘할 수 있을 거예요!'
    case 'DRAW':
      return '무승부예요. 다음엔 승부를 가려봐요!'
    default:
      return '상대방과의 결과 동기화를 기다리고 있어요.'
  }
})
const myBadgeLabel = computed(() => {
  if (outcome.value === 'WIN') return 'WIN'
  if (outcome.value === 'LOSE') return 'LOSE'
  if (outcome.value === 'DRAW') return 'DRAW'
  return '확인 중'
})
const opponentBadgeLabel = computed(() => {
  if (outcome.value === 'WIN') return 'LOSE'
  if (outcome.value === 'LOSE') return 'WIN'
  if (outcome.value === 'DRAW') return 'DRAW'
  return '확인 중'
})
const title = computed(() =>
  game.value?.id === 'draw'
    ? '눈으로 그리기'
    : (game.value?.title.replace(/\s*\([^)]*\)\s*$/, '') ?? ''),
)
const isCompetitive = computed(
  () =>
    game.value?.id !== 'draw' &&
    ['ai', 'friends', 'random'].includes(mode.value),
)
const hasKnownOutcome = computed(() =>
  ['WIN', 'LOSE', 'DRAW'].includes(outcome.value),
)
const isShowcaseCompetitiveResult = computed(
  () =>
    ['air', 'hold', 'blink'].includes(game.value?.id ?? '') &&
    isCompetitive.value &&
    hasKnownOutcome.value,
)
const isPendingCompetitiveResult = computed(
  () => isCompetitive.value && !hasKnownOutcome.value,
)
const isDrawResult = computed(() => game.value?.id === 'draw')
const isFailedResult = computed(() => route.query.result === 'failed')
const isHoldRecordMissed = computed(
  () =>
    game.value?.id === 'hold' &&
    mode.value === 'solo' &&
    result.value?.isNewRecord === false,
)
const isBlinkSoloRecordMissed = computed(
  () =>
    game.value?.id === 'blink' &&
    mode.value === 'solo' &&
    result.value?.isNewRecord === false,
)
const isCompetitiveLoss = computed(
  () => isCompetitive.value && outcome.value === 'LOSE',
)
const isDrawOutcome = computed(
  () => isCompetitive.value && outcome.value === 'DRAW',
)
function formatAirScore(value: string | undefined): string {
  const score = String(value ?? '').replace(/[^\d.-]/g, '')
  return score || '-'
}
const airMyScore = computed(() => formatAirScore(result.value?.score))
const airOpponentScore = computed(() =>
  formatAirScore(result.value?.opponentScore),
)
const opponentResultImage = computed(() =>
  game.value?.id === 'hold' ? opponentProfileImage : game.value?.image,
)
const drawRoundResults = computed(() => result.value?.drawRounds ?? [])
const drawTotalScore = computed(() => {
  const roundsTotal = drawRoundResults.value.reduce(
    (total, round) => total + round.score,
    0,
  )
  if (roundsTotal > 0) return roundsTotal

  const score = String(result.value?.score ?? '').replace(/[^0-9.-]/g, '')
  return Number(score) || 0
})
const myNickname = computed(() => auth.displayName)
const opponentNickname = computed(
  () =>
    result.value?.opponentNickname ??
    (mode.value === 'ai' ? 'AI 플레이어' : '상대 플레이어'),
)
const holdRecordDifference = computed(() => {
  const previousBest = result.value?.previousBestScore
  const currentScore = Number.parseFloat(
    String(result.value?.score ?? '').replace(/[^0-9.-]/g, ''),
  )
  if (previousBest === null || previousBest === undefined) return '-'
  if (!Number.isFinite(currentScore)) return '-'
  const difference = currentScore - previousBest
  return `${difference >= 0 ? '+' : ''}${difference.toFixed(1)}초`
})

const soloScoreDisplay = ref('0')
const drawScoreDisplay = ref(0)
let soloCountFrame: number | undefined
let drawCountFrame: number | undefined

function prefersReducedMotion() {
  return (
    typeof globalThis.window !== 'undefined' &&
    typeof globalThis.window.matchMedia === 'function' &&
    globalThis.window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

function formatCount(value: number, decimals: number) {
  return value.toLocaleString('ko-KR', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  })
}

function animateSoloScore(raw: string) {
  const match = /^([\d,]+(?:\.\d+)?)/.exec(raw)
  if (!match || prefersReducedMotion()) {
    soloScoreDisplay.value = raw
    return
  }
  const suffix = raw.slice(match[0].length)
  const decimals = match[1].includes('.') ? match[1].split('.')[1].length : 0
  const final = Number(match[1].replace(/,/g, ''))
  const start = globalThis.performance.now()
  const duration = 900

  function tick(now: number) {
    const progress = Math.min((now - start) / duration, 1)
    const eased = 1 - (1 - progress) ** 3
    soloScoreDisplay.value = `${formatCount(final * eased, decimals)}${suffix}`
    if (progress < 1) soloCountFrame = globalThis.requestAnimationFrame(tick)
  }
  soloCountFrame = globalThis.requestAnimationFrame(tick)
}

function animateDrawScore(final: number) {
  if (prefersReducedMotion()) {
    drawScoreDisplay.value = final
    return
  }
  const start = globalThis.performance.now()
  const duration = 900

  function tick(now: number) {
    const progress = Math.min((now - start) / duration, 1)
    const eased = 1 - (1 - progress) ** 3
    drawScoreDisplay.value = Math.round(final * eased)
    if (progress < 1) drawCountFrame = globalThis.requestAnimationFrame(tick)
  }
  drawCountFrame = globalThis.requestAnimationFrame(tick)
}

onMounted(async () => {
  await loadPersistedResult()

  if (isDrawResult.value) {
    animateDrawScore(drawTotalScore.value)
  } else if (!isCompetitive.value && result.value) {
    animateSoloScore(result.value.score)
  }

  // 친구/랜덤 대결이면 재시작 합의를 위해 게임 세션 WS에 다시 접속한다.
  if (isMultiplayerBattle.value && battleRoomId.value) {
    const identity = resolveIdentity()
    if (identity) replaySession.connect(battleRoomId.value, identity)
  }
})

onBeforeUnmount(() => {
  if (soloCountFrame !== undefined)
    globalThis.cancelAnimationFrame(soloCountFrame)
  if (drawCountFrame !== undefined)
    globalThis.cancelAnimationFrame(drawCountFrame)
  if (hasRealResult.value) lastResultStore.clear()
  stopReplayResend()
})

// --- 재시작(다시 플레이) 양측 동의 ---
// 친구/랜덤 대결은 한 명만 눌러도 재시작되면 안 되고, 두 사람이 모두 "다시 플레이"를 눌러야 한다.
// 결과 화면에서 게임 세션 WS에 다시 접속해 REPLAY_REQUEST를 주고받고, 양쪽이 요청하면 함께 재입장한다.
const { showToast } = useToast()
const isMultiplayerBattle = computed(
  () => mode.value === 'friends' || mode.value === 'random',
)
const battleRoomId = computed(() => String(route.query.roomId ?? ''))
const replayRequested = ref(false)
const peerReplayRequested = ref(false)
let replayResendTimer: ReturnType<typeof globalThis.setInterval> | undefined

const replaySession = useGameSessionSocket({
  onPlayerEvent: (event) => {
    if (event.eventType !== 'REPLAY_REQUEST') return
    peerReplayRequested.value = true
    // 내가 이미 요청했다면 상대가 첫 요청을 놓쳤을 수 있으니 한 번 더 보내 확실히 알린다.
    if (replayRequested.value) replaySession.sendPlayerEvent('REPLAY_REQUEST')
    startReplayIfBothAgreed()
  },
  onParticipantLeft: () => {
    if (!replayRequested.value) return
    stopReplayResend()
    replayRequested.value = false
    showToast('상대가 나가서 다시 시작할 수 없어요.')
  },
})

function stopReplayResend() {
  if (!replayResendTimer) return
  globalThis.clearInterval(replayResendTimer)
  replayResendTimer = undefined
}

function navigateToReplay() {
  if (!game.value) return
  const playQuery = { ...route.query }
  delete playQuery.result
  playQuery.replay = '1'
  router.push({
    name: 'game-play',
    params: { gameId: game.value.id },
    query: playQuery,
  })
}

function startReplayIfBothAgreed() {
  if (!replayRequested.value || !peerReplayRequested.value) return
  stopReplayResend()
  navigateToReplay()
}

function replay() {
  if (!game.value) return
  // 솔로/AI는 즉시 재시작한다.
  if (!isMultiplayerBattle.value) {
    navigateToReplay()
    return
  }
  // 친구/랜덤 대결: 내 동의를 상대에게 알리고, 양쪽 동의가 모이면 함께 재입장한다.
  if (replayRequested.value) return
  replayRequested.value = true
  replaySession.sendPlayerEvent('REPLAY_REQUEST')
  // 상대가 아직 결과 화면 세션에 접속 전이면 첫 요청을 놓치므로 합의 전까지 주기적으로 재전송한다.
  replayResendTimer = globalThis.setInterval(
    () => replaySession.sendPlayerEvent('REPLAY_REQUEST'),
    700,
  )
  showToast('상대의 다시 하기 동의를 기다리고 있어요…')
  startReplayIfBothAgreed()
}
function viewRanking() {
  router.push({ name: 'ranking', query: { game: game.value?.id } })
}
function goToGames() {
  router.push({ name: 'games' })
}
</script>

<template>
  <GameResultShell
    v-if="game && result"
    :title="isShowcaseCompetitiveResult ? '게임' : title"
    :mode-label="gameModeLabels[mode]"
    :headline="
      isFailedResult ? '아쉽지만, 게임에 실패했어요!' : result.headline
    "
    :summary="
      isFailedResult
        ? '하트를 모두 사용해 게임이 종료되었어요.'
        : result.summary
    "
    :hide-outcome-intro="isCompetitive || isFailedResult || isHoldRecordMissed"
    :hide-header="isCompetitive || isDrawResult || isHoldRecordMissed"
    :hide-title="game.id === 'hold'"
  >
    <section
      class="result-grid"
      :class="[
        `result-grid--${game.id}`,
        {
          'result-grid--competitive': isCompetitive,
          'result-grid--failed': isFailedResult,
          'result-grid--hold-record-missed': isHoldRecordMissed,
          'result-grid--duel-loss': isCompetitive,
        },
      ]"
    >
      <template v-if="isShowcaseCompetitiveResult">
        <section class="air-result" :aria-label="`${title} 대결 결과`">
          <header
            class="duel-loss__hero"
            :class="{ 'duel-loss__hero--draw': isDrawOutcome }"
            aria-labelledby="air-duel-result-title"
          >
            <div>
              <p>{{ title }} 결과</p>
              <span>{{
                isDrawOutcome
                  ? '팽팽했어요!'
                  : isCompetitiveLoss
                    ? '아쉽다!'
                    : '최고에요!'
              }}</span>
              <h2 id="air-duel-result-title">
                {{
                  isDrawOutcome
                    ? 'DRAW'
                    : isCompetitiveLoss
                      ? 'YOU LOSE...'
                      : 'YOU WIN!'
                }}
              </h2>
              <strong>
                {{
                  isDrawOutcome
                    ? '마지막까지 팽팽한 승부였어요!'
                    : isCompetitiveLoss
                      ? game.id === 'blink'
                        ? '다음엔 더 빠르게 눈을 깜빡여 승리를 가져와 보세요!'
                        : '다음엔 더 정확한 시선 컨트롤로 승리를 가져와 보세요!'
                      : game.id === 'blink'
                        ? '상대보다 더 많이 깜빡이며 이번 대결을 이겼어요!'
                        : '멋진 시선 컨트롤로 이번 대결을 이겼어요!'
                }}
              </strong>
            </div>
            <img
              :src="
                isDrawOutcome
                  ? drawResultBannerImage
                  : isCompetitiveLoss
                    ? duelLoserImage
                    : duelWinnerBannerImage
              "
              :alt="
                isDrawOutcome
                  ? '무승부 결과 배너 캐릭터'
                  : isCompetitiveLoss
                    ? '아쉬워하는 내 플레이어 캐릭터'
                    : '승리한 내 플레이어 캐릭터'
              "
              draggable="false"
            />
          </header>
          <section
            class="air-duel-scoreboard"
            :class="{ 'air-duel-scoreboard--no-score': game.id === 'hold' }"
          >
            <article
              class="air-duel-scoreboard__player air-duel-scoreboard__player--mine"
            >
              <div>
                <strong>{{ myNickname }}</strong>
                <span>나</span>
              </div>
              <img
                :src="
                  isDrawOutcome
                    ? drawResultMainImage
                    : isCompetitiveLoss
                      ? failedProfileImage
                      : duelWinnerImage
                "
                :alt="
                  isDrawOutcome
                    ? '무승부 내 플레이어'
                    : isCompetitiveLoss
                      ? '아쉬워하는 내 플레이어'
                      : '승리한 내 플레이어'
                "
                draggable="false"
              />
              <b v-if="game.id === 'air'">{{ airMyScore }}</b>
              <b v-else-if="game.id === 'blink'">{{ result.score }}</b>
            </article>
            <div
              class="air-duel-scoreboard__outcome"
              :class="{
                'air-duel-scoreboard__outcome--lose': isCompetitiveLoss,
                'air-duel-scoreboard__outcome--draw': isDrawOutcome,
              }"
            >
              <span aria-hidden="true">{{ isDrawOutcome ? '🤝' : '🏆' }}</span>
              <strong>{{
                isDrawOutcome ? '무승부' : isCompetitiveLoss ? '패배' : '승리!'
              }}</strong>
            </div>
            <article
              class="air-duel-scoreboard__player air-duel-scoreboard__player--ai"
            >
              <div>
                <strong>{{ opponentNickname }}</strong>
                <span>{{ mode === 'ai' ? 'BOT' : '상대' }}</span>
              </div>
              <img
                :src="
                  isDrawOutcome
                    ? drawResultRivalImage
                    : mode === 'ai'
                      ? isCompetitiveLoss
                        ? airAiRobotImage
                        : airAiRobotLoseImage
                      : isCompetitiveLoss
                        ? duelWinnerImage
                        : duelLoserImage
                "
                :alt="
                  isDrawOutcome
                    ? '무승부 상대 플레이어'
                    : isCompetitiveLoss
                      ? '승리한 상대 플레이어'
                      : '아쉬워하는 상대 플레이어'
                "
                draggable="false"
              />
              <b v-if="game.id === 'air'">{{ airOpponentScore }}</b>
              <b v-else-if="game.id === 'blink'">{{
                result.opponentScore ?? '-'
              }}</b>
            </article>
          </section>
          <footer
            class="duel-loss__summary duel-loss__summary--air-ai air-result__summary"
          >
            <p>
              <b>{{
                isDrawOutcome
                  ? '팽팽한 한 판이었어요!'
                  : isCompetitiveLoss
                    ? '괜찮아요! 다시 도전해요!'
                    : '재미있는 한 판이었어요!'
              }}</b>
              <span>
                {{
                  isDrawOutcome
                    ? '다음 대결에서 승부를 가려봐요.'
                    : isCompetitiveLoss
                      ? '다음 대결에서는 더 좋은 결과를 만들어 보세요.'
                      : '함께해서 더 즐거웠어요. 또 대결해 보세요.'
                }}
              </span>
            </p>
            <button type="button" @click="replay">다시 플레이</button>
          </footer>
        </section>
      </template>

      <template v-else-if="isPendingCompetitiveResult">
        <section
          class="competitive-pending-result"
          aria-labelledby="pending-result-title"
        >
          <img :src="game.image" :alt="title + ' 결과'" draggable="false" />
          <p>{{ title }} 대결 결과</p>
          <h2 id="pending-result-title">결과 확인 중</h2>
          <span
            >상대 결과를 아직 받지 못했어요. 잠시 후 다시 확인해 주세요.</span
          >
          <div class="competitive-pending-result__score">
            <strong>{{ result.score }}</strong>
            <b>VS</b>
            <strong>{{ result.opponentScore ?? '-' }}</strong>
          </div>
          <button type="button" @click="replay">다시 플레이</button>
        </section>
      </template>

      <template v-else-if="isCompetitive">
        <section
          class="duel-loss"
          :class="{ 'duel-loss--draw': isDrawOutcome }"
          aria-labelledby="duel-loss-title"
        >
          <header
            class="duel-loss__hero"
            :class="{ 'duel-loss__hero--draw': isDrawOutcome }"
          >
            <div>
              <p>{{ title }} 결과</p>
              <span>{{
                isDrawOutcome
                  ? '팽팽했어요!'
                  : isCompetitiveLoss
                    ? '아쉽다!'
                    : '최고에요!'
              }}</span>
              <h2 id="duel-loss-title">
                {{
                  isDrawOutcome
                    ? 'DRAW'
                    : isCompetitiveLoss
                      ? 'YOU LOSE...'
                      : 'YOU WIN!'
                }}
              </h2>
              <strong>
                {{
                  isDrawOutcome
                    ? '마지막까지 팽팽한 승부였어요!'
                    : isCompetitiveLoss
                      ? game.id === 'air'
                        ? '다음엔 더 정확한 시선 컨트롤로 승리를 가져와 보세요!'
                        : '다음엔 더 정확한 눈 컨트롤로 승리를 가져와 보세요!'
                      : game.id === 'air'
                        ? '멋진 시선 컨트롤로 이번 대결을 이겼어요!'
                        : '멋진 눈 컨트롤로 이번 대결을 이겼어요!'
                }}
              </strong>
            </div>
            <img
              :src="
                isDrawOutcome
                  ? drawResultBannerImage
                  : isCompetitiveLoss
                    ? duelLoserImage
                    : duelWinnerBannerImage
              "
              :alt="
                isDrawOutcome
                  ? '무승부 결과 배너 캐릭터'
                  : isCompetitiveLoss
                    ? '아쉬워하는 내 플레이어 캐릭터'
                    : '승리한 내 플레이어 캐릭터'
              "
              draggable="false"
            />
          </header>

          <section
            v-if="game.id === 'air' && mode === 'ai'"
            class="air-duel-scoreboard"
            aria-label="에어하키 대결 결과"
          >
            <article
              class="air-duel-scoreboard__player air-duel-scoreboard__player--mine"
            >
              <div><strong>나</strong><span>YOU</span></div>
              <img
                :src="
                  isDrawOutcome
                    ? drawResultMainImage
                    : isCompetitiveLoss
                      ? failedProfileImage
                      : duelWinnerImage
                "
                :alt="
                  isDrawOutcome
                    ? '무승부 내 플레이어'
                    : isCompetitiveLoss
                      ? '아쉬워하는 내 플레이어'
                      : '승리한 내 플레이어'
                "
                draggable="false"
              />
              <b>{{ airMyScore }}</b>
            </article>
            <div
              class="air-duel-scoreboard__outcome"
              :class="{
                'air-duel-scoreboard__outcome--lose': isCompetitiveLoss,
                'air-duel-scoreboard__outcome--draw': isDrawOutcome,
              }"
            >
              <span aria-hidden="true">{{ isDrawOutcome ? '🤝' : '🏆' }}</span>
              <strong>{{
                isDrawOutcome ? '무승부' : isCompetitiveLoss ? '패배' : '승리!'
              }}</strong>
            </div>
            <article
              class="air-duel-scoreboard__player air-duel-scoreboard__player--ai"
            >
              <div><strong>AI</strong><span>BOT</span></div>
              <img
                :src="
                  isDrawOutcome
                    ? drawResultRivalImage
                    : isCompetitiveLoss
                      ? airAiRobotImage
                      : airAiRobotLoseImage
                "
                :alt="
                  isDrawOutcome
                    ? '무승부 상대 플레이어'
                    : isCompetitiveLoss
                      ? '웃고 있는 AI 로봇'
                      : '아쉬워하는 AI 로봇'
                "
                draggable="false"
              />
              <b>{{ airOpponentScore }}</b>
            </article>
          </section>

          <section
            v-else
            class="duel-loss__scoreboard"
            aria-label="대결 결과 비교"
          >
            <article class="duel-loss__player duel-loss__player--mine">
              <div class="duel-loss__identity">
                <strong>{{ myNickname }}</strong
                ><span>나</span>
              </div>
              <img
                :src="
                  isDrawOutcome
                    ? drawResultMainImage
                    : isCompetitiveLoss
                      ? failedProfileImage
                      : duelWinnerImage
                "
                :alt="
                  isDrawOutcome
                    ? '무승부 내 플레이어'
                    : isCompetitiveLoss
                      ? '아쉬워하는 내 플레이어'
                      : '승리한 내 플레이어'
                "
                draggable="false"
              />
            </article>

            <article class="duel-loss__stats">
              <div class="duel-loss__score-row">
                <strong>{{ result.score }}</strong
                ><b>VS</b><strong>{{ result.opponentScore ?? '-' }}</strong>
              </div>
              <dl>
                <div v-for="stat in visibleResultStats" :key="stat.label">
                  <dd>{{ stat.value }}</dd>
                  <dt>{{ stat.label }}</dt>
                  <dd>{{ stat.opponentValue ?? '-' }}</dd>
                </div>
              </dl>
            </article>

            <article class="duel-loss__player duel-loss__player--opponent">
              <div class="duel-loss__identity">
                <strong>{{ opponentNickname }}</strong>
                <span>{{
                  game.id === 'air' && mode === 'ai' ? 'BOT' : '상대'
                }}</span>
              </div>
              <img
                :src="
                  isDrawOutcome
                    ? drawResultRivalImage
                    : game.id === 'air' && mode === 'ai'
                      ? airAiRobotImage
                      : ['friends', 'random'].includes(mode)
                        ? isCompetitiveLoss
                          ? duelWinnerImage
                          : duelLoserImage
                        : opponentProfileImage
                "
                :alt="
                  isDrawOutcome
                    ? '무승부 상대 플레이어'
                    : isCompetitiveLoss
                      ? '승리한 상대 플레이어'
                      : '아쉬워하는 상대 플레이어'
                "
                draggable="false"
              />
            </article>
          </section>

          <footer
            class="duel-loss__summary"
            :class="{
              'duel-loss__summary--air-ai': game.id === 'air' && mode === 'ai',
              'duel-loss__summary--draw': isDrawOutcome,
            }"
          >
            <p>
              <b>{{
                isDrawOutcome
                  ? '팽팽한 한 판이었어요!'
                  : isCompetitiveLoss
                    ? '괜찮아요! 다시 도전해요!'
                    : '재미있는 한 판이었어요!'
              }}</b>
              <span>
                {{
                  isDrawOutcome
                    ? '다음 대결에서 승부를 가려봐요.'
                    : isCompetitiveLoss
                      ? game.id === 'air'
                        ? '패들을 움직이는 시선 타이밍을 조금 더 연습해 보세요!'
                        : '실수한 타이밍을 분석하고 연습하면 더 높은 점수를 달성할 수 있어요.'
                      : '친구와 함께해서 더 즐거웠어요. 또 대결해 보세요'
                }}
              </span>
            </p>
            <dl v-if="visibleResultStats.length && !isDrawOutcome">
              <div v-for="stat in visibleResultStats" :key="stat.label">
                <dt>{{ stat.label }}</dt>
                <dd>{{ stat.value }}</dd>
              </div>
            </dl>
            <button type="button" @click="replay">다시 플레이</button>
          </footer>
          <p class="duel-loss__note">
            {{
              mode === 'friends'
                ? '친구와의 게임은 랭킹에 반영되지 않습니다.'
                : '결과가 실제 플레이 기록으로 저장됐어요.'
            }}
          </p>
        </section>
      </template>

      <template v-else-if="isHoldRecordMissed">
        <section
          class="hold-record-missed"
          aria-labelledby="hold-record-missed-title"
        >
          <header class="hold-record-missed__header">
            <span>눈싸움 · 솔로 모드</span>
            <h2 id="hold-record-missed-title">게임 결과!</h2>
          </header>

          <div class="hold-record-missed__grid">
            <article class="hold-record-missed__hero">
              <span class="hold-record-missed__badge">아쉽지만...</span>
              <h3>신기록 갱신에 실패했어요!</h3>
              <p>다음엔 더 멋진 기록을 세워봐요!</p>
              <img
                :src="failedProfileImage"
                alt="아쉬워하는 플레이어 캐릭터"
                draggable="false"
              />
              <div class="hold-record-missed__time">
                <span>최종 생존 시간</span>
                <strong>{{ result.score }}</strong>
                <small
                  >이전 최고 기록
                  <b>{{ result.previousBestScore ?? '-' }}초</b></small
                >
              </div>
            </article>

            <div class="hold-record-missed__side">
              <article class="hold-record-missed__rank">
                <span>-</span>
                <p>내 순위</p>
                <strong>확인 불가</strong>
                <small>전체 랭킹에서 순위를 확인할 수 있어요.</small>
              </article>
              <article class="hold-record-missed__summary">
                <h3>기록 요약</h3>
                <dl>
                  <div>
                    <dt>내 최고 기록</dt>
                    <dd>{{ result.previousBestScore ?? '-' }}초</dd>
                  </div>
                  <div>
                    <dt>기록 차이</dt>
                    <dd>{{ holdRecordDifference }}</dd>
                  </div>
                  <div>
                    <dt>순위</dt>
                    <dd>-</dd>
                  </div>
                </dl>
              </article>
              <aside class="hold-record-missed__tip">
                <b>TIP!</b>
                <span
                  >집중력과 순발력이 높은 시간대에 도전해보세요!<br />짧은 휴식
                  후 다시 도전하면 더 좋은 결과를 얻을 수 있어요.</span
                >
              </aside>
              <button type="button" @click="replay">다시 도전하기</button>
            </div>
          </div>
          <footer class="hold-record-missed__encouragement">
            <b>포기하지 마세요!</b>
            <span>매번의 도전이 실력을 키워요. 당신은 이미 잘하고 있어요!</span>
          </footer>
        </section>
      </template>

      <template v-else-if="isFailedResult">
        <article class="failed-result" aria-labelledby="failed-result-title">
          <section class="failed-result__mascot" aria-hidden="true">
            <span class="failed-result__badge">GAME OVER</span>
            <img :src="failedProfileImage" alt="" draggable="false" />
          </section>
          <section class="failed-result__content">
            <p class="failed-result__eyebrow">아쉽지만...</p>
            <h2 id="failed-result-title">게임에 실패했어요!</h2>
            <div class="failed-result__heart-card">
              <strong>하트를 모두 사용했어요!</strong>
              <div class="failed-result__hearts" aria-label="사용한 하트 5개">
                <svg
                  v-for="index in 5"
                  :key="index"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path
                    d="M12 20.3 3.8 12.8A5.3 5.3 0 0 1 11.3 5L12 5.7l.7-.7a5.3 5.3 0 0 1 7.5 7.8z"
                    fill="currentColor"
                  />
                </svg>
              </div>
            </div>
            <p class="failed-result__description">
              하트를 모두 사용해서 더 이상 게임을 진행할 수 없어요.
            </p>
            <p class="failed-result__encouragement">
              다시 도전해서 더 좋은 기록을 노려보세요!
            </p>
          </section>
        </article>
      </template>

      <template v-else-if="isDrawResult">
        <header class="draw-final-hero">
          <span
            class="draw-final-hero__sparkle draw-final-hero__sparkle--left"
            aria-hidden="true"
          >
            <svg viewBox="0 0 24 24">
              <path
                d="M12 2c0 5 1.3 8.5 4 11-2.7 2.5-4 6-4 11-.1-5-1.4-8.5-4-11 2.6-2.5 3.9-6 4-11z"
                fill="currentColor"
              />
            </svg>
          </span>
          <span
            class="draw-final-hero__sparkle draw-final-hero__sparkle--right"
            aria-hidden="true"
          >
            <svg viewBox="0 0 24 24">
              <path
                d="M12 2c0 5 1.3 8.5 4 11-2.7 2.5-4 6-4 11-.1-5-1.4-8.5-4-11 2.6-2.5 3.9-6 4-11z"
                fill="currentColor"
              />
            </svg>
          </span>
          <h1>
            <span class="draw-final-hero__deco" aria-hidden="true">
              <svg viewBox="0 0 24 24">
                <path
                  d="M12 2c0 5 1.3 8.5 4 11-2.7 2.5-4 6-4 11-.1-5-1.4-8.5-4-11 2.6-2.5 3.9-6 4-11z"
                  fill="#c0a9ff"
                />
              </svg>
            </span>
            게임이 종료되었습니다!
            <span
              class="draw-final-hero__deco draw-final-hero__deco--tail"
              aria-hidden="true"
            >
              <svg viewBox="0 0 24 24">
                <path
                  d="M12 2c0 5 1.3 8.5 4 11-2.7 2.5-4 6-4 11-.1-5-1.4-8.5-4-11 2.6-2.5 3.9-6 4-11z"
                  fill="var(--color-gold)"
                />
              </svg>
            </span>
          </h1>
          <p>
            {{ drawRoundResults.length }}개 라운드의 그림 인식 결과를
            확인해보세요.
          </p>
        </header>

        <section class="draw-total-card" aria-label="최종 총점">
          <p class="draw-total-card__label">
            <span class="draw-total-card__label-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24">
                <path d="M7 4h10v5a5 5 0 0 1-10 0z" fill="var(--color-gold)" />
                <path
                  d="M7 5H4a3 3 0 0 0 3 5M17 5h3a3 3 0 0 1-3 5"
                  fill="none"
                  stroke="var(--color-gold)"
                  stroke-width="1.6"
                />
                <path d="M11 13h2v3h-2z" fill="var(--color-gold)" />
                <path
                  d="M8 19c0-1.8 1.8-2.7 4-2.7s4 .9 4 2.7v1H8z"
                  fill="var(--color-gold)"
                />
              </svg>
            </span>
            최종 총점
          </p>
          <strong class="draw-total-card__score"
            >{{ drawScoreDisplay }}점</strong
          >
          <p class="draw-total-card__message">신기록을 달성했어요!</p>
          <p v-if="result.isNewRecord === true" class="draw-total-card__meta">
            <b>NEW RECORD</b>
          </p>
        </section>

        <section class="draw-rounds" aria-label="라운드 결과">
          <h2>라운드 결과</h2>
          <div class="draw-rounds__grid">
            <article
              v-for="(round, index) in drawRoundResults"
              :key="round.prompt"
              class="draw-round-card"
            >
              <p class="draw-round-card__head">
                <span class="draw-round-card__head-group">
                  <span class="draw-round-card__label"
                    >ROUND {{ index + 1 }}</span
                  >
                  <span class="draw-round-card__difficulty">{{
                    DRAWING_DIFFICULTY_LABEL[round.difficulty]
                  }}</span>
                </span>
              </p>
              <p class="draw-round-card__prompt">
                <b
                  class="draw-round-card__answer"
                  :class="
                    round.success
                      ? 'draw-round-card__answer--correct'
                      : 'draw-round-card__answer--wrong'
                  "
                  >{{ round.success ? '정답' : '오답' }}</b
                >
                {{ round.prompt }}
              </p>
              <button
                type="button"
                class="draw-round-card__score"
                :aria-describedby="`draw-score-detail-${index}`"
              >
                {{ round.score }}점
                <span
                  :id="`draw-score-detail-${index}`"
                  class="draw-score-tooltip"
                  role="tooltip"
                >
                  <b>상세 점수</b>
                  <span
                    ><i>기본 점수</i><em>{{ round.baseScore }}점</em></span
                  >
                  <span
                    ><i>시간 보너스</i><em>{{ round.timeBonus }}점</em></span
                  >
                  <span
                    ><i>AI Confidence</i
                    ><em>{{ round.confidenceBonus }}점</em></span
                  >
                </span>
              </button>
            </article>
          </div>
        </section>

        <section class="draw-ranking" aria-label="랭킹 결과">
          <div class="draw-ranking__heading">
            <h2>랭킹 결과</h2>
            <button type="button" @click="viewRanking">
              전체 랭킹 보기
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M5 12h14M13 6l6 6-6 6"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </button>
          </div>
          <div class="draw-ranking__panel">
            <div class="draw-ranking__highlight">
              <span class="draw-ranking__medal" aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <path d="M8 2L5 9l4 2 2-5z" fill="#aeb6c2" />
                  <path d="M16 2l3 7-4 2-2-5z" fill="#aeb6c2" />
                  <circle
                    cx="12"
                    cy="15"
                    r="7"
                    fill="#aeb6c2"
                    stroke="#8f97a6"
                    stroke-width="1"
                  />
                  <path d="M8.5 15h7" stroke="#fff" stroke-width="1.5" />
                </svg>
              </span>
              <p>
                이번 게임 점수 <strong>{{ drawTotalScore }}점</strong>
              </p>
              <b>랭킹은 전체 랭킹에서 확인할 수 있어요.</b>
            </div>
            <ol class="draw-ranking__list">
              <li class="draw-ranking__list-item--mine">
                <b>-</b><span>{{ myNickname }} (나)</span
                ><strong>{{ drawTotalScore }}점</strong>
              </li>
            </ol>
          </div>
        </section>

        <section class="draw-final-footer">
          <button
            type="button"
            class="draw-final-footer__replay"
            @click="replay"
          >
            <span aria-hidden="true">
              <svg viewBox="0 0 24 24">
                <path
                  d="M20 11A8 8 0 1 0 18 16"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <path
                  d="M20 5v6h-6"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </span>
            다시 플레이
          </button>
          <button
            type="button"
            class="draw-final-footer__list"
            @click="goToGames"
          >
            게임 목록
          </button>
        </section>
      </template>

      <template v-else>
        <article
          v-if="!isCompetitive"
          class="result-hero"
          :class="{
            'result-hero--blink-record-missed': isBlinkSoloRecordMissed,
          }"
        >
          <img
            :src="
              isBlinkSoloRecordMissed
                ? blinkRecordMissedImage
                : game.mascotImage
            "
            :alt="
              isBlinkSoloRecordMissed
                ? '눈 깜빡이기 신기록 실패 캐릭터'
                : `${title} 결과 마스코트`
            "
            draggable="false"
          />
          <div class="score-block">
            <span>{{ result.scoreLabel }}</span
            ><strong>{{ soloScoreDisplay }}</strong>
          </div>
        </article>

        <template v-if="isCompetitive">
          <section class="versus-hero" aria-label="대결 승패 결과">
            <strong>{{ outcomeHeadline }}</strong>
            <p>{{ outcomeSummary }}</p>
          </section>
          <article class="versus-result">
            <section class="player-result player-result--mine">
              <b>{{ myBadgeLabel }}</b
              ><img
                :src="game.mascotImage"
                alt="내 플레이어 마스코트"
                draggable="false"
              />
              <span>{{ myNickname }} · 나</span
              ><strong>{{ myNickname }}</strong>
            </section>
            <section class="versus-stats">
              <div>
                <strong>{{ result.score }}</strong
                ><b>VS</b><strong>{{ result.opponentScore ?? '-' }}</strong>
              </div>
              <div v-for="stat in visibleResultStats" :key="stat.label">
                <span>{{ stat.value }}</span
                ><b>{{ stat.label }}</b
                ><span>{{ stat.opponentValue ?? stat.value }}</span>
              </div>
            </section>
            <section class="player-result player-result--opponent">
              <b>{{ opponentBadgeLabel }}</b
              ><img
                :src="opponentResultImage"
                alt="상대 플레이어 프로필 이미지"
                draggable="false"
              />
              <span>{{
                mode === 'ai' ? 'AI · BOT' : `${opponentNickname} · 상대`
              }}</span
              ><strong>{{ opponentNickname }}</strong>
            </section>
          </article>
        </template>

        <article class="result-summary">
          <template v-if="!isCompetitive">
            <div class="result-summary__heading">
              <p class="result-summary__eyebrow">기록 요약</p>
              <span v-if="result.isNewRecord === true" class="record-badge"
                >NEW RECORD</span
              >
            </div>
            <h3>{{ title }} 플레이 기록</h3>
          </template>
          <div v-else class="versus-summary">
            <div>
              <b>{{ outcomeHeadline }}</b
              ><span>{{ outcomeSummary }}</span>
            </div>
            <dl>
              <div v-for="stat in visibleResultStats" :key="stat.label">
                <dt>{{ stat.label }}</dt>
                <dd>{{ stat.value }}</dd>
              </div>
            </dl>
            <button type="button" class="primary" @click="replay">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M20 11A8 8 0 1 0 18 16"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <path
                  d="M20 5v6h-6"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              다시 플레이
            </button>
          </div>
          <dl
            v-if="!isCompetitive"
            :class="{
              'result-summary__stats--single': visibleResultStats.length === 1,
              'result-summary__stats--pair':
                game?.id === 'rhythm' && visibleResultStats.length === 2,
            }"
          >
            <div v-for="stat in visibleResultStats" :key="stat.label">
              <dt>{{ stat.label }}</dt>
              <dd>{{ stat.value }}</dd>
            </div>
          </dl>
          <p class="result-note">
            {{
              !isCompetitive
                ? '좋은 기록이에요. 다음 게임에서도 도전해 보세요!'
                : '결과가 실제 플레이 기록으로 저장됐어요.'
            }}
          </p>
        </article>
      </template>
    </section>
    <nav
      v-if="!isDrawResult && !isHoldRecordMissed"
      class="result-actions"
      aria-label="결과 화면 동작"
    >
      <button
        v-if="!isCompetitive"
        type="button"
        class="primary"
        @click="replay"
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path
            d="M20 11A8 8 0 1 0 18 16"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <path
            d="M20 5v6h-6"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
        다시 플레이</button
      ><button type="button" @click="viewRanking">랭킹 보기</button
      ><RouterLink to="/games">게임 목록</RouterLink>
    </nav>
  </GameResultShell>
  <section v-else-if="persistedResultLoading" class="missing">
    <h1>게임 결과를 불러오는 중이에요.</h1>
  </section>
  <section v-else class="missing">
    <h1>게임 결과를 찾을 수 없어요.</h1>
    <RouterLink to="/games">게임 목록으로</RouterLink>
  </section>
</template>

<style scoped>
@keyframes result-enter {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@keyframes result-enter-hero {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.94);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
@keyframes result-enter-card {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.96);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
@keyframes badge-pop {
  from {
    opacity: 0;
    transform: scale(0.7);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
.result-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(340px, 0.9fr);
  gap: 20px;
  text-align: left;
}
.result-grid--failed {
  display: block;
}
.result-grid--duel-loss {
  display: block;
}
.duel-loss {
  display: grid;
  gap: 20px;
  text-align: center;
}
.duel-loss__hero {
  position: relative;
  display: grid;
  min-height: 194px;
  grid-template-columns: minmax(0, 1fr) 250px;
  align-items: center;
  overflow: hidden;
  padding: 28px 54px;
  border: 1px solid #e6e3f5;
  border-radius: 24px;
  background: #f9f8ff;
  box-shadow: 0 12px 30px rgba(36, 44, 95, 0.05);
}
.duel-loss__hero > div {
  position: relative;
  z-index: 1;
  display: grid;
  justify-items: center;
}
.duel-loss__hero p {
  margin: 0 0 12px;
  color: var(--color-ink);
  font-family: var(--font-display);
  font-size: 20px;
}
.duel-loss__hero span {
  padding: 5px 20px;
  border-radius: 999px;
  color: #fff;
  background: #ee78a7;
  font-size: 13px;
  font-weight: 900;
}
.duel-loss__hero--draw span {
  background: #7b81e3;
}
.duel-loss__hero h2 {
  margin: 8px 0;
  color: #6744ed;
  font-family: var(--font-display);
  font-size: clamp(40px, 5vw, 62px);
  font-style: italic;
  letter-spacing: -0.07em;
  line-height: 1;
  text-shadow: 0 5px 12px rgba(103, 68, 237, 0.18);
}
.duel-loss__hero strong {
  color: #56628b;
  font-size: 14px;
}
.duel-loss__hero img {
  position: absolute;
  right: 8px;
  bottom: -12px;
  width: 240px;
  opacity: 0.34;
}
.duel-loss__hero--draw {
  min-height: 220px;
}
.duel-loss__hero--draw img {
  top: 50%;
  right: 16px;
  bottom: auto;
  width: 390px;
  opacity: 0.52;
  transform: translateY(-50%);
}
.air-duel-scoreboard {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 1fr;
  min-height: 335px;
  overflow: hidden;
  border: 1px solid #e2e4f4;
  border-radius: 24px;
  background: linear-gradient(90deg, #f7f6ff 0 50%, #fff7f8 50% 100%);
  box-shadow: 0 10px 24px rgba(36, 44, 95, 0.05);
}
.air-duel-scoreboard__player {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 126px;
  grid-template-rows: auto 1fr auto;
  align-items: center;
  padding: 32px 12% 28px;
  text-align: center;
}
.air-duel-scoreboard__player > div {
  grid-column: 1 / -1;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 9px;
}
.air-duel-scoreboard__player > div strong {
  color: #4b55df;
  font-family: var(--font-display);
  font-size: 27px;
}
.air-duel-scoreboard__player > div span {
  padding: 4px 9px;
  border: 1px solid currentColor;
  border-radius: 999px;
  color: #5f68df;
  font-size: 11px;
  font-weight: 900;
}
.air-duel-scoreboard__player img {
  grid-column: 1;
  grid-row: 2 / 4;
  align-self: end;
  justify-self: center;
  width: 100%;
  max-width: 165px;
  height: 180px;
  object-fit: contain;
}
.air-duel-scoreboard__player b {
  grid-column: 2;
  color: #4b43e7;
  font-family: var(--font-display);
}
.air-duel-scoreboard__player b {
  align-self: center;
  font-size: clamp(58px, 7vw, 86px);
  line-height: 0.9;
}
.air-duel-scoreboard--no-score .air-duel-scoreboard__player {
  grid-template-columns: 1fr;
}
.air-duel-scoreboard--no-score .air-duel-scoreboard__player img {
  grid-column: 1;
  grid-row: 2;
  align-self: center;
}
.air-duel-scoreboard__player--ai {
  grid-template-columns: 126px minmax(0, 1fr);
}
.air-duel-scoreboard__player--ai img {
  grid-column: 2;
}
.air-duel-scoreboard__player--ai b {
  grid-column: 1;
  color: #d44d42;
}
.air-duel-scoreboard__player--ai > div strong,
.air-duel-scoreboard__player--ai > div span {
  color: #c74439;
}
.air-duel-scoreboard__outcome {
  position: absolute;
  top: 50%;
  left: 50%;
  z-index: 1;
  display: grid;
  width: 168px;
  height: 168px;
  place-content: center;
  gap: 5px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 10px 30px rgba(84, 75, 205, 0.12);
  transform: translate(-50%, -50%);
  text-align: center;
}
.air-duel-scoreboard__outcome span {
  font-size: 34px;
}
.air-duel-scoreboard__outcome strong {
  color: #5744ed;
  font-family: var(--font-display);
  font-size: 42px;
}
.air-duel-scoreboard__outcome--lose strong {
  color: #d45475;
}
.air-duel-scoreboard__outcome--draw strong {
  color: #7b81e3;
}
.result-grid--air.result-grid--competitive {
  display: block;
}
.result-shell:has(.air-result) {
  padding-top: 20px;
}
.air-result {
  display: grid;
  gap: 20px;
}
.air-result .duel-loss__hero {
  min-height: 194px;
  padding: 28px 54px;
}
.air-result .duel-loss__hero img {
  width: 240px;
}
.air-result .duel-loss__hero--draw img {
  right: 16px;
  width: 430px;
}
.air-result .duel-loss__hero--draw {
  min-height: 220px;
}
.air-result .air-duel-scoreboard {
  min-height: 290px;
}
.air-result .air-duel-scoreboard__player {
  padding: 22px 15% 20px;
}
.air-result .air-duel-scoreboard__player img {
  max-width: 165px;
  height: 190px;
}
.air-result .air-duel-scoreboard__outcome {
  width: 150px;
  height: 150px;
}
.air-result .air-duel-scoreboard__outcome span {
  font-size: 32px;
}
.air-result .air-duel-scoreboard__outcome strong {
  font-size: 38px;
}
.air-result__summary {
  grid-template-columns: minmax(0, 1fr) minmax(250px, 310px);
}
.air-result__summary button {
  min-height: 62px;
  font-family: var(--font-display);
  font-size: 20px;
}
.competitive-pending-result button {
  min-height: 48px;
  padding: 0 22px;
  border: 0;
  border-radius: 12px;
  color: #fff;
  background: #7056e8;
  font-weight: 800;
  cursor: pointer;
}
.competitive-pending-result {
  display: grid;
  justify-items: center;
  gap: 12px;
  padding: 42px 24px;
  border: 1px solid #e2e4f4;
  border-radius: 24px;
  background: linear-gradient(145deg, #f8f7ff, #fff);
  text-align: center;
}
.competitive-pending-result > img {
  width: 130px;
  height: 130px;
  object-fit: contain;
}
.competitive-pending-result p,
.competitive-pending-result > span {
  margin: 0;
  color: var(--color-muted);
}
.competitive-pending-result h2 {
  margin: 0;
  color: var(--color-ink);
  font-family: var(--font-display);
  font-size: 32px;
}
.competitive-pending-result__score {
  display: flex;
  align-items: baseline;
  gap: 16px;
  margin: 8px 0;
}
.competitive-pending-result__score strong {
  color: var(--color-accent-blue);
  font-family: var(--font-display);
  font-size: 32px;
}
.competitive-pending-result__score b {
  color: var(--color-muted);
}
.duel-loss__scoreboard {
  display: grid;
  grid-template-columns: minmax(150px, 0.68fr) minmax(360px, 1.35fr) minmax(
      150px,
      0.68fr
    );
  gap: 18px;
  align-items: stretch;
}
.duel-loss--draw .duel-loss__scoreboard {
  min-height: 290px;
}
.duel-loss__player,
.duel-loss__stats,
.duel-loss__summary {
  border: 1px solid #e2e4f4;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(36, 44, 95, 0.05);
}
.duel-loss__player {
  display: grid;
  gap: 9px;
  justify-items: center;
  padding: 15px 14px;
}
.duel-loss--draw .duel-loss__player {
  padding: 22px 15% 20px;
}
.duel-loss__player > b {
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 900;
}
.duel-loss__identity {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
}
.duel-loss__identity strong {
  color: #634fe3;
  font-family: var(--font-display);
  font-size: 15px;
}
.duel-loss__identity span {
  padding: 4px 9px;
  border: 1px solid #bdb6f8;
  border-radius: 999px;
  color: #695ce7;
  font-size: 11px;
  font-weight: 900;
}
.duel-loss__player img {
  width: 100%;
  max-width: 142px;
  height: 130px;
  object-fit: contain;
}
.duel-loss--draw .duel-loss__player img {
  max-width: 165px;
  height: 190px;
}
.duel-loss__player > b {
  color: #634fe3;
  background: #f1efff;
}
.duel-loss__player--opponent {
  border-color: #f4d9e6;
}
.duel-loss__player--opponent .duel-loss__identity strong {
  color: #d64a78;
}
.duel-loss__player--opponent .duel-loss__identity span {
  border-color: #f2b6cb;
  color: #d64a78;
}
.duel-loss__player--opponent > b {
  color: #db427b;
  background: #fff0f6;
}
.duel-loss--draw .duel-loss__player--opponent {
  border-color: #e2e4f4;
}
.duel-loss--draw .duel-loss__player--opponent .duel-loss__identity strong {
  color: #634fe3;
}
.duel-loss--draw .duel-loss__player--opponent .duel-loss__identity span {
  border-color: #bdb6f8;
  color: #695ce7;
}
.duel-loss--draw .duel-loss__player--opponent > b {
  color: #634fe3;
  background: #f1efff;
}
.duel-loss__stats {
  overflow: hidden;
}
.duel-loss--draw .duel-loss__stats {
  display: grid;
  grid-template-rows: 128px minmax(0, 1fr);
}
.duel-loss__score-row,
.duel-loss__stats dl > div {
  display: grid;
  grid-template-columns: 1fr 78px 1fr;
  align-items: center;
  min-height: 56px;
  padding: 0 20px;
  border-top: 1px solid #ececf5;
}
.duel-loss__score-row {
  min-height: 92px;
  border-top: 0;
}
.duel-loss--draw .duel-loss__score-row {
  min-height: 0;
}
.duel-loss__score-row strong {
  color: #6147ed;
  font-size: clamp(28px, 3.6vw, 42px);
}
.duel-loss__score-row strong:last-child,
.duel-loss__stats dd:last-child {
  color: #e74d87;
}
.duel-loss--draw .duel-loss__score-row strong:last-child,
.duel-loss--draw .duel-loss__stats dd:last-child {
  color: #6147ed;
}
.duel-loss__score-row b,
.duel-loss__stats dt {
  color: #687294;
  font-size: 12px;
  font-weight: 900;
}
.duel-loss__stats dl {
  margin: 0;
}
.duel-loss--draw .duel-loss__stats dl {
  display: grid;
  grid-template-rows: repeat(2, minmax(0, 1fr));
}
.duel-loss--draw .duel-loss__stats dl > div {
  min-height: 0;
}
.duel-loss__stats dd {
  margin: 0;
  color: #6147ed;
  font-size: 19px;
  font-weight: 900;
}
.duel-loss__hearts-row dd {
  display: flex;
  justify-content: center;
  gap: 4px;
}
.duel-loss__hearts-row i {
  color: #e74d87;
  font-size: 21px;
  font-style: normal;
}
.duel-loss__hearts-row i.empty {
  color: #34384f;
}
.duel-loss__summary {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(250px, 1.1fr) 155px;
  align-items: center;
  gap: 18px;
  padding: 18px 24px;
  text-align: left;
}
.duel-loss__summary--air-ai {
  grid-template-columns: minmax(0, 1fr) 155px;
}
.duel-loss__summary p {
  display: grid;
  gap: 7px;
  margin: 0;
}
.duel-loss__summary p b {
  color: #6048e9;
  font-size: 18px;
}
.duel-loss__summary p span {
  color: #687294;
  font-size: 13px;
  line-height: 1.5;
}
.duel-loss__summary dl {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin: 0;
  padding: 12px;
  border-radius: 12px;
  background: #f7f6ff;
  text-align: center;
}
.duel-loss__summary dt {
  color: #7a83a0;
  font-size: 11px;
}
.duel-loss__summary dd {
  margin: 5px 0 0;
  color: var(--color-ink);
  font-weight: 900;
}
.duel-loss__summary button {
  min-height: 48px;
  border: 0;
  border-radius: 11px;
  color: #fff;
  background: #5c40ef;
  font: inherit;
  font-weight: 900;
  cursor: pointer;
}
.duel-loss__summary--draw {
  grid-template-columns: minmax(0, 1fr) 155px;
}
.duel-loss__summary--draw button {
  width: 100%;
  max-width: 155px;
  height: 48px;
  min-height: 48px;
  justify-self: end;
  font-family: var(--font-display);
  font-size: 20px;
}
.duel-loss__note {
  margin: 0;
  color: #7f74e5;
  font-size: 13px;
  font-weight: 800;
}
.result-grid--hold-record-missed {
  display: block;
}
.hold-record-missed {
  display: grid;
  gap: 16px;
  text-align: left;
}
.hold-record-missed__header {
  text-align: center;
}
.hold-record-missed__header span {
  display: inline-block;
  padding: 5px 11px;
  border-radius: 999px;
  color: #5b52e4;
  background: #f0efff;
  font-size: 12px;
  font-weight: 900;
}
.hold-record-missed__header h2 {
  margin: 6px 0 0;
  color: var(--color-ink);
  font-family: var(--font-display);
  font-size: clamp(30px, 4vw, 42px);
}
.hold-record-missed__grid {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(360px, 0.95fr);
  gap: 18px;
}
.hold-record-missed__hero,
.hold-record-missed__rank,
.hold-record-missed__summary,
.hold-record-missed__tip,
.hold-record-missed__encouragement {
  border: 1px solid #e2e4f4;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(36, 44, 95, 0.05);
}
.hold-record-missed__hero {
  position: relative;
  display: grid;
  min-height: 430px;
  grid-template-rows: auto auto auto minmax(0, 1fr) auto;
  justify-items: center;
  overflow: hidden;
  padding: 28px 28px 0;
  background: #f7f5ff;
  text-align: center;
}
.hold-record-missed__badge {
  padding: 7px 22px;
  border-radius: 8px;
  color: #fff;
  background: #7f8394;
  font-weight: 900;
}
.hold-record-missed__hero h3 {
  margin: 13px 0 0;
  color: #42475e;
  font-size: clamp(22px, 3vw, 31px);
}
.hold-record-missed__hero > p {
  margin: 6px 0 0;
  color: #55618a;
  font-size: 13px;
  font-weight: 700;
}
.hold-record-missed__hero > img {
  width: min(55%, 210px);
  height: 210px;
  object-fit: contain;
  align-self: end;
  opacity: 0.9;
}
.hold-record-missed__time {
  z-index: 1;
  width: min(100%, 390px);
  margin: -12px 0 10px;
  padding: 15px;
  border: 1px solid #e1e3f0;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.96);
}
.hold-record-missed__time span,
.hold-record-missed__time small {
  display: block;
  color: #6a7395;
  font-size: 12px;
  font-weight: 700;
}
.hold-record-missed__time strong {
  display: block;
  margin: 4px 0 6px;
  color: var(--color-ink);
  font-size: clamp(31px, 5vw, 45px);
  font-variant-numeric: tabular-nums;
}
.hold-record-missed__time b {
  color: #6656ed;
}
.hold-record-missed__side {
  display: grid;
  grid-template-rows: 1.35fr 0.85fr auto auto;
  gap: 14px;
}
.hold-record-missed__rank {
  display: grid;
  align-content: center;
  justify-items: center;
  padding: 20px;
  text-align: center;
}
.hold-record-missed__rank > span {
  display: grid;
  width: 52px;
  height: 52px;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: #a3a6b4;
  font-size: 24px;
  font-weight: 900;
}
.hold-record-missed__rank p {
  margin: 8px 0 2px;
  color: #59627e;
  font-size: 13px;
  font-weight: 800;
}
.hold-record-missed__rank strong {
  color: #6352ed;
  font-family: var(--font-display);
  font-size: 40px;
}
.hold-record-missed__rank small {
  margin-top: 7px;
  color: #58628c;
  font-size: 12px;
}
.hold-record-missed__summary {
  padding: 16px 20px;
}
.hold-record-missed__summary h3 {
  margin: 0 0 12px;
  color: var(--color-ink);
  font-size: 14px;
}
.hold-record-missed__summary dl {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin: 0;
}
.hold-record-missed__summary dl div {
  padding: 0 8px;
  border-left: 1px solid #e7e8f0;
  text-align: center;
}
.hold-record-missed__summary dl div:first-child {
  border-left: 0;
}
.hold-record-missed__summary dt {
  color: #74809d;
  font-size: 11px;
}
.hold-record-missed__summary dd {
  margin: 6px 0 0;
  color: var(--color-ink);
  font-weight: 900;
}
.hold-record-missed__summary dl div:nth-child(2) dd {
  color: #ef5b67;
}
.hold-record-missed__tip {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 13px 17px;
  background: #f7f6ff;
}
.hold-record-missed__tip b {
  color: #5a48e8;
  font-size: 14px;
}
.hold-record-missed__tip span {
  color: #647094;
  font-size: 11px;
  line-height: 1.45;
}
.hold-record-missed__side > button {
  min-height: 48px;
  border: 1px solid #7869ee;
  border-radius: 12px;
  color: #5544df;
  background: #fff;
  font: inherit;
  font-weight: 900;
  cursor: pointer;
}
.hold-record-missed__encouragement {
  display: flex;
  gap: 13px;
  align-items: center;
  padding: 17px 24px;
  background: #f7f6ff;
}
.hold-record-missed__encouragement b {
  color: #4f43de;
}
.hold-record-missed__encouragement span {
  color: #667193;
  font-size: 13px;
}
.failed-result {
  display: grid;
  grid-template-columns: minmax(260px, 0.8fr) minmax(0, 1.2fr);
  min-height: 430px;
  overflow: hidden;
  border: 1px solid #e6e3f5;
  border-radius: 26px;
  background: #fbfaff;
  box-shadow: 0 14px 34px rgba(36, 44, 95, 0.07);
  animation: result-enter 0.5s var(--ease-out) both;
}
.failed-result__mascot {
  position: relative;
  display: grid;
  min-height: 330px;
  place-items: end center;
  overflow: hidden;
  padding: 42px 24px 20px;
  background: #f4f2fd;
}
.failed-result__mascot::before {
  position: absolute;
  inset: auto 14% 12%;
  height: 38%;
  border-radius: 50%;
  background: #ebe7fc;
  content: '';
}
.failed-result__mascot img {
  position: relative;
  z-index: 1;
  width: min(100%, 280px);
  max-height: 300px;
  object-fit: contain;
  filter: saturate(0.52);
}
.failed-result__badge {
  position: absolute;
  top: 28px;
  left: 28px;
  z-index: 2;
  padding: 9px 12px;
  border: 2px solid #ef4e7b;
  border-radius: 10px;
  color: #ef3f70;
  background: #fff;
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 900;
  letter-spacing: 0.03em;
  transform: rotate(-8deg);
}
.failed-result__content {
  display: grid;
  align-content: center;
  justify-items: center;
  padding: 42px 44px;
  text-align: center;
}
.failed-result__eyebrow {
  margin: 0;
  color: #242c5f;
  font-size: 21px;
  font-weight: 800;
}
.failed-result__content h2 {
  margin: 8px 0 28px;
  color: var(--color-ink);
  font-family: var(--font-display);
  font-size: clamp(32px, 4vw, 48px);
  line-height: 1.2;
  letter-spacing: -0.05em;
}
.failed-result__heart-card {
  display: grid;
  width: min(100%, 430px);
  gap: 18px;
  padding: 28px 24px;
  border: 1px solid #e3e0f3;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 8px 22px rgba(43, 48, 95, 0.05);
}
.failed-result__heart-card strong {
  color: var(--color-ink);
  font-size: 20px;
}
.failed-result__hearts {
  display: flex;
  justify-content: center;
  gap: clamp(10px, 2vw, 18px);
  color: #565866;
}
.failed-result__hearts svg {
  width: clamp(28px, 3.2vw, 38px);
  height: clamp(28px, 3.2vw, 38px);
}
.failed-result__description {
  max-width: 390px;
  margin: 26px 0 0;
  color: #687294;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.65;
}
.failed-result__encouragement {
  margin: 14px 0 0;
  color: #ec3e76;
  font-size: 17px;
  font-weight: 900;
}
.result-hero,
.result-summary,
.versus-result {
  border: 1px solid #e0e3f1;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 12px 30px rgba(36, 44, 95, 0.05);
}
.result-hero {
  position: relative;
  display: grid;
  min-height: 410px;
  grid-template-rows: auto minmax(0, 1fr) auto;
  place-items: center;
  overflow: hidden;
  background: #f5f3ff;
  animation: result-enter 0.5s var(--ease-out) both;
}
.result-hero img {
  width: min(62%, 300px);
  height: 255px;
  object-fit: contain;
  align-self: center;
}
.score-block {
  position: static;
  width: calc(100% - 40px);
  margin: 0 20px 20px;
  padding: 14px;
  border: 0;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.88);
  text-align: center;
}
.score-block span,
.rank-card span {
  display: block;
  color: var(--color-muted);
  font-size: 12px;
}
.score-block strong {
  display: block;
  color: var(--color-accent-blue);
  font-family: inherit;
  font-size: clamp(36px, 5vw, 58px);
  line-height: 1.05;
  font-variant-numeric: tabular-nums;
}
.result-summary {
  display: grid;
  align-content: center;
  gap: 18px;
  padding: 28px;
  animation: result-enter 0.5s var(--ease-out) both;
  animation-delay: 0.12s;
}
.result-summary__eyebrow {
  margin: 0;
  color: var(--color-accent-blue);
  font-size: 12px;
  font-weight: 900;
}
.result-summary__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.record-badge {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  color: #805500;
  background: #fff0bd;
  font-size: 11px;
  font-weight: 900;
  animation: badge-pop 0.35s var(--ease-out) both;
  animation-delay: 0.5s;
}
.result-summary h3 {
  margin: -10px 0 0;
  color: var(--color-ink);
  font-size: 22px;
}
.rank-card {
  padding: 18px;
  border: 1.5px dashed #a8b3e9;
  border-radius: 16px;
  background: #f5f4ff;
  text-align: center;
}
.rank-card b {
  display: block;
  margin: 5px 0;
  color: var(--color-accent-blue);
  font-family: inherit;
  font-size: 42px;
}
.rank-card small {
  color: var(--color-muted);
}
.result-summary dl {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin: 0;
}
.result-summary dl div {
  padding: 13px 8px;
  border-left: 1px solid var(--color-line);
  text-align: center;
}
.result-summary dl div:first-child {
  border-left: 0;
}
.result-summary__stats--single > div {
  grid-column: 2;
  border-left: 0;
}
.result-summary__stats--pair {
  grid-template-columns: repeat(2, 1fr);
}
.result-summary dt {
  color: var(--color-muted);
  font-size: 12px;
}
.result-summary dd {
  margin: 6px 0 0;
  color: var(--color-ink);
  font-weight: 900;
}
.result-note {
  margin: 0;
  color: var(--color-muted);
  font-size: 12px;
  padding: 12px;
  border-radius: 10px;
  background: #f6f5ff;
  text-align: left;
}
.result-grid--competitive {
  grid-template-columns: 1fr;
  gap: 18px;
}
.versus-hero {
  padding: 0 0 2px;
  text-align: center;
  animation: result-enter-hero 0.5s var(--ease-out) both;
}
.versus-hero > span {
  display: inline-block;
  padding: 6px 15px;
  border-radius: 999px;
  color: #fff;
  background: #7b63e8;
  font-size: 12px;
  font-weight: 900;
}
.versus-hero > strong {
  display: block;
  margin: 7px 0 2px;
  color: #5a46e2;
  font-size: clamp(38px, 6vw, 62px);
  letter-spacing: -0.06em;
}
.versus-hero > p {
  margin: 0;
  color: var(--color-muted);
  font-size: 14px;
}
.versus-result {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 1.35fr) minmax(0, 1fr);
  align-items: center;
  gap: 18px;
  min-height: 330px;
  padding: 18px;
  overflow: hidden;
  animation: result-enter 0.5s var(--ease-out) both;
  animation-delay: 0.15s;
}
.player-result {
  display: grid;
  width: 100%;
  box-sizing: border-box;
  align-content: center;
  gap: 8px;
  place-items: center;
  min-height: 292px;
  padding: 16px;
  border: 1px solid #d5d9ff;
  border-radius: 14px;
  color: var(--color-accent-blue);
  background: #f2f4ff;
  text-align: center;
}
.player-result--opponent {
  border-color: #f2d4d8;
  color: #d64c52;
  background: #fff2f2;
}
.player-result > b {
  padding: 4px 12px;
  border-radius: 999px;
  color: #fff;
  background: #6c63e7;
  font-size: 11px;
}
.player-result--opponent > b {
  color: #333;
  background: #e5e5e5;
}
.player-result span {
  padding: 5px 11px;
  border-radius: 999px;
  color: #fff;
  background: #8276ed;
  font-size: 11px;
  font-weight: 900;
}
.player-result--opponent span {
  background: #989898;
}
.player-result img {
  width: min(100%, 165px);
  height: 160px;
  object-fit: contain;
  object-position: center bottom;
}
.result-grid--hold .player-result {
  grid-template-rows: auto 170px auto auto;
}
.result-grid--hold .player-result img {
  width: 170px;
  height: 170px;
  align-self: end;
}
.player-result > strong {
  color: var(--color-ink);
  font-size: 14px;
}
.player-result--opponent > strong {
  color: #4f5160;
}
.versus-stats {
  overflow: hidden;
  border: 1px solid #e0e3f1;
  border-radius: 14px;
  background: #fff;
}
.versus-stats > div {
  display: grid;
  grid-template-columns: 1fr 86px 1fr;
  align-items: center;
  min-height: 52px;
  padding: 0 18px;
  border-top: 1px solid #edf0f6;
  text-align: center;
}
.versus-stats > div:first-child {
  min-height: 100px;
  border-top: 0;
}
.versus-stats > div:first-child strong {
  color: #5b48e0;
  font-size: 39px;
}
.versus-stats > div:first-child strong:last-child {
  color: #d84171;
}
.versus-stats b {
  color: #737d9c;
  font-size: 12px;
}
.versus-stats span {
  color: var(--color-ink);
  font-size: 16px;
  font-weight: 900;
}
.versus-summary {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 0.75fr) 190px;
  align-items: center;
  gap: 20px;
  padding: 19px 24px;
  border: 1px solid #e0e3f1;
  border-radius: 16px;
  background: #f5f4ff;
  animation: result-enter 0.5s var(--ease-out) both;
  animation-delay: 0.3s;
}
.versus-summary > div {
  display: grid;
  gap: 7px;
}
.versus-summary > div b {
  color: #5747df;
  font-size: 22px;
}
.versus-summary > div span {
  color: #687294;
  font-size: 13px;
  line-height: 1.5;
}
.versus-summary dl {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin: 0;
  text-align: center;
}
.versus-summary dt {
  color: #727c99;
  font-size: 11px;
}
.versus-summary dd {
  margin: 5px 0 0;
  color: var(--color-ink);
  font-weight: 900;
}
.versus-summary button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 48px;
  border: 0;
  border-radius: 10px;
  color: #fff;
  background: var(--color-accent-blue);
  font-weight: 900;
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}
.versus-summary button svg {
  width: 16px;
  height: 16px;
}
.versus-summary button:hover {
  background: #4064c9;
}
.result-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 20px;
}
.result-actions button,
.result-actions a {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 46px;
  padding: 0 20px;
  border: 1px solid var(--color-line);
  border-radius: 10px;
  color: var(--color-ink);
  background: #fff;
  font-weight: 900;
  text-decoration: none;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) ease,
    color var(--duration-fast) ease,
    border-color var(--duration-fast) ease;
}
.result-actions button svg,
.result-actions a svg {
  width: 16px;
  height: 16px;
}
.result-actions .primary {
  border-color: var(--color-accent-blue);
  color: #fff;
  background: var(--color-accent-blue);
}
.result-actions button,
.result-actions a {
  border: 1px solid #d9ddf2;
  border-radius: 9px;
  font-family: inherit;
  font-size: inherit;
}
.result-actions .primary:hover {
  background: #564eda;
}
.result-grid--draw {
  display: grid;
  grid-template-columns: 1fr;
  gap: 20px;
}
.draw-final-hero {
  position: relative;
  text-align: center;
  animation: result-enter 0.5s var(--ease-out) both;
}
.draw-final-hero h1 {
  position: relative;
  display: inline-flex;
  gap: 14px;
  align-items: center;
  margin: 0;
  color: var(--color-ink);
  font-size: clamp(30px, 3.4vw, 42px);
}
.draw-final-hero p {
  margin: 8px 0 0;
  color: var(--color-muted);
  font-size: 16px;
}
.draw-final-hero__deco {
  display: inline-flex;
  transform: rotate(-10deg) translateY(-4px);
}
.draw-final-hero__deco svg {
  width: 0.72em;
  height: 0.72em;
}
.draw-final-hero__deco--tail {
  transform: rotate(10deg) translateY(-4px);
}
.draw-final-hero__sparkle {
  position: absolute;
  top: 4px;
  color: #c0a9ff;
}
.draw-final-hero__sparkle svg {
  width: 20px;
  height: 20px;
}
.draw-final-hero__sparkle--left {
  left: 18%;
}
.draw-final-hero__sparkle--right {
  top: auto;
  right: 18%;
  bottom: 4px;
  color: #8fd8b2;
}
.draw-final-hero__sparkle--right svg {
  width: 16px;
  height: 16px;
}
.draw-total-card {
  display: grid;
  gap: 8px;
  justify-items: center;
  margin-top: 26px;
  padding: 34px 24px 28px;
  border: 1px solid #e5e2fa;
  border-radius: 24px;
  background: #f7f4fe;
  box-shadow: var(--shadow-card);
  text-align: center;
  animation: result-enter-card 0.5s var(--ease-out) both;
  animation-delay: 0.12s;
}
.draw-total-card__label {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin: 0;
  color: var(--color-ink);
  font-size: 17px;
  font-weight: 700;
}
.draw-total-card__label-icon svg {
  width: 20px;
  height: 20px;
}
.draw-total-card__score {
  color: #6b46e5;
  font-family: var(--font-display);
  font-size: clamp(52px, 6vw, 72px);
  line-height: 1.1;
  text-shadow: 0 6px 24px rgba(107, 70, 229, 0.25);
  font-variant-numeric: tabular-nums;
}
.draw-total-card__message {
  margin: 0;
  color: #71c191;
  font-size: 16px;
  font-weight: 700;
}
.draw-total-card__meta {
  display: flex;
  justify-content: center;
  margin: 10px 0 0;
}
.draw-total-card__meta b {
  padding: 7px 14px;
  border-radius: 999px;
  color: #fff;
  background: #71c191;
  box-shadow: 0 4px 12px rgba(113, 193, 145, 0.4);
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.05em;
  display: inline-block;
  animation: badge-pop 0.35s var(--ease-out) both;
  animation-delay: 0.5s;
}
.draw-rounds {
  margin-top: 30px;
  animation: result-enter 0.5s var(--ease-out) both;
  animation-delay: 0.24s;
}
.draw-rounds h2 {
  margin: 0 0 14px;
  color: var(--color-ink);
  font-size: 22px;
}
.draw-rounds__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.draw-round-card {
  display: grid;
  gap: 6px;
  justify-items: center;
  padding: 22px 14px 20px;
  border: 1px solid var(--color-line);
  border-radius: 18px;
  background: #fff;
  box-shadow: var(--shadow-card);
  text-align: center;
}
.draw-round-card__head {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: center;
  margin: 0;
}
.draw-round-card__head-group {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}
.draw-round-card__label {
  color: var(--color-accent-blue);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
}
.draw-round-card__difficulty {
  padding: 3px 10px;
  border-radius: 999px;
  color: var(--color-accent-blue);
  background: var(--color-blue-soft);
  font-size: 11.5px;
  font-weight: 800;
}
.draw-round-card__prompt {
  margin: 0;
  color: var(--color-ink);
  font-size: 20px;
  font-weight: 700;
  display: inline-flex;
  gap: 8px;
  align-items: center;
}
.draw-round-card__answer {
  padding: 3px 9px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 800;
}
.draw-round-card__answer--correct {
  color: #2f9e63;
  background: #e4f6ec;
}
.draw-round-card__answer--wrong {
  color: #e05a5a;
  background: #fdecec;
}
.draw-round-card__score {
  position: relative;
  padding: 6px 12px;
  border: 0;
  border-radius: 10px;
  color: var(--color-ink);
  background: var(--color-surface-soft);
  font-family: var(--font-display);
  font-size: 26px;
  cursor: help;
}
.draw-round-card__score:hover,
.draw-round-card__score:focus-visible {
  background: var(--color-blue-soft);
}
.draw-score-tooltip {
  position: absolute;
  bottom: calc(100% + 10px);
  left: 50%;
  z-index: 20;
  display: grid;
  gap: 7px;
  width: 208px;
  padding: 14px 15px;
  border-radius: 13px;
  color: #fff;
  background: #26334f;
  box-shadow: var(--shadow-float);
  font-family: 'Noto Sans KR', sans-serif;
  font-size: 12.5px;
  text-align: left;
  opacity: 0;
  visibility: hidden;
  transform: translateX(-50%) translateY(4px);
  transition:
    opacity 0.16s ease,
    transform 0.16s ease,
    visibility 0.16s;
  pointer-events: none;
}
.draw-score-tooltip::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 50%;
  border: 6px solid transparent;
  border-top-color: #26334f;
  transform: translateX(-50%);
}
.draw-round-card__score:hover .draw-score-tooltip,
.draw-round-card__score:focus-visible .draw-score-tooltip {
  opacity: 1;
  visibility: visible;
  transform: translateX(-50%) translateY(0);
}
.draw-score-tooltip > b {
  justify-self: center;
  font-size: 12px;
  font-weight: 800;
  text-align: center;
  color: #ffd95e;
}
.draw-score-tooltip > span {
  display: flex;
  justify-content: space-between;
}
.draw-score-tooltip > span i {
  font-style: normal;
  color: #c3cbe2;
}
.draw-score-tooltip > span em {
  font-style: normal;
  font-weight: 700;
}
.draw-ranking {
  margin-top: 30px;
  animation: result-enter 0.5s var(--ease-out) both;
  animation-delay: 0.36s;
}
.draw-ranking__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 0 14px;
}
.draw-ranking__heading h2 {
  margin: 0;
  color: var(--color-ink);
  font-size: 22px;
}
.draw-ranking__heading button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 13px;
  border: 0;
  border-radius: 9px;
  color: var(--color-accent-blue);
  background: transparent;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}
.draw-ranking__heading button svg {
  width: 15px;
  height: 15px;
}
.draw-ranking__heading button:hover {
  background: var(--color-blue-soft);
}
.draw-ranking__panel {
  display: grid;
  gap: 14px;
  padding: 22px 24px;
  border: 1px solid var(--color-line);
  border-radius: 18px;
  background: #fff;
  box-shadow: var(--shadow-card);
}
.draw-ranking__highlight {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 14px 18px;
  border-radius: 14px;
  background: #f7f4fe;
}
.draw-ranking__medal svg {
  width: 26px;
  height: 26px;
}
.draw-ranking__highlight p {
  margin: 0;
  color: var(--color-ink);
  font-size: 15px;
  font-weight: 600;
}
.draw-ranking__highlight strong {
  font-family: var(--font-display);
  font-size: 22px;
  color: #7451dd;
}
.draw-ranking__highlight small {
  color: var(--color-muted);
}
.draw-ranking__highlight b {
  margin-left: auto;
  padding: 6px 12px;
  border-radius: 999px;
  color: #2f9e63;
  background: #e4f6ec;
  font-size: 12.5px;
  font-weight: 800;
}
.draw-ranking__list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.draw-ranking__list li {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 10px 14px;
  border-radius: 11px;
  background: var(--color-surface-soft);
  font-size: 14px;
}
.draw-ranking__list li b {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: var(--color-muted);
  background: #fff;
  font-size: 12px;
}
.draw-ranking__list li span {
  color: var(--color-ink);
  font-weight: 600;
}
.draw-ranking__list li strong {
  margin-left: auto;
  color: var(--color-ink);
}
.draw-ranking__list li.draw-ranking__list-item--mine {
  border: 1px solid #cfd6f6;
  background: var(--color-blue-soft);
}
.draw-ranking__list li.draw-ranking__list-item--mine b {
  color: #fff;
  background: var(--color-accent-blue);
}
.draw-final-footer {
  display: flex;
  gap: 14px;
  justify-content: center;
  margin-top: 34px;
  animation: result-enter 0.5s var(--ease-out) both;
  animation-delay: 0.44s;
}
.draw-final-footer__replay {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  padding: 14px 30px;
  border: 0;
  border-radius: 14px;
  color: #fff;
  background: #7b81e3;
  box-shadow: 0 8px 18px rgba(123, 129, 227, 0.3);
  font-size: 16px;
  font-weight: 800;
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}
.draw-final-footer__replay svg {
  width: 18px;
  height: 18px;
}
.draw-final-footer__replay:hover {
  background: #6a70d6;
}
.draw-final-footer__list {
  padding: 14px 30px;
  border: 1.5px solid #dcdff6;
  border-radius: 14px;
  color: #26334f;
  background: #fff;
  font-size: 16px;
  font-weight: 800;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) ease,
    color var(--duration-fast) ease,
    border-color var(--duration-fast) ease;
}
.draw-final-footer__list:hover {
  border-color: #7b81e3;
  color: #7b81e3;
}
.missing {
  padding: 60px;
  text-align: center;
}
@media (max-width: 700px) {
  .air-result {
    gap: 18px;
  }
  .air-result .air-duel-scoreboard {
    min-height: 300px;
  }
  .air-result .air-duel-scoreboard__player {
    padding: 24px 8px;
  }
  .air-result .air-duel-scoreboard__player img {
    max-width: 120px;
    height: 145px;
  }
  .air-result .air-duel-scoreboard__outcome {
    width: 118px;
    height: 118px;
  }
  .air-result .air-duel-scoreboard__outcome span {
    font-size: 25px;
  }
  .air-result .air-duel-scoreboard__outcome strong {
    font-size: 29px;
  }
  .air-result__summary {
    grid-template-columns: 1fr;
  }
  .hold-record-missed__grid {
    grid-template-columns: 1fr;
  }
  .hold-record-missed__hero {
    min-height: 390px;
  }
  .hold-record-missed__side {
    grid-template-rows: auto;
  }
  .hold-record-missed__summary dl {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .hold-record-missed__summary dl div {
    padding: 8px 0 0;
    border-top: 1px solid #e7e8f0;
    border-left: 0;
  }
  .hold-record-missed__summary dl div:first-child {
    padding-top: 0;
    border-top: 0;
  }
  .hold-record-missed__encouragement {
    display: grid;
  }
  .air-duel-scoreboard {
    min-height: 280px;
  }
  .air-duel-scoreboard__player {
    grid-template-columns: minmax(0, 1fr) 72px;
    padding: 22px 8px 18px;
  }
  .air-duel-scoreboard__player--ai {
    grid-template-columns: 72px minmax(0, 1fr);
  }
  .air-duel-scoreboard__player img {
    max-width: 118px;
    height: 132px;
  }
  .air-duel-scoreboard__player b {
    font-size: 48px;
  }
  .air-duel-scoreboard__outcome {
    width: 112px;
    height: 112px;
  }
  .air-duel-scoreboard__outcome span {
    font-size: 25px;
  }
  .air-duel-scoreboard__outcome strong {
    font-size: 28px;
  }
  .duel-loss__hero {
    grid-template-columns: 1fr;
    padding: 28px 20px;
  }
  .duel-loss__hero img {
    display: none;
  }
  .duel-loss__scoreboard,
  .duel-loss__summary {
    grid-template-columns: 1fr;
  }
  .duel-loss__player img {
    max-width: 120px;
  }
  .failed-result {
    grid-template-columns: 1fr;
  }
  .failed-result__mascot {
    min-height: 210px;
    padding-top: 48px;
  }
  .failed-result__mascot img {
    max-height: 180px;
  }
  .failed-result__content {
    padding: 32px 20px;
  }
  .failed-result__content h2 {
    margin-bottom: 22px;
  }
  .result-grid {
    grid-template-columns: 1fr;
  }
  .draw-rounds__grid {
    grid-template-columns: 1fr;
  }
  .draw-final-footer {
    justify-content: center;
    text-align: center;
  }
  .versus-result {
    grid-template-columns: 1fr;
  }
  .player-result {
    min-height: 200px;
  }
  .versus-summary {
    grid-template-columns: 1fr;
  }
  .result-summary dl {
    grid-template-columns: 1fr;
  }
  .result-summary dl div {
    border-top: 1px solid var(--color-line);
    border-left: 0;
  }
  .result-summary__stats--single > div {
    grid-column: auto;
  }
  .result-actions {
    flex-direction: column;
  }
  .result-actions button,
  .result-actions a {
    width: 100%;
    box-sizing: border-box;
  }
}
</style>
