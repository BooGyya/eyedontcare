<script setup lang="ts">
import { computed, ref } from 'vue'
import PageHeader from '../components/common/PageHeader.vue'
import CommunityDialog from '../components/groups/CommunityDialog.vue'
import CommunityGroupCard from '../components/groups/CommunityGroupCard.vue'
import SegmentedTabs from '../components/common/SegmentedTabs.vue'
import { useToast } from '../composables/useToast'
import { communityGroups as initialCommunityGroups } from '../mocks/community'
import { gameCatalog } from '../mocks/pages'
import type {
  CommunityGroup,
  CommunityGroupDraft,
  CommunityGroupFilter,
  CommunityGroupSort,
} from '../types/community'
import groupJoinImage from '../assets/images/illustrations/illustration-group-join.png'
import teamworkImage from '../assets/images/illustrations/illustration-teamwork.png'

const { showToast } = useToast()

const groupFilters: readonly CommunityGroupFilter[] = ['all', 'owned', 'joined']
const filterLabels: Record<CommunityGroupFilter, string> = {
  all: '전체',
  owned: '내가 만든 모임',
  joined: '참여 중인 모임',
}
const groupFilterItems = groupFilters.map((filter) => filterLabels[filter])

const groups = ref<CommunityGroup[]>(
  initialCommunityGroups.map((group) => ({ ...group })),
)
const searchQuery = ref('')
const selectedFilter = ref<CommunityGroupFilter>('all')
const selectedSort = ref<CommunityGroupSort>('latest')
const isCreateDialogOpen = ref(false)
const isJoinDialogOpen = ref(false)
const joinCode = ref('')
const joinCodeError = ref('')
const createErrors = ref<Partial<Record<keyof CommunityGroupDraft, string>>>({})
const isGuestUser = ref(false)

const initialDraft = (): CommunityGroupDraft => ({
  name: '',
  description: '',
  capacity: 8,
  visibility: 'public',
  joinCode: '',
  activity: gameCatalog[0]?.title ?? '',
})
const createDraft = ref<CommunityGroupDraft>(initialDraft())

const filteredGroups = computed(() => {
  const normalizedQuery = searchQuery.value.trim().toLocaleLowerCase()
  const visibleGroups = groups.value.filter((group) => {
    const matchesSearch =
      !normalizedQuery ||
      [group.name, group.description, group.activity, group.leader]
        .join(' ')
        .toLocaleLowerCase()
        .includes(normalizedQuery)
    const matchesFilter =
      selectedFilter.value === 'all' ||
      (selectedFilter.value === 'owned' && group.isOwner) ||
      (selectedFilter.value === 'joined' && group.isJoined)

    return matchesSearch && matchesFilter
  })

  return [...visibleGroups].sort((left, right) => {
    if (selectedSort.value === 'members') return right.members - left.members
    if (selectedSort.value === 'name')
      return left.name.localeCompare(right.name, 'ko')
    return right.createdAt - left.createdAt
  })
})

function selectFilter(label: string) {
  const selected = groupFilters.find((filter) => filterLabels[filter] === label)
  if (selected) selectedFilter.value = selected
}

function clearSearch() {
  searchQuery.value = ''
}

function resetFilters() {
  selectedFilter.value = 'all'
  searchQuery.value = ''
  selectedSort.value = 'latest'
}

function openCreateDialog() {
  if (isGuestUser.value) {
    showToast('소모임 기능은 로그인 후 이용할 수 있어요.')
    return
  }

  createErrors.value = {}
  createDraft.value = initialDraft()
  isCreateDialogOpen.value = true
}

function openJoinDialog() {
  if (isGuestUser.value) {
    showToast('소모임 기능은 로그인 후 이용할 수 있어요.')
    return
  }

  joinCode.value = ''
  joinCodeError.value = ''
  isJoinDialogOpen.value = true
}

function closeCreateDialog() {
  isCreateDialogOpen.value = false
}

function closeJoinDialog() {
  isJoinDialogOpen.value = false
}

