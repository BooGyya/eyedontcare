<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useToast } from '../composables/useToast'
import { gameResultRecords } from '../mocks/gameResults'
import { profileData, profileState } from '../mocks/profile'
import type { GameOutcome, GameResultDetail } from '../types/gameResult'

const { showToast } = useToast()
const isEditing = ref(false)
const nickname = ref(profileState.nickname)
const draftNickname = ref(profileState.nickname)
const selectedAvatarId = ref(profileData.avatars[0]?.id ?? '')
const draftAvatarId = ref(selectedAvatarId.value)
const isNicknameChecked = ref(false)
const isPasswordDialogOpen = ref(false)
const currentPassword = ref('')
const changePassword = ref('')
const changePasswordConfirmation = ref('')
const isWithdrawDialogOpen = ref(false)
const selectedRecord = ref<GameResultDetail | null>(null)
const closeButton = ref<globalThis.HTMLButtonElement | null>(null)
const modalDialog = ref<globalThis.HTMLElement | null>(null)
let lastFocusedElement: globalThis.HTMLElement | null = null
let previousBodyOverflow = ''

const selectedAvatar = computed(
  () =>
    profileData.avatars.find(
      (avatar) => avatar.id === selectedAvatarId.value,
    ) ?? profileData.avatars[0],
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

function getMyParticipant(record: GameResultDetail) {
  return (
    record.participants.find(
      (participant) => participant.participantType === 'USER',
    ) ?? record.participants[0]
  )
}

function getDurationMs(record: GameResultDetail) {
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

function formatPlayMode(playMode: GameResultDetail['playMode']) {
  return playMode === 'MULTI' ? '멀티플레이' : '싱글플레이'
}

function getOutcomeLabel(outcome: GameOutcome) {
  return {
    WIN: '승리',
    LOSE: '패배',
    DRAW: '무승부',
    COMPLETED: '완료',
  }[outcome]
}

function getOutcomeIcon(outcome: GameOutcome) {
  return {
    WIN: '🏆',
    LOSE: '◌',
    DRAW: '🤝',
    COMPLETED: '✓',
  }[outcome]
}

function getSummary(record: GameResultDetail) {
  const participant = getMyParticipant(record)

  if (participant.outcome === 'COMPLETED') {
    return `${record.gameName}를 완료했어요.`
  }

  if (participant.outcome === 'WIN') {
    return `${record.gameName}에서 ${participant.rank}위로 승리했어요.`
  }

  return `${record.gameName}에서 ${participant.rank}위를 기록했어요.`
}

function handleOpenEdit() {
  draftNickname.value = nickname.value
  draftAvatarId.value = selectedAvatarId.value
  isNicknameChecked.value = false
  isEditing.value = true
}

function handleNicknameChange() {
  isNicknameChecked.value = false
}

function handleCheckNickname() {
  if (draftNickname.value.trim().length < 2) {
    showToast('닉네임은 2자 이상 입력해 주세요.')
    return
  }

  isNicknameChecked.value = true
  showToast('사용 가능한 닉네임이에요.')
}

function handleSaveProfile() {
  if (!isNicknameChecked.value && draftNickname.value !== nickname.value) {
    showToast('닉네임 중복 확인을 먼저 해주세요.')
    return
  }

  nickname.value = draftNickname.value.trim()
  selectedAvatarId.value = draftAvatarId.value
  profileState.nickname = nickname.value
  profileState.avatar = selectedAvatar.value?.image ?? profileData.avatar
  isEditing.value = false
  showToast('프로필 정보가 저장되었어요.')
}

function handleCancelEdit() {
  isEditing.value = false
}

function handleOpenRecord(record: GameResultDetail, event: globalThis.Event) {
  lastFocusedElement = event.currentTarget as globalThis.HTMLElement
  selectedRecord.value = record
}

function handleRecordKeydown(
  record: GameResultDetail,
  event: globalThis.KeyboardEvent,
) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    handleOpenRecord(record, event)
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

function handleSubmitPasswordChange() {
  if (!currentPassword.value.trim()) {
    showToast('현재 비밀번호를 입력해 주세요.')
    return
  }

  if (!changePassword.value.trim()) {
    showToast('새 비밀번호를 입력해 주세요.')
    return
  }

  if (!changePasswordsMatch.value) {
    showToast('새 비밀번호와 확인 비밀번호가 일치하지 않아요.')
    return
  }

  isPasswordDialogOpen.value = false
  showToast('비밀번호가 변경되었어요.')
}

function handleWithdraw() {
  isWithdrawDialogOpen.value = true
}

function handleConfirmWithdraw() {
  isWithdrawDialogOpen.value = false
  showToast('탈퇴가 접수되었어요. 그동안 함께해 주셔서 감사합니다.')
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
        <p>꾸준히 눈을 쉬게 해준 지 {{ profileData.journeyDays }}일째예요.</p>
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
              maxlength="12"
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
            v-for="avatar in profileData.avatars"
            :key="avatar.id"
            :aria-checked="avatar.id === draftAvatarId"
            :class="{
              'profile-page__avatar-option--selected':
                avatar.id === draftAvatarId,
            }"
            role="radio"
            type="button"
            @click="draftAvatarId = avatar.id"
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
      <ul v-if="gameResultRecords.length">
        <li
          v-for="record in gameResultRecords"
          :key="record.resultId"
          tabindex="0"
          role="button"
          @click="handleOpenRecord(record, $event)"
          @keydown="handleRecordKeydown(record, $event)"
        >
          <span
            :class="`profile-page__record-icon--${getMyParticipant(record).outcome.toLowerCase()}`"
            aria-hidden="true"
            >{{ getOutcomeIcon(getMyParticipant(record).outcome) }}</span
          >
          <p>
            <b>{{ getSummary(record) }}</b
            ><small
              >{{ formatPlayMode(record.playMode) }} ·
              {{ formatStartedAt(record.startedAt) }} ·
              {{ formatDuration(getDurationMs(record)) }} 플레이</small
            >
          </p>
          <strong>{{ getMyParticipant(record).rank }}위</strong
          ><span class="profile-page__record-arrow" aria-hidden="true">›</span>
        </li>
      </ul>
    </section>

    <section class="profile-page__account" aria-label="계정 관리">
      <div>
        <span>ACCOUNT</span>
        <h2>계정 관리</h2>
        <p>서비스 이용을 종료하거나 계정 정보를 관리할 수 있어요.</p>
      </div>
      <div class="profile-page__account-actions">
        <button type="button" @click="handleOpenPasswordChange">
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
    <div
      v-if="selectedRecord"
      class="game-result-modal"
      @click.self="handleCloseRecord"
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
          ×
        </button>
        <header>
          <span>경기 결과</span>
          <p>
            {{ formatStartedAt(selectedRecord.startedAt) }} ·
            {{ formatPlayMode(selectedRecord.playMode) }}
          </p>
          <h2 id="game-result-title">{{ selectedRecord.gameName }}</h2>
        </header>
        <div class="game-result-modal__outcome">
          <span>{{
            getOutcomeIcon(getMyParticipant(selectedRecord).outcome)
          }}</span
          ><strong
            >{{ getOutcomeLabel(getMyParticipant(selectedRecord).outcome) }} ·
            {{ getMyParticipant(selectedRecord).rank }}위</strong
          >
        </div>
        <div class="game-result-modal__stats">
          <article>
            <span>점수</span
            ><strong>{{ getMyParticipant(selectedRecord).score }}점</strong>
          </article>
          <article>
            <span>생존 시간</span
            ><strong>{{
              formatDuration(
                selectedRecord.gameResult['1']?.survivalTimeMs ??
                  getDurationMs(selectedRecord),
              )
            }}</strong>
          </article>
          <article>
            <span>플레이 시간</span
            ><strong>{{
              formatDuration(getDurationMs(selectedRecord))
            }}</strong>
          </article>
        </div>
        <div class="game-result-modal__participants">
          <div>
            <span>플레이어</span><span>결과</span><span>순위</span
            ><span>점수</span>
          </div>
          <div
            v-for="participant in selectedRecord.participants"
            :key="participant.slotNo"
          >
            <b>{{ participant.displayName }}</b
            ><span>{{ getOutcomeLabel(participant.outcome) }}</span
            ><span>{{ participant.rank }}위</span
            ><span>{{ participant.score }}</span>
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

    <div
      v-if="isPasswordDialogOpen"
      class="profile-dialog"
      @click.self="handleClosePasswordChange"
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
            placeholder="변경할 비밀번호를 입력해 주세요"
          />
        </label>
        <label class="profile-page__field">
          <span>새 비밀번호 확인</span>
          <input
            v-model="changePasswordConfirmation"
            type="password"
            autocomplete="new-password"
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

    <div
      v-if="isWithdrawDialogOpen"
      class="profile-dialog"
      @click.self="isWithdrawDialogOpen = false"
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
          탈퇴하면 게임 기록과 랭킹, 소모임 활동 내역이 모두 삭제되고 되돌릴 수
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
  background: linear-gradient(135deg, var(--color-purple-soft), #fff);
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
  letter-spacing: -0.06em;
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
  letter-spacing: -0.04em;
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
  color: #d55555;
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
}
.profile-page__records li:hover {
  border-radius: 12px;
  background: var(--color-surface-soft);
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
  place-items: center;
  border-radius: 12px;
  font-weight: 800;
}
.profile-page__record-icon--win {
  background: var(--color-purple-soft);
}
.profile-page__record-icon--lose {
  color: #657087;
  background: #f1f3f7;
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
  color: var(--color-accent-blue);
  font-size: 24px;
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
}
.profile-page__account-actions .profile-page__withdraw-button {
  border-color: #f1cbcb;
  color: #c65151;
  background: #fffafa;
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
  width: 34px;
  height: 34px;
  border-radius: 50%;
  color: var(--color-muted);
  background: var(--color-surface-soft);
  font-size: 25px;
  cursor: pointer;
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
.game-result-modal__outcome span {
  font-size: 29px;
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
  grid-template-columns: minmax(0, 1.6fr) repeat(3, 0.7fr);
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
  background: #e05a5a;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}
.profile-dialog__danger-button:hover {
  background: #c94b4b;
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
</style>
