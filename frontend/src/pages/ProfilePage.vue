<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import gameEyeImage from '../assets/images/games/game-eye.png'
import { useToast } from '../composables/useToast'
import { profileData } from '../mocks/profile'
import { useAuthStore } from '../stores/auth'
import { PROFILE_OPTIONS, checkNickname as apiCheckNickname } from '../api/user'
import { getMyResults, getResult } from '../api/gameResult'
import { ApiError } from '../api/http'
import { isValidPassword, PASSWORD_POLICY_MESSAGE } from '../utils/password'
import { GAME_DISPLAY_NAME } from '../types/waitingRoom'
import type { GameName } from '../types/waitingRoom'
import type { ProfileImageCode } from '../types/auth'
import type {
  GameOutcome,
  GameResultDetailResponse,
  GameResultPlayMode,
  MyGameResult,
  ParticipantResult,
} from '../types/gameResult'

/** 백엔드 닉네임 규칙: 공백 없이 한글/영문/숫자 2~10자. */
const NICKNAME_PATTERN = /^[가-힣A-Za-z0-9]{2,10}$/

const { showToast } = useToast()
const router = useRouter()
const auth = useAuthStore()
const isEditing = ref(false)
const nickname = ref(auth.user.nickname)
const draftNickname = ref(auth.user.nickname)
const selectedAvatarId = ref<ProfileImageCode>(
  auth.user.profileImageCode ?? 'PROFILE_1',
)
const draftAvatarId = ref<ProfileImageCode>(selectedAvatarId.value)
const isNicknameChecked = ref(false)
const isPasswordDialogOpen = ref(false)
const currentPassword = ref('')
const changePassword = ref('')
const changePasswordConfirmation = ref('')
const isWithdrawDialogOpen = ref(false)

// 최근 경기 기록(회원 전용). 목록은 요약만, 상세는 클릭 시 조회한다.
const RECORDS_PAGE_SIZE = 5
const records = ref<MyGameResult[]>([])
const recordsPage = ref(1)
const recordsTotal = ref(0)
const isLoadingRecords = ref(false)
const totalRecordPages = computed(() =>
  Math.max(1, Math.ceil(recordsTotal.value / RECORDS_PAGE_SIZE)),
)

const selectedRecord = ref<GameResultDetailResponse | null>(null)
// 상세 응답은 본인을 식별하지 못하므로, 목록에서 클릭한 항목의 내 결과를 함께 보관한다.
const selectedMyOutcome = ref<GameOutcome>('COMPLETED')
const selectedMyRank = ref(1)
const closeButton = ref<globalThis.HTMLButtonElement | null>(null)
const modalDialog = ref<globalThis.HTMLElement | null>(null)
let lastFocusedElement: globalThis.HTMLElement | null = null
let previousBodyOverflow = ''

const selectedAvatar = computed(
  () =>
    PROFILE_OPTIONS.find((option) => option.code === selectedAvatarId.value) ??
    PROFILE_OPTIONS[0],
)

// 가입일을 1일째로 세는 달력 기준 일수. 게스트/이전 mock 화면은 기존 값 유지.
const journeyDays = computed(() => {
  if (!auth.user.createdAt) return profileData.journeyDays
  const created = new Date(auth.user.createdAt)
  const startOfDay = (d: Date) =>
    new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const elapsed = startOfDay(new Date()) - startOfDay(created)
  return Math.max(1, Math.floor(elapsed / 86_400_000) + 1)
})

// 로그인/프로필 갱신으로 스토어 user가 바뀌면(편집 중이 아닐 때) 표시값을 동기화한다.
watch(
  () => auth.user,
  (nextUser) => {
    if (isEditing.value) return
    nickname.value = nextUser.nickname
    selectedAvatarId.value = nextUser.profileImageCode ?? 'PROFILE_1'
  },
)

const changePasswordsMatch = computed(
  () => changePassword.value === changePasswordConfirmation.value,
)

watch(selectedRecord, async (record) => {
  if (record) {
    previousBodyOverflow = globalThis.document.body.style.overflow
    globalThis.document.body.style.overflow = 'hidden'
    await nextTick()
    closeButton.value?.focus()
    return
  }

  globalThis.document.body.style.overflow = previousBodyOverflow
  await nextTick()
  lastFocusedElement?.focus()
})

onBeforeUnmount(() => {
  globalThis.document.body.style.overflow = previousBodyOverflow
})

function getDurationMs(record: GameResultDetailResponse) {
  return (
    new Date(record.endedAt).getTime() - new Date(record.startedAt).getTime()
  )
}