function handleGroupAction(group: CommunityGroup) {
  if (isGuestUser.value) {
    showToast('소모임 상세 정보와 입장은 로그인 후 이용할 수 있어요.')
    return
  }

  if (group.isJoined) {
    showToast(`${group.name} 상세 페이지를 준비하고 있어요.`)
    return
  }

  if (group.members >= group.capacity) {
    showToast('정원이 마감된 소모임이에요.')
    return
  }

  if (group.visibility === 'private') {
    joinCode.value = ''
    joinCodeError.value = ''
    isJoinDialogOpen.value = true
    return
  }

  joinGroup(group)
}

function joinGroup(group: CommunityGroup) {
  if (group.isJoined) {
    showToast('이미 참여 중인 소모임이에요.')
    return
  }

  if (group.members >= group.capacity) {
    showToast('정원이 마감된 소모임이에요.')
    return
  }

  groups.value = groups.value.map((item) =>
    item.id === group.id
      ? { ...item, isJoined: true, members: item.members + 1 }
      : item,
  )
  showToast(`${group.name}에 가입했어요!`)
}

function handleJoinByCode() {
  const normalizedCode = joinCode.value.trim().toUpperCase()
  if (!normalizedCode) {
    joinCodeError.value = '참여 코드를 입력해주세요.'
    return
  }

  const target = groups.value.find((group) => group.joinCode === normalizedCode)
  if (!target) {
    joinCodeError.value = '일치하는 참여 코드를 찾지 못했어요.'
    return
  }
  if (target.isJoined) {
    joinCodeError.value = '이미 참여 중인 소모임이에요.'
    return
  }
  if (target.members >= target.capacity) {
    joinCodeError.value = '정원이 마감된 소모임이에요.'
    return
  }

  joinGroup(target)
  closeJoinDialog()
}

function validateCreateDraft() {
  const nextErrors: Partial<Record<keyof CommunityGroupDraft, string>> = {}
  if (!createDraft.value.name.trim())
    nextErrors.name = '소모임 이름을 입력해주세요.'
  if (!createDraft.value.description.trim())
    nextErrors.description = '소모임 소개를 입력해주세요.'
  if (createDraft.value.capacity < 2 || createDraft.value.capacity > 30) {
    nextErrors.capacity = '최대 인원은 2명부터 30명까지 설정할 수 있어요.'
  }
  if (
    createDraft.value.visibility === 'private' &&
    !createDraft.value.joinCode.trim()
  ) {
    nextErrors.joinCode = '비공개 소모임의 참여 코드를 입력해주세요.'
  }

  createErrors.value = nextErrors
  return Object.keys(nextErrors).length === 0
}

function handleCreateGroup() {
  if (!validateCreateDraft()) return

  const draft = createDraft.value
  const newGroup: CommunityGroup = {
    id: `group-${Date.now()}`,
    name: draft.name.trim(),
    description: draft.description.trim(),
    image: groupJoinImage,
    members: 1,
    capacity: draft.capacity,
    visibility: draft.visibility,
    activity: draft.activity,
    leader: '눈썹 최강자',
    isJoined: true,
    isOwner: true,
    createdAt: Date.now(),
    ...(draft.visibility === 'private'
      ? { joinCode: draft.joinCode.trim().toUpperCase() }
      : {}),
  }

  groups.value = [newGroup, ...groups.value]
  selectedFilter.value = 'all'
  closeCreateDialog()
  showToast(`${newGroup.name} 소모임을 만들었어요!`)
}
</script>

<template>
  <section class="community-page">
    <div class="community-page__heading">
      <PageHeader
        eyebrow="PLAY TOGETHER"
        title="소모임"
        description="같은 목표를 가진 친구들과 눈 건강 루틴을 만들어보세요."
      />
      <div class="community-page__actions">
        <button
          data-testid="open-join-dialog"
          type="button"
          class="community-page__secondary-button"
          @click="openJoinDialog"
        >
          코드로 참가
        </button>
        <button
          data-testid="open-create-dialog"
          type="button"
          class="community-page__primary-button"
          @click="openCreateDialog"
        >
          + 소모임 만들기
        </button>
      </div>
    </div>

    <p v-if="isGuestUser" class="community-page__guest-note" role="status">
      게스트 모드에서는 소모임 목록만 둘러볼 수 있어요. 입장과 생성은 로그인 후
      이용할 수 있습니다.
    </p>

    <div class="community-page__toolbar">
      <SegmentedTabs
        label="소모임 필터"
        :items="groupFilterItems"
        :model-value="filterLabels[selectedFilter]"
        @update:model-value="selectFilter"
      />
      <div class="community-page__toolbar-controls">
        <label class="community-page__search-label">
          <span class="sr-only">소모임 검색</span>
          <svg
            class="community-page__search-icon"
            viewBox="0 0 24 24"
            fill="none"
            aria-hidden="true"
          >
            <circle
              cx="10.5"
              cy="10.5"
              r="6.5"
              stroke="currentColor"
              stroke-width="1.8"
            />
            <path
              d="M20 20l-4.35-4.35"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
            />
          </svg>
          <input
            v-model="searchQuery"
            data-testid="community-search"
            type="search"
            placeholder="소모임 검색"
          />
          <button
            v-if="searchQuery"
            type="button"
            class="community-page__search-clear"
            aria-label="검색어 지우기"
            @click="clearSearch"
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
        </label>
        <label class="community-page__sort-label">
          <span class="sr-only">소모임 정렬</span>
          <select v-model="selectedSort" aria-label="소모임 정렬">
            <option value="latest">최신순</option>
            <option value="members">참여 인원순</option>
            <option value="name">이름순</option>
          </select>
        </label>
      </div>
    </div>

    <p class="community-page__result" aria-live="polite">
      {{ filteredGroups.length }}개의 소모임
    </p>

    <TransitionGroup
      v-if="filteredGroups.length"
      tag="div"
      name="group-list"
      class="community-page__list"
    >
      <CommunityGroupCard
        v-for="group in filteredGroups"
        :key="group.id"
        :group="group"
        :is-guest="isGuestUser"
        @join="handleGroupAction"
        @enter="handleGroupAction"
      />
    </TransitionGroup>
    <section v-else class="community-page__empty" aria-live="polite">
      <img
        :src="teamworkImage"
        alt=""
        aria-hidden="true"
        class="community-page__empty-image"
      />
      <strong>찾는 소모임이 없어요.</strong>
      <p>검색어 또는 필터를 바꿔서 다시 찾아보세요.</p>
      <div class="community-page__empty-actions">
        <button
          type="button"
          class="community-page__primary-button"
          @click="openCreateDialog"
        >
          소모임 만들기
        </button>
        <button
          type="button"
          class="community-page__empty-reset"
          @click="resetFilters"
        >
          필터 초기화
        </button>
      </div>
    </section>

    <CommunityDialog
      :open="isCreateDialogOpen"
      title="소모임 만들기"
      description="친구들을 초대할 나만의 소모임을 만들어보세요."
      :close-on-backdrop="false"
      @close="closeCreateDialog"
    >
      <form class="community-form" @submit.prevent="handleCreateGroup">
        <label>
          <span>소모임 이름</span>
          <input
            v-model="createDraft.name"
            data-testid="create-group-name"
            data-dialog-initial-focus
            type="text"
            maxlength="30"
          />
          <small v-if="createErrors.name" role="alert">{{
            createErrors.name
          }}</small>
        </label>
        <label>
          <span>소개</span>
          <textarea
            v-model="createDraft.description"
            data-testid="create-group-description"
            rows="3"
            maxlength="120"
          />
          <small v-if="createErrors.description" role="alert">{{
            createErrors.description
          }}</small>
        </label>
        <div class="community-form__row">
          <label>
            <span>최대 인원</span>
            <input
              v-model.number="createDraft.capacity"
              data-testid="create-group-capacity"
              type="number"
              min="2"
              max="30"
            />
            <small v-if="createErrors.capacity" role="alert">{{
              createErrors.capacity
            }}</small>
          </label>
          <label>
            <span>주요 활동</span>
            <select v-model="createDraft.activity">
              <option
                v-for="game in gameCatalog"
                :key="game.id"
                :value="game.title"
              >
                {{ game.title }}
              </option>
            </select>
          </label>
        </div>
        <fieldset>
          <legend>공개 설정</legend>
          <label
            ><input
              v-model="createDraft.visibility"
              type="radio"
              value="public"
            />
            공개</label
          >
          <label
            ><input
              v-model="createDraft.visibility"
              type="radio"
              value="private"
            />
            비공개</label
          >
        </fieldset>
        <label v-if="createDraft.visibility === 'private'">
          <span>참여 코드</span>
          <input
            v-model="createDraft.joinCode"
            type="text"
            maxlength="12"
            placeholder="친구에게 공유할 코드를 입력해주세요"
          />
          <small v-if="createErrors.joinCode" role="alert">{{
            createErrors.joinCode
          }}</small>
        </label>
        <button
          data-testid="create-group-submit"
          class="community-form__submit"
          type="submit"
        >
          소모임 만들기
        </button>
      </form>
    </CommunityDialog>

    <CommunityDialog
      :open="isJoinDialogOpen"
      title="코드로 참가하기"
      description="소모임 코드를 입력하고 친구들과 함께 게임해요."
      @close="closeJoinDialog"
    >
      <form class="community-form" @submit.prevent="handleJoinByCode">
        <label>
          <span>참여 코드</span>
          <input
            v-model="joinCode"
            data-testid="join-code-input"
            data-dialog-initial-focus
            type="text"
            maxlength="12"
            placeholder="참여 코드를 입력해주세요"
            @input="joinCodeError = ''"
          />
          <small v-if="joinCodeError" role="alert">{{ joinCodeError }}</small>
        </label>
        <p class="community-form__hint">
          참여 코드는 소모임 리더 또는 참여자에게 받아 입력해주세요.
        </p>
        <button class="community-form__submit" type="submit">가입하기</button>
      </form>
    </CommunityDialog>
  </section>