function formatDuration(durationMs: number) {
  const totalSeconds = Math.max(0, Math.floor(durationMs / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60

  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

function formatStartedAt(startedAt: string) {
  const date = new Date(startedAt)

  return new Intl.DateTimeFormat('ko-KR', {
    month: 'long',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(date)
}

function formatPlayMode(playMode: GameResultPlayMode) {
  return playMode === 'INVITE' || playMode === 'RANDOM'
    ? '멀티플레이'
    : '싱글플레이'
}

function getOutcomeLabel(outcome: GameOutcome) {
  return {
    WIN: '승리',
    LOSE: '패배',
    DRAW: '무승부',
    COMPLETED: '완료',
  }[outcome]
}

// 한글 받침 유무로 목적격 조사(을/를)를 고른다. 받침 없거나 한글이 아니면 '를'.
function objectParticle(word: string): string {
  const last = word.charCodeAt(word.length - 1)
  const hasFinalConsonant =
    last >= 0xac00 && last <= 0xd7a3 && (last - 0xac00) % 28 !== 0
  return hasFinalConsonant ? '을' : '를'
}

// "완료"로 표시할 게임: 눈으로 그리기(전 모드) + 눈 깜빡이기·눈싸움·리듬의 혼자하기(SOLO).
// 그 외(멀티/AI, 에어하키 등)는 승리/패배로 표시한다. (1:1이라 순위는 쓰지 않는다.)
const SOLO_COMPLETION_GAMES = new Set<GameName>(['BLINK', 'EYEFIGHT', 'RHYTHM'])

function isCompletionRecord(
  gameName: GameName,
  playMode: GameResultPlayMode,
): boolean {
  if (gameName === 'DRAWING') return true
  return SOLO_COMPLETION_GAMES.has(gameName) && playMode === 'SOLO'
}

/** 표시용 결과. 완료 게임은 승패 대신 항상 '완료'로 보여준다. */
function displayOutcome(
  gameName: GameName,
  playMode: GameResultPlayMode,
  outcome: GameOutcome,
): GameOutcome {
  return isCompletionRecord(gameName, playMode) ? 'COMPLETED' : outcome
}

function getSummary(record: MyGameResult) {
  const name = GAME_DISPLAY_NAME[record.gameName]
  const outcome = displayOutcome(
    record.gameName,
    record.playMode,
    record.myOutcome,
  )

  if (outcome === 'COMPLETED') {
    return `${name}${objectParticle(name)} 완료했어요.`
  }
  if (outcome === 'WIN') {
    return `${name}에서 승리했어요.`
  }
  if (outcome === 'LOSE') {
    return `${name}에서 패배했어요.`
  }
  return `${name}에서 무승부를 기록했어요.`
}

/** 목록 행에 표시할 결과(완료/승리/패배/무승부). 아이콘·배지에 공통으로 쓴다. */
function recordOutcome(record: MyGameResult): GameOutcome {
  return displayOutcome(record.gameName, record.playMode, record.myOutcome)
}

/** 상세 모달에서 표시할 내 결과(완료 게임은 '완료'). */
const detailOutcome = computed<GameOutcome>(() => {
  const record = selectedRecord.value
  if (!record) return selectedMyOutcome.value
  return displayOutcome(
    record.gameName,
    record.playMode,
    selectedMyOutcome.value,
  )
})

// 상세 응답에서 내 참가자. mySlotNo로 찾고, 없으면(구버전 응답) 결과·순위가 맞는
// 참가자 → 첫 참가자 순으로 추정한다.
const myDetailParticipant = computed<ParticipantResult | null>(() => {
  const record = selectedRecord.value
  if (!record) return null
  const bySlot = record.participants.find((p) => p.slotNo === record.mySlotNo)
  if (bySlot) return bySlot
  const byOutcome = record.participants.find(
    (p) =>
      p.outcome === selectedMyOutcome.value && p.rank === selectedMyRank.value,
  )
  return byOutcome ?? record.participants[0] ?? null
})

const isDetailAirHockey = computed(
  () => selectedRecord.value?.gameName === 'HOCKEY',
)

// 승패/완료 문구 하단 점수. 에어하키는 점수 없이 플레이 시간만 보여주므로 null,
// 그 외 게임은 랭킹에 반영하는 점수를 게임별 형식으로 보여준다.
const myRankingScore = computed<{ label: string; value: string } | null>(() => {
  const record = selectedRecord.value
  if (!record || isDetailAirHockey.value) return null
  const score = myDetailParticipant.value?.score
  if (score === null || score === undefined) return null
  return {
    label: record.gameName === 'EYEFIGHT' ? '생존 시간' : '점수',
    value: formatScore(record.gameName, score),
  }
})

/** 게임별 점수 표기. 눈싸움은 생존 시간(mm:ss), 그 외는 'N점'. 점수 없으면 '—'. */
function formatScore(gameName: GameName, score: number | null): string {
  if (score === null) return '—'
  if (gameName === 'EYEFIGHT') return formatDuration(score * 1000)
  return `${score}점`
}

/** 1:1 상대 결과는 내 결과의 반대다(무승부·완료는 그대로). */
function invertOutcome(outcome: GameOutcome): GameOutcome {
  if (outcome === 'WIN') return 'LOSE'
  if (outcome === 'LOSE') return 'WIN'
  return outcome
}

// 내 슬롯의 gameResult JSONB(점수·상대 정보가 담긴 객체).
const myResultData = computed<Record<string, unknown> | null>(() => {
  const record = selectedRecord.value
  const slotNo = myDetailParticipant.value?.slotNo
  if (!record || slotNo === undefined) return null
  const slot = record.gameResult[String(slotNo)]
  return slot && typeof slot === 'object'
    ? (slot as Record<string, unknown>)
    : null
})

const isSharedMatch = computed(
  () =>
    selectedRecord.value?.playMode === 'INVITE' ||
    selectedRecord.value?.playMode === 'RANDOM',
)

interface PlayerRow {
  key: string
  name: string
  score: string
  outcome: GameOutcome
  isMe: boolean
}

// 상대 행(친구 초대·랜덤 매칭에서만). 참가자 배열에 상대가 있으면 그것을,
// 없으면(현재 멀티는 본인만 참가자로 저장) JSONB의 상대 정보로 1행을 구성한다.
const opponentRows = computed<PlayerRow[]>(() => {
  const record = selectedRecord.value
  if (!record || !isSharedMatch.value) return []

  const mySlot = myDetailParticipant.value?.slotNo
  const others = record.participants.filter((p) => p.slotNo !== mySlot)
  if (others.length) {
    return others.map((p) => ({
      key: `slot-${p.slotNo}`,
      name: p.displayName,
      score: formatScore(record.gameName, p.score),
      outcome: p.outcome,
      isMe: false,
    }))
  }

  const data = myResultData.value
  const name =
    typeof data?.opponentNickname === 'string' ? data.opponentNickname : null
  if (!name) return []
  const rawScore = data?.opponentScore
  return [
    {
      key: 'opponent',
      name,
      score: formatScore(
        record.gameName,
        typeof rawScore === 'number' ? rawScore : null,
      ),
      outcome: invertOutcome(detailOutcome.value),
      isMe: false,
    },
  ]
})

// 상세 스코어보드: 상대가 있을 때만 내 행을 맨 위에 붙여 나·상대를 함께 보여준다.
const playerRows = computed<PlayerRow[]>(() => {
  const opponents = opponentRows.value
  const record = selectedRecord.value
  if (!opponents.length || !record) return []
  const me = myDetailParticipant.value
  const myRow: PlayerRow = {
    key: 'me',
    name: me?.displayName || auth.user.nickname || '나',
    score: formatScore(record.gameName, me?.score ?? null),
    outcome: detailOutcome.value,
    isMe: true,
  }
  return [myRow, ...opponents]
})

async function loadRecords() {
  if (!auth.isAuthenticated || auth.user.id === null) {
    records.value = []
    recordsTotal.value = 0
    return
  }
  isLoadingRecords.value = true
  try {
    const result = await getMyResults(recordsPage.value, RECORDS_PAGE_SIZE)
    records.value = result.content
    recordsTotal.value = result.totalElements
  } catch (error) {
    showToast(
      error instanceof ApiError
        ? error.message
        : '경기 기록을 불러오지 못했어요.',
    )
  } finally {
    isLoadingRecords.value = false
  }
}

function goToRecordsPage(next: number) {
  if (next < 1 || next > totalRecordPages.value) return
  recordsPage.value = next
  void loadRecords()
}

onMounted(() => {
  void loadRecords()
})

// 마이페이지는 로그인 전용 — 페이지에 머무는 중 로그아웃(세션 만료 포함)되면 홈으로 보낸다.
watch(
  () => auth.isAuthenticated,
  (authenticated) => {
    if (!authenticated) void router.push({ name: 'home' })
  },
)

// 로그인/로그아웃으로 회원이 바뀌면 첫 페이지부터 다시 불러온다.
watch(
  () => auth.user.id,
  () => {
    recordsPage.value = 1
    void loadRecords()
  },
)

function handleOpenEdit() {
  draftNickname.value = nickname.value
  draftAvatarId.value = selectedAvatarId.value
  isNicknameChecked.value = false
  isEditing.value = true
}

function handleNicknameChange() {
  isNicknameChecked.value = false
}

/** maxlength로 입력은 이미 막히므로, 10자 초과 시도에 안내만 띄운다. */
function handleNicknameBeforeInput(event: globalThis.InputEvent) {
  if (!event.data) return
  const input = event.currentTarget as globalThis.HTMLInputElement
  const selectionLength =
    (input.selectionEnd ?? 0) - (input.selectionStart ?? 0)
  const nextLength = input.value.length - selectionLength + event.data.length
  if (nextLength > 10) {
    showToast('닉네임은 10자까지 입력할 수 있어요.')
  }
}

async function handleCheckNickname() {
  const value = draftNickname.value.trim()
  if (!NICKNAME_PATTERN.test(value)) {
    showToast('닉네임은 공백 없이 한글/영문/숫자 2~10자여야 해요.')
    return
  }
  try {
    const result = await apiCheckNickname(value)
    isNicknameChecked.value = result.available
    showToast(
      result.available
        ? '사용 가능한 닉네임이에요.'
        : '이미 사용 중인 닉네임이에요.',
    )
  } catch (error) {
    showToast(
      error instanceof ApiError ? error.message : '닉네임 확인에 실패했어요.',
    )
  }
}

async function handleSaveProfile() {
  if (auth.user.id === null) {
    showToast('로그인이 필요해요.')
    auth.openLogin()
    return
  }
  const nextNickname = draftNickname.value.trim()
  const nicknameChanged = nextNickname !== auth.user.nickname
  const avatarChanged = draftAvatarId.value !== auth.user.profileImageCode

  if (nicknameChanged && !isNicknameChecked.value) {
    showToast('닉네임 중복 확인을 먼저 해주세요.')
    return
  }
  if (!nicknameChanged && !avatarChanged) {
    isEditing.value = false
    return
  }

  const patch: { nickname?: string; profileImageCode?: ProfileImageCode } = {}
  if (nicknameChanged) patch.nickname = nextNickname
  if (avatarChanged) patch.profileImageCode = draftAvatarId.value

  try {
    await auth.updateProfile(patch)
    nickname.value = auth.user.nickname
    selectedAvatarId.value = auth.user.profileImageCode ?? 'PROFILE_1'
    isEditing.value = false
    showToast('프로필 정보가 저장되었어요.')
  } catch (error) {
    showToast(
      error instanceof ApiError ? error.message : '프로필 저장에 실패했어요.',
    )
  }
}

function handleCancelEdit() {
  isEditing.value = false
}

async function handleOpenRecord(record: MyGameResult, event: globalThis.Event) {
  lastFocusedElement = event.currentTarget as globalThis.HTMLElement
  selectedMyOutcome.value = record.myOutcome
  selectedMyRank.value = record.myRank
  try {
    selectedRecord.value = await getResult(record.resultId)
  } catch (error) {
    showToast(
      error instanceof ApiError
        ? error.message
        : '경기 상세를 불러오지 못했어요.',
    )
  }
}

function handleRecordKeydown(
  record: MyGameResult,
  event: globalThis.KeyboardEvent,
) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    void handleOpenRecord(record, event)
  }
}

function handleCloseRecord() {
  selectedRecord.value = null
}

function handleModalTab(event: globalThis.KeyboardEvent) {
  const focusableElements =
    modalDialog.value?.querySelectorAll<globalThis.HTMLElement>(
      'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    )

  if (!focusableElements?.length) {
    return
  }

  const firstElement = focusableElements[0]
  const lastElement = focusableElements[focusableElements.length - 1]

  if (event.shiftKey && globalThis.document.activeElement === firstElement) {
    event.preventDefault()
    lastElement.focus()
  } else if (
    !event.shiftKey &&
    globalThis.document.activeElement === lastElement
  ) {
    event.preventDefault()
    firstElement.focus()
  }
}

function handleOpenPasswordChange() {
  currentPassword.value = ''
  changePassword.value = ''
  changePasswordConfirmation.value = ''
  isPasswordDialogOpen.value = true
}

function handleClosePasswordChange() {
  isPasswordDialogOpen.value = false
}

async function handleSubmitPasswordChange() {
  if (!currentPassword.value.trim()) {
    showToast('현재 비밀번호를 입력해 주세요.')
    return
  }

  if (!changePassword.value.trim()) {
    showToast('새 비밀번호를 입력해 주세요.')
    return
  }

  if (!isValidPassword(changePassword.value)) {
    showToast(PASSWORD_POLICY_MESSAGE)
    return
  }

  if (!changePasswordsMatch.value) {
    showToast('새 비밀번호와 확인 비밀번호가 일치하지 않아요.')
    return
  }

  try {
    await auth.changePassword(currentPassword.value, changePassword.value)
    isPasswordDialogOpen.value = false
    showToast('비밀번호가 변경되었어요.')
  } catch (error) {
    showToast(
      error instanceof ApiError ? error.message : '비밀번호 변경에 실패했어요.',
    )
  }
}

function handleWithdraw() {
  isWithdrawDialogOpen.value = true
}

async function handleConfirmWithdraw() {
  try {
    await auth.withdraw()
    isWithdrawDialogOpen.value = false
    showToast('탈퇴가 완료되었어요. 그동안 함께해 주셔서 감사합니다.')
    await router.push({ name: 'home' })
  } catch (error) {
    showToast(
      error instanceof ApiError ? error.message : '회원 탈퇴에 실패했어요.',
    )
  }
}
</script>

<template>
  <section class="profile-page">
    <section class="profile-page__hero">
      <div class="profile-page__avatar">
        <img
          :src="selectedAvatar?.image ?? profileData.avatar"
          alt="선택한 프로필 이미지"
        />
      </div>
      <div class="profile-page__identity">
        <span>MY EYE JOURNEY</span>
        <h1>{{ nickname }}</h1>
        <p>eyedontcare와 함께한 지 {{ journeyDays }}일째예요.</p>
      </div>
      <button
        class="profile-page__edit-button"
        type="button"
        @click="handleOpenEdit"
      >
        프로필 수정
      </button>
    </section>

    <section
      v-if="isEditing"
      class="profile-page__editor"
      aria-labelledby="profile-edit-title"
    >
      <header>
        <div>
          <span>EDIT PROFILE</span>
          <h2 id="profile-edit-title">프로필 수정</h2>
        </div>
        <p>닉네임과 프로필 이미지를 변경할 수 있어요.</p>
      </header>
      <div class="profile-page__form-grid">
        <label class="profile-page__field">
          <span>닉네임</span>
          <div>
            <input
              v-model="draftNickname"
              type="text"
              maxlength="10"
              @beforeinput="handleNicknameBeforeInput"
              @input="handleNicknameChange"
            />
            <button type="button" @click="handleCheckNickname">
              중복 확인
            </button>
          </div>
          <small v-if="isNicknameChecked" class="profile-page__success"
            >사용 가능한 닉네임이에요.</small
          >
        </label>
      </div>
      <div class="profile-page__image-field">
        <span>프로필 이미지</span>
        <div
          class="profile-page__avatar-picker"
          role="radiogroup"
          aria-label="프로필 이미지 선택"
        >
          <button
            v-for="avatar in PROFILE_OPTIONS"
            :key="avatar.code"
            :aria-checked="avatar.code === draftAvatarId"
            :class="{
              'profile-page__avatar-option--selected':
                avatar.code === draftAvatarId,
            }"
            role="radio"
            type="button"
            @click="draftAvatarId = avatar.code"
          >
            <img
              :src="avatar.image"
              :alt="`${avatar.name} 프로필 이미지`"
            /><span>{{ avatar.name }}</span>
          </button>
        </div>
      </div>
      <div class="profile-page__editor-actions">
        <button
          class="profile-page__secondary-button"
          type="button"
          @click="handleCancelEdit"
        >
          취소</button
        ><button
          class="profile-page__save-button"
          type="button"
          @click="handleSaveProfile"
        >
          저장하기
        </button>
      </div>
    </section>

    <section
      class="profile-page__records"
      aria-labelledby="recent-record-title"
    >
      <header>
        <div>
          <span>RECENT GAME RECORDS</span>
          <h2 id="recent-record-title">최근 경기 기록</h2>
        </div>
      </header>
      <ul v-if="auth.isAuthenticated && records.length">
        <li
          v-for="record in records"
          :key="record.resultId"
          tabindex="0"
          role="button"
          @click="handleOpenRecord(record, $event)"
          @keydown="handleRecordKeydown(record, $event)"
        >
          <span
            :class="`profile-page__record-icon--${recordOutcome(record).toLowerCase()}`"
            aria-hidden="true"
          >
            <svg
              v-if="recordOutcome(record) === 'WIN'"
              viewBox="0 0 20 20"
              fill="none"
            >
              <path
                d="M6 3h8v2a4 4 0 0 1-4 4 4 4 0 0 1-4-4V3Z"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linejoin="round"
              />
              <path
                d="M6 4H4a2 2 0 0 0 2 3M14 4h2a2 2 0 0 1-2 3"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
              />
              <path
                d="M10 9v3M7 16h6M8 16v-2.3a2 2 0 0 1 4 0V16"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
            <svg
              v-else-if="recordOutcome(record) === 'LOSE'"
              viewBox="0 0 20 20"
              fill="none"
            >
              <circle
                cx="10"
                cy="10"
                r="7.25"
                stroke="currentColor"
                stroke-width="1.5"
              />
              <path
                d="M7.5 7.5l5 5M12.5 7.5l-5 5"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
              />
            </svg>
            <svg
              v-else-if="recordOutcome(record) === 'DRAW'"
              viewBox="0 0 20 20"
              fill="none"
            >
              <circle
                cx="8"
                cy="10"
                r="5"
                stroke="currentColor"
                stroke-width="1.5"
              />
              <circle
                cx="12"
                cy="10"
                r="5"
                stroke="currentColor"
                stroke-width="1.5"
              />
            </svg>
            <svg v-else viewBox="0 0 20 20" fill="none">
              <circle
                cx="10"
                cy="10"
                r="7.25"
                stroke="currentColor"
                stroke-width="1.5"
              />
              <path
                d="M7 10.2l2.2 2.2L13.5 8"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </span>
          <p>
            <b>{{ getSummary(record) }}</b
            ><small
              >{{ formatPlayMode(record.playMode) }} ·
              {{ formatStartedAt(record.playedAt) }}</small
            >
          </p>
          <strong>{{ getOutcomeLabel(recordOutcome(record)) }}</strong>
          <span class="profile-page__record-arrow" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none">
              <path
                d="M9 6l6 6-6 6"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </span>
        </li>
      </ul>
      <div
        v-else-if="!auth.isAuthenticated"
        class="profile-page__records-empty"
      >
        <img :src="gameEyeImage" alt="" aria-hidden="true" />
        <p>로그인하면 내 경기 기록을 볼 수 있어요</p>
        <span>로그인 후 최근 플레이한 기록이 여기에 표시돼요</span>
        <button
          class="profile-page__records-cta"
          type="button"
          @click="auth.openLogin"
        >
          로그인하기
        </button>
      </div>
      <div v-else class="profile-page__records-empty">
        <img :src="gameEyeImage" alt="" aria-hidden="true" />
        <p>아직 경기 기록이 없어요</p>
        <span>게임을 플레이하면 기록이 여기에 쌓여요</span>
        <RouterLink class="profile-page__records-cta" to="/games"
          >게임 하러 가기</RouterLink
        >
      </div>
      <nav
        v-if="auth.isAuthenticated && totalRecordPages > 1"
        class="profile-page__records-pagination"
        aria-label="경기 기록 페이지"
      >
        <button
          type="button"
          :disabled="recordsPage <= 1 || isLoadingRecords"
          @click="goToRecordsPage(recordsPage - 1)"
        >
          이전
        </button>
        <span>{{ recordsPage }} / {{ totalRecordPages }}</span>
        <button
          type="button"
          :disabled="recordsPage >= totalRecordPages || isLoadingRecords"
          @click="goToRecordsPage(recordsPage + 1)"
        >
          다음
        </button>
      </nav>
    </section>

    <section class="profile-page__account" aria-label="계정 관리">
      <div>
        <span>ACCOUNT</span>
        <h2>계정 관리</h2>
        <p>서비스 이용을 종료하거나 계정 정보를 관리할 수 있어요.</p>
      </div>
      <div class="profile-page__account-actions">
        <button
          v-if="auth.user.loginType !== 'KAKAO'"
          type="button"
          @click="handleOpenPasswordChange"
        >
          비밀번호 변경
        </button>
        <button
          class="profile-page__withdraw-button"
          type="button"
          @click="handleWithdraw"
        >
          탈퇴하기
        </button>
      </div>
    </section>
  </section>

  <Teleport to="body">
    <Transition name="dialog-pop" appear>
      <div
        v-if="selectedRecord"
        class="game-result-modal"
        @keydown.esc="handleCloseRecord"
      >
        <section
          ref="modalDialog"
          class="game-result-modal__dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="game-result-title"
          @keydown.tab="handleModalTab"
        >
          <button
            ref="closeButton"
            class="game-result-modal__close"
            type="button"
            aria-label="경기 결과 닫기"
            @click="handleCloseRecord"
          >
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="M6 6l12 12M18 6 6 18"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
              />
            </svg>
          </button>
          <header>
            <span>경기 결과</span>
            <p>
              {{ formatStartedAt(selectedRecord.startedAt) }} ·
              {{ formatPlayMode(selectedRecord.playMode) }}
            </p>
            <h2 id="game-result-title">
              {{ GAME_DISPLAY_NAME[selectedRecord.gameName] }}
            </h2>
          </header>
          <div class="game-result-modal__outcome">
            <span
              :class="`profile-page__record-icon--${detailOutcome.toLowerCase()}`"
              aria-hidden="true"
            >
              <svg
                v-if="detailOutcome === 'WIN'"
                viewBox="0 0 20 20"
                fill="none"
              >
                <path
                  d="M6 3h8v2a4 4 0 0 1-4 4 4 4 0 0 1-4-4V3Z"
                  stroke="currentColor"
                  stroke-width="1.5"
                  stroke-linejoin="round"
                />
                <path
                  d="M6 4H4a2 2 0 0 0 2 3M14 4h2a2 2 0 0 1-2 3"
                  stroke="currentColor"
                  stroke-width="1.5"
                  stroke-linecap="round"
                />
                <path
                  d="M10 9v3M7 16h6M8 16v-2.3a2 2 0 0 1 4 0V16"
                  stroke="currentColor"
                  stroke-width="1.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              <svg
                v-else-if="detailOutcome === 'LOSE'"
                viewBox="0 0 20 20"
                fill="none"
              >
                <circle
                  cx="10"
                  cy="10"
                  r="7.25"
                  stroke="currentColor"
                  stroke-width="1.5"
                />
                <path
                  d="M7.5 7.5l5 5M12.5 7.5l-5 5"
                  stroke="currentColor"
                  stroke-width="1.5"
                  stroke-linecap="round"
                />
              </svg>
              <svg
                v-else-if="detailOutcome === 'DRAW'"
                viewBox="0 0 20 20"
                fill="none"
              >
                <circle
                  cx="8"
                  cy="10"
                  r="5"
                  stroke="currentColor"
                  stroke-width="1.5"
                />
                <circle
                  cx="12"
                  cy="10"
                  r="5"
                  stroke="currentColor"
                  stroke-width="1.5"
                />
              </svg>
              <svg v-else viewBox="0 0 20 20" fill="none">
                <circle
                  cx="10"
                  cy="10"
                  r="7.25"
                  stroke="currentColor"
                  stroke-width="1.5"
                />
                <path
                  d="M7 10.2l2.2 2.2L13.5 8"
                  stroke="currentColor"
                  stroke-width="1.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </span>
            <strong>{{ getOutcomeLabel(detailOutcome) }}</strong>
          </div>
          <div class="game-result-modal__stats">
            <article v-if="myRankingScore">
              <span>{{ myRankingScore.label }}</span
              ><strong>{{ myRankingScore.value }}</strong>
            </article>
            <article>
              <span>플레이 시간</span
              ><strong>{{
                formatDuration(getDurationMs(selectedRecord))
              }}</strong>
            </article>
          </div>
          <div
            v-if="playerRows.length"
            class="game-result-modal__participants"
          >
            <div><span>플레이어</span><span>점수</span><span>결과</span></div>
            <div
              v-for="row in playerRows"
              :key="row.key"
              :class="{ 'game-result-modal__participant--me': row.isMe }"
            >
              <b>{{ row.name }}{{ row.isMe ? ' (나)' : '' }}</b
              ><span>{{ row.score }}</span
              ><span>{{ getOutcomeLabel(row.outcome) }}</span>
            </div>
          </div>
          <button
            class="game-result-modal__confirm"
            type="button"
            @click="handleCloseRecord"
          >
            확인
          </button>
        </section>
      </div>
    </Transition>

    <Transition name="dialog-pop" appear>
      <div
        v-if="isPasswordDialogOpen"
        class="profile-dialog"
        @keydown.esc="handleClosePasswordChange"
      >
        <section
          class="profile-dialog__card"
          role="dialog"
          aria-modal="true"
          aria-labelledby="password-change-title"
        >
          <h2 id="password-change-title">비밀번호 변경</h2>
          <label class="profile-page__field">
            <span>현재 비밀번호</span>
            <input
              v-model="currentPassword"
              type="password"
              autocomplete="current-password"
              placeholder="현재 비밀번호를 입력해 주세요"
            />
          </label>
          <label class="profile-page__field">
            <span>새 비밀번호</span>
            <input
              v-model="changePassword"
              type="password"
              autocomplete="new-password"
              maxlength="16"
              placeholder="변경할 비밀번호를 입력해 주세요"
            />
          </label>
          <label class="profile-page__field">
            <span>새 비밀번호 확인</span>
            <input
              v-model="changePasswordConfirmation"
              type="password"
              autocomplete="new-password"
              maxlength="16"
              placeholder="비밀번호를 한 번 더 입력해 주세요"
            />
            <small
              v-if="changePasswordConfirmation"
              :class="
                changePasswordsMatch
                  ? 'profile-page__success'
                  : 'profile-page__error'
              "
              >{{
                changePasswordsMatch
                  ? '비밀번호가 일치해요.'
                  : '비밀번호가 일치하지 않아요.'
              }}</small
            >
          </label>
          <div class="profile-dialog__actions">
            <button
              class="profile-page__secondary-button"
              type="button"
              @click="handleClosePasswordChange"
            >
              취소
            </button>
            <button
              class="profile-page__save-button"
              type="button"
              @click="handleSubmitPasswordChange"
            >
              변경하기
            </button>
          </div>
        </section>
      </div>
    </Transition>

    <Transition name="dialog-pop" appear>
      <div
        v-if="isWithdrawDialogOpen"
        class="profile-dialog"
        @keydown.esc="isWithdrawDialogOpen = false"
      >
        <section
          class="profile-dialog__card profile-dialog__card--narrow"
          role="dialog"
          aria-modal="true"
          aria-labelledby="withdraw-title"
        >
          <h2 id="withdraw-title">정말 탈퇴하시겠어요?</h2>
          <p class="profile-dialog__warning">
            탈퇴하면 게임 기록과 랭킹, 길드 활동 내역이 모두 삭제되고 되돌릴 수
            없어요.<br />그래도 정말 탈퇴하시겠어요?
          </p>
          <div class="profile-dialog__actions">
            <button
              class="profile-page__secondary-button"
              type="button"
              @click="isWithdrawDialogOpen = false"
            >
              취소
            </button>
            <button
              class="profile-dialog__danger-button"
              type="button"
              @click="handleConfirmWithdraw"
            >
              탈퇴하기
            </button>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.profile-page {
  padding: 32px 0 58px;
}
.profile-page__hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 24px;
  align-items: center;
  padding: clamp(24px, 4vw, 42px);
  border: 1px solid var(--color-line);
  border-radius: 24px;
  background: #f7f4ff;
  box-shadow: var(--shadow-card);
}
.profile-page__avatar {
  display: grid;
  width: 132px;
  height: 132px;
  place-items: center;
  border: 5px solid #fff;
  border-radius: 50%;
  background: var(--color-blue-soft);
  box-shadow: 0 10px 25px rgba(57, 65, 118, 0.12);
}
.profile-page__avatar img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}
.profile-page__identity > span,
.profile-page__editor header > div > span,
.profile-page__records header > div > span,
.profile-page__account > div > span {
  color: var(--color-accent-blue);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.1em;
}
.profile-page__identity h1 {
  margin: 7px 0;
  color: var(--color-ink);
  font-size: clamp(31px, 4vw, 44px);
  letter-spacing: -0.02em;
  word-break: keep-all;
}
.profile-page__identity p,
.profile-page__editor header p,
.profile-page__account p {
  margin: 0;
  color: var(--color-muted);
  font-size: 14px;
  word-break: keep-all;
}
.profile-page__edit-button,
.profile-page__save-button,
.profile-page__field button {
  min-height: 40px;
  padding: 0 18px;
  border-radius: var(--radius-button);
  color: #fff;
  background: var(--color-primary);
  font-weight: 800;
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}
.profile-page__edit-button:hover,
.profile-page__save-button:hover,
.profile-page__field button:hover {
  background: var(--color-primary-hover);
}
.profile-page__editor,
.profile-page__records,
.profile-page__account {
  margin-top: 21px;
  padding: clamp(21px, 3vw, 30px);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: #fff;
  box-shadow: var(--shadow-card);
}
.profile-page__editor header,
.profile-page__records header,
.profile-page__account {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 20px;
}
.profile-page__editor h2,
.profile-page__records h2,
.profile-page__account h2 {
  margin: 5px 0 0;
  font-size: 23px;
  letter-spacing: -0.02em;
}
.profile-page__form-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 15px;
  margin-top: 25px;
}
.profile-page__field,
.profile-page__image-field {
  display: grid;
  gap: 8px;
  color: var(--color-ink);
  font-size: 13px;
  font-weight: 800;
}
.profile-page__field > div {
  display: flex;
  gap: 8px;
}
.profile-page__field input {
  width: 100%;
  min-width: 0;
  padding: 12px 13px;
  border: 1px solid var(--color-line);
  border-radius: 10px;
  color: var(--color-ink);
  background: var(--color-surface-soft);
  font: inherit;
  font-weight: 500;
}
.profile-page__field button {
  flex: 0 0 auto;
  padding: 0 13px;
  font-size: 12px;
}
.profile-page__field small {
  font-size: 11px;
  font-weight: 600;
}
.profile-page__success {
  color: #27886c;
}
.profile-page__error {
  color: var(--color-danger, #c2455a);
}
.profile-page__image-field {
  margin-top: 25px;
}
.profile-page__avatar-picker {
  display: flex;
  gap: 13px;
  overflow-x: auto;
  padding: 3px 2px 7px;
}
.profile-page__avatar-picker button {
  display: grid;
  flex: 0 0 86px;
  gap: 7px;
  place-items: center;
  padding: 6px 4px;
  border: 2px solid transparent;
  border-radius: 15px;
  color: var(--color-muted);
  background: transparent;
  font-size: 11px;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) ease,
    transform var(--duration-fast) ease;
}
.profile-page__avatar-picker button:hover {
  transform: translateY(-2px);
}
.profile-page__avatar-picker img {
  width: 57px;
  height: 57px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--color-blue-soft);
}
.profile-page__avatar-option--selected {
  border-color: var(--color-accent-blue) !important;
  color: var(--color-ink) !important;
  background: var(--color-blue-soft) !important;
}
.profile-page__editor-actions {
  display: flex;
  justify-content: end;
  gap: 9px;
  margin-top: 24px;
}
.profile-page__secondary-button {
  min-height: 40px;
  padding: 0 18px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-button);
  color: var(--color-muted);
  background: #fff;
  font-weight: 800;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) ease,
    border-color var(--duration-fast) ease,
    color var(--duration-fast) ease;
}
.profile-page__secondary-button:hover {
  border-color: var(--color-accent-blue);
  color: var(--color-accent-blue);
  background: var(--color-surface-soft);
}
.profile-page__records ul {
  display: grid;
  margin: 22px 0 0;
  padding: 0;
  list-style: none;
}
.profile-page__records li {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  gap: 13px;
  align-items: center;
  padding: 15px 3px;
  border-top: 1px solid var(--color-line);
  cursor: pointer;
  transition:
    background-color var(--duration-fast) ease,
    border-radius var(--duration-fast) ease;
}
.profile-page__records li:hover {
  border-radius: 12px;
  background: var(--color-surface-soft);
}
.profile-page__records-empty {
  display: grid;
  justify-items: center;
  gap: 6px;
  padding: 48px 24px;
  text-align: center;
}
.profile-page__records-empty img {
  width: 96px;
  height: 96px;
  margin-bottom: 8px;
  object-fit: contain;
}
.profile-page__records-empty p {
  margin: 0;
  color: var(--color-ink);
  font-size: 16px;
  font-weight: 700;
}
.profile-page__records-empty span {
  color: var(--color-muted);
  font-size: 13px;
}
.profile-page__records-cta {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  margin-top: 14px;
  padding: 0 22px;
  border-radius: var(--radius-button);
  color: #fff;
  background: var(--color-primary);
  font-weight: 800;
  text-decoration: none;
  transition: background-color var(--duration-fast) ease;
}
.profile-page__records-cta:hover {
  background: var(--color-primary-hover);
}
.profile-page__records li:focus-visible {
  outline: 3px solid rgba(79, 116, 219, 0.5);
  outline-offset: 2px;
  border-radius: 12px;
}
.profile-page__record-icon--win,
.profile-page__record-icon--lose,
.profile-page__record-icon--draw,
.profile-page__record-icon--completed {
  display: grid;
  width: 37px;
  height: 37px;
  flex: 0 0 37px;
  place-items: center;
  border-radius: 12px;
  font-weight: 800;
}
.profile-page__record-icon--win svg,
.profile-page__record-icon--lose svg,
.profile-page__record-icon--draw svg,
.profile-page__record-icon--completed svg {
  width: 20px;
  height: 20px;
}
.profile-page__record-icon--win {
  color: #8a6314;
  background: var(--color-purple-soft);
}
.profile-page__record-icon--lose {
  color: #657087;
  background: #f1f3f7;
}
.profile-page__record-icon--draw {
  color: #9c7a1a;
  background: var(--color-yellow-soft);
}
.profile-page__record-icon--completed {
  color: #2f9275;
  background: var(--color-mint-soft);
}
.profile-page__records p {
  min-width: 0;
  margin: 0;
  color: var(--color-muted);
  font-size: 14px;
}
.profile-page__records p b {
  display: block;
  color: var(--color-ink);
}
.profile-page__records p small {
  display: block;
  margin-top: 4px;
  font-size: 11px;
}
.profile-page__records li > strong {
  color: var(--color-accent-mint);
  font-size: 14px;
}
.profile-page__record-arrow {
  display: grid;
  color: var(--color-accent-blue);
}
.profile-page__record-arrow svg {
  width: 22px;
  height: 22px;
}
.profile-page__account {
  align-items: center;
}
.profile-page__account-actions {
  display: flex;
  gap: 9px;
}
.profile-page__account-actions button {
  min-height: 39px;
  padding: 0 16px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-button);
  color: var(--color-ink);
  background: #fff;
  font-weight: 800;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) ease,
    border-color var(--duration-fast) ease,
    color var(--duration-fast) ease;
}
.profile-page__account-actions button:hover {
  border-color: var(--color-accent-blue);
  color: var(--color-accent-blue);
  background: var(--color-surface-soft);
}
.profile-page__account-actions .profile-page__withdraw-button {
  border-color: color-mix(in srgb, var(--color-danger, #c2455a) 30%, white);
  color: var(--color-danger, #c2455a);
  background: color-mix(in srgb, var(--color-danger, #c2455a) 4%, white);
}
.profile-page__account-actions .profile-page__withdraw-button:hover {
  border-color: var(--color-danger, #c2455a);
  color: #fff;
  background: var(--color-danger, #c2455a);
}
.profile-page__records-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 16px;
  color: var(--color-muted);
  font-size: 13px;
  font-weight: 700;
}
.profile-page__records-pagination button {
  min-height: 34px;
  padding: 0 14px;
  border: 1px solid var(--color-line);
  border-radius: 9px;
  color: var(--color-ink);
  background: #fff;
  font-weight: 800;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) ease,
    color var(--duration-fast) ease;
}
.profile-page__records-pagination button:hover:not(:disabled) {
  border-color: var(--color-accent-blue);
  color: var(--color-accent-blue);
}
.profile-page__records-pagination button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
.game-result-modal {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(23, 36, 61, 0.46);
}
.game-result-modal__dialog {
  position: relative;
  width: min(100%, 520px);
  max-height: min(760px, 100%);
  overflow-y: auto;
  padding: 34px;
  border-radius: 24px;
  background: #fff;
  box-shadow: var(--shadow-float);
}
.game-result-modal__close {
  position: absolute;
  top: 18px;
  right: 18px;
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 50%;
  color: var(--color-muted);
  background: var(--color-surface-soft);
  cursor: pointer;
  transition:
    background-color var(--duration-fast) ease,
    color var(--duration-fast) ease;
}
.game-result-modal__close svg {
  width: 18px;
  height: 18px;
}
.game-result-modal__close:hover {
  color: var(--color-ink);
  background: var(--color-blue-soft);
}
.game-result-modal header {
  text-align: center;
}
.game-result-modal header > span {
  color: var(--color-accent-blue);
  font-size: 12px;
  font-weight: 800;
}
.game-result-modal header p {
  margin: 8px 0;
  color: var(--color-muted);
  font-size: 13px;
}
.game-result-modal header h2 {
  margin: 0;
  font-size: 30px;
}
.game-result-modal__outcome {
  display: grid;
  gap: 8px;
  place-items: center;
  margin: 24px 0;
  padding: 20px;
  border-radius: 18px;
  background: var(--color-purple-soft);
}
.game-result-modal__outcome strong {
  color: var(--color-ink);
  font-size: 19px;
}
.game-result-modal__stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.game-result-modal__stats article {
  display: grid;
  gap: 5px;
  padding: 14px 8px;
  border: 1px solid var(--color-line);
  border-radius: 13px;
  text-align: center;
}
.game-result-modal__stats span {
  color: var(--color-muted);
  font-size: 11px;
}
.game-result-modal__stats strong {
  font-size: 14px;
}
.game-result-modal__participants {
  margin-top: 22px;
}
.game-result-modal__participants > div {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) 0.8fr 0.8fr;
  gap: 8px;
  padding: 11px 4px;
  border-bottom: 1px solid var(--color-line);
  color: var(--color-muted);
  font-size: 12px;
  text-align: center;
}
.game-result-modal__participants > div:first-child {
  border-top: 1px solid var(--color-line);
  background: var(--color-surface-soft);
  font-weight: 800;
}
.game-result-modal__participant--me {
  color: var(--color-ink);
  font-weight: 700;
}
.game-result-modal__participant--me b {
  color: var(--color-primary);
}
.game-result-modal__participants b {
  overflow: hidden;
  color: var(--color-ink);
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
}
.game-result-modal__confirm {
  width: 100%;
  min-height: 43px;
  margin-top: 24px;
  border-radius: var(--radius-button);
  color: #fff;
  background: var(--color-primary);
  font-weight: 800;
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}
.game-result-modal__confirm:hover {
  background: var(--color-primary-hover);
}
.profile-dialog {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(23, 36, 61, 0.45);
}
.profile-dialog__card {
  display: grid;
  gap: 16px;
  width: min(440px, 100%);
  padding: 30px 28px 24px;
  border-radius: 22px;
  background: #fff;
  box-shadow: var(--shadow-float);
}
.profile-dialog__card h2 {
  margin: 0;
  color: var(--color-ink);
  font-size: 24px;
}
.profile-dialog__card--narrow {
  width: min(400px, 100%);
}
.profile-dialog__warning {
  margin: 0;
  color: var(--color-muted);
  font-size: 14px;
  line-height: 1.7;
  word-break: keep-all;
}
.profile-dialog__actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 4px;
}
.profile-dialog__danger-button {
  padding: 11px 18px;
  border: 0;
  border-radius: 11px;
  color: #fff;
  background: var(--color-danger, #c2455a);
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}
.profile-dialog__danger-button:hover {
  background: color-mix(in srgb, var(--color-danger, #c2455a) 82%, black);
}
@media (max-width: 760px) {
  .profile-page__hero {
    grid-template-columns: auto minmax(0, 1fr);
  }
  .profile-page__edit-button {
    grid-column: 1 / -1;
  }
  .profile-page__form-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 640px) {
  .profile-page {
    padding-top: 24px;
  }
  .profile-page__hero {
    grid-template-columns: 1fr;
    gap: 17px;
    text-align: center;
  }
  .profile-page__avatar {
    width: 108px;
    height: 108px;
    margin: 0 auto;
  }
  .profile-page__editor header,
  .profile-page__records header,
  .profile-page__account {
    align-items: start;
    flex-direction: column;
    gap: 8px;
  }
  .profile-page__account-actions {
    width: 100%;
  }
  .profile-page__account-actions button {
    flex: 1;
  }
  .game-result-modal__dialog {
    padding: 30px 20px 20px;
  }
}
.dialog-pop-enter-active,
.dialog-pop-leave-active {
  transition: opacity 200ms ease;
}
.dialog-pop-enter-from,
.dialog-pop-leave-to {
  opacity: 0;
}
.dialog-pop-enter-active .game-result-modal__dialog,
.dialog-pop-leave-active .game-result-modal__dialog,
.dialog-pop-enter-active .profile-dialog__card,
.dialog-pop-leave-active .profile-dialog__card {
  transition:
    transform 240ms var(--ease-out),
    opacity 240ms var(--ease-out);
}
.dialog-pop-enter-from .game-result-modal__dialog,
.dialog-pop-leave-to .game-result-modal__dialog,
.dialog-pop-enter-from .profile-dialog__card,
.dialog-pop-leave-to .profile-dialog__card {
  opacity: 0;
  transform: scale(0.96) translateY(8px);
}
</style>