</template>

<style scoped>
.community-page {
  padding: 32px 0 58px;
}
.community-page__heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 24px;
}
.community-page__heading .page-header {
  margin-bottom: 26px;
}
.community-page__actions {
  display: flex;
  gap: 9px;
  margin-bottom: 28px;
}
.community-page__primary-button,
.community-page__secondary-button {
  padding: 11px 15px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 800;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) ease,
    border-color var(--duration-fast) ease,
    color var(--duration-fast) ease;
}
.community-page__primary-button {
  color: #fff;
  background: var(--color-accent-blue);
}
.community-page__primary-button:hover {
  background: var(--color-primary-hover);
}
.community-page__secondary-button {
  border: 1px solid var(--color-line);
  color: var(--color-ink);
  background: #fff;
}
.community-page__secondary-button:hover {
  border-color: var(--color-accent-blue);
  color: var(--color-accent-blue);
}
.community-page__guest-note {
  margin: 0 0 18px;
  padding: 12px 14px;
  border: 1px solid var(--color-line);
  border-radius: 12px;
  color: var(--color-muted);
  background: var(--color-surface-soft);
  font-size: 13px;
}
.community-page__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 15px 0;
  border-top: 1px solid var(--color-line);
  border-bottom: 1px solid var(--color-line);
}
.community-page__toolbar-controls {
  display: flex;
  gap: 9px;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
.community-page__search-label,
.community-page__sort-label {
  display: flex;
  align-items: center;
  height: 39px;
  border: 1px solid var(--color-line);
  border-radius: 10px;
  background: #fff;
}
.community-page__search-label {
  gap: 7px;
  padding: 0 12px;
  color: var(--color-muted);
}
.community-page__search-icon {
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
}
.community-page__search-label input,
.community-page__sort-label select {
  min-width: 0;
  border: 0;
  outline: 0;
  color: var(--color-ink);
  background: transparent;
  font: inherit;
  font-size: 13px;
}
.community-page__search-label input {
  width: 160px;
}
.community-page__search-clear {
  display: grid;
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
  place-items: center;
  border-radius: 50%;
  color: var(--color-muted);
  background: transparent;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) ease,
    color var(--duration-fast) ease;
}
.community-page__search-clear svg {
  width: 14px;
  height: 14px;
}
.community-page__search-clear:hover {
  color: var(--color-ink);
  background: var(--color-surface-soft);
}
.community-page__sort-label select {
  height: 100%;
  padding: 0 10px;
  cursor: pointer;
}
.community-page__result {
  margin: 18px 0 13px;
  color: var(--color-muted);
  font-size: 13px;
}
.community-page__result::first-letter {
  color: var(--color-ink);
  font-weight: 800;
}
.community-page__list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}
.group-list-enter-active {
  transition:
    opacity 240ms var(--ease-out),
    transform 240ms var(--ease-out);
}
.group-list-move {
  transition: transform var(--duration-base) var(--ease-out);
}
.group-list-enter-from {
  opacity: 0;
  transform: translateY(10px);
}
.community-page__empty {
  display: grid;
  gap: 6px;
  place-items: center;
  min-height: 250px;
  padding: 32px;
  border: 1px dashed var(--color-line);
  border-radius: var(--radius-card);
  text-align: center;
}
.community-page__empty-image {
  width: 140px;
  height: 140px;
  margin-bottom: 6px;
  object-fit: contain;
}
.community-page__empty strong {
  font-size: 18px;
}
.community-page__empty p {
  margin: 7px 0 0;
  color: var(--color-muted);
  font-size: 14px;
}
.community-page__empty-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 18px;
}
.community-page__empty-reset {
  padding: 4px 6px;
  color: var(--color-accent-blue);
  background: transparent;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
  transition: color var(--duration-fast) ease;
}
.community-page__empty-reset:hover {
  color: var(--color-primary-hover);
}
.community-form {
  display: grid;
  gap: 17px;
}
.community-form > label,
.community-form__row > label {
  display: grid;
  gap: 7px;
  color: var(--color-ink);
  font-size: 13px;
  font-weight: 800;
}
.community-form input:not([type='radio']),
.community-form textarea,
.community-form select {
  width: 100%;
  min-width: 0;
  padding: 11px 12px;
  border: 1px solid var(--color-line);
  border-radius: 10px;
  color: var(--color-ink);
  background: #fff;
  font: inherit;
  font-size: 14px;
  font-weight: 400;
  outline: 0;
  resize: vertical;
  transition:
    border-color var(--duration-fast) ease,
    box-shadow var(--duration-fast) ease;
}
.community-form input:focus,
.community-form textarea:focus,
.community-form select:focus {
  border-color: var(--color-accent-blue);
  box-shadow: 0 0 0 3px rgba(79, 116, 219, 0.14);
}
.community-form small {
  color: var(--color-danger, #c2455a);
  font-size: 12px;
  font-weight: 500;
}
.community-form__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.community-form fieldset {
  display: flex;
  gap: 17px;
  margin: 0;
  padding: 0;
  border: 0;
}
.community-form legend {
  margin-bottom: 9px;
  font-size: 13px;
  font-weight: 800;
}
.community-form fieldset label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
.community-form__hint {
  margin: -4px 0 0;
  padding: 11px 12px;
  border-radius: 10px;
  color: var(--color-muted);
  background: var(--color-surface-soft);
  font-size: 12px;
  line-height: 1.55;
  word-break: keep-all;
}
.community-form__submit {
  width: 100%;
  padding: 13px;
  border-radius: 10px;
  color: #fff;
  background: var(--color-accent-blue);
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}
.community-form__submit:hover {
  background: var(--color-primary-hover);
}
.community-form__submit:active {
  transform: translateY(1px);
}

@media (max-width: 1100px) {
  .community-page__list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 760px) {
  .community-page__heading,
  .community-page__toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .community-page__actions {
    margin-bottom: 22px;
  }
  .community-page__toolbar-controls {
    width: 100%;
  }
  .community-page__search-label {
    flex: 1;
  }
  .community-page__search-label input {
    width: 100%;
  }
}
@media (max-width: 640px) {
  .community-page {
    padding-top: 24px;
  }
  .community-page__heading {
    gap: 0;
  }
  .community-page__heading .page-header {
    margin-bottom: 18px;
  }
  .community-page__actions {
    width: 100%;
    margin-bottom: 20px;
  }
  .community-page__actions button {
    flex: 1;
  }
  .community-page__list,
  .community-form__row {
    grid-template-columns: 1fr;
  }
  .community-page__toolbar-controls {
    flex-wrap: wrap;
  }
  .community-page__sort-label {
    flex: 1;
  }
  .community-page__sort-label select {
    width: 100%;
  }
}
</style>
