<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '../components/common/PageHeader.vue'
import { useToast } from '../composables/useToast'
import { useAuthStore } from '../stores/auth'
import { ApiError } from '../api/http'
import {
  getGroup,
  joinGroupById,
  leaveGroup,
  deleteGroup,
  kickMember,
  type GroupDetailResponse,
} from '../api/group'
import { communityPosts } from '../mocks/community'
import type { CommunityPost } from '../types/community'
import {
  COMMENT_COOLDOWN_MS,
  COMMENT_MAX_LENGTH,
  POST_MAX_LENGTH,
} from '../types/community'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { showToast } = useToast()

const groupId = computed(() => String(route.params.groupId))
const detail = ref<GroupDetailResponse | null>(null)
const isLoading = ref(false)
const errorMessage = ref('')
const isBusy = ref(false)

async function load() {
  if (!auth.isAuthenticated) return
  isLoading.value = true
  errorMessage.value = ''
  detail.value = null
  try {
    detail.value = await getGroup(groupId.value)
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError ? error.message : '소모임을 불러오지 못했어요.'
  } finally {
    isLoading.value = false
  }
}

function roleLabel(role: string) {
  return role === 'OWNER' ? '방장' : '멤버'
}

// --- 게임 후기 게시판 (mock-first: 백엔드 게시판 API 연동 전까지 프론트에서만 관리) ---
const posts = ref<CommunityPost[]>([])
const openPostIds = ref<Record<string, boolean>>({})
const commentDrafts = ref<Record<string, string>>({})
const isComposerOpen = ref(false)
const composerContent = ref('')

function seedPosts() {
  // 실제 소모임은 백엔드가 숫자 id를 내려줘서 mock의 문자열 id와 일치하지 않을 수 있다.
  // 매칭되는 게시글이 없으면 게시판이 비어 보이지 않도록 기본 후기 세트로 대체한다.
  const matched = communityPosts.filter(
    (post) => post.groupId === groupId.value,
  )
  const source = matched.length > 0 ? matched : communityPosts.slice(0, 3)
  posts.value = source.map((post) => ({
    ...post,
    comments: post.comments.map((comment) => ({ ...comment })),
  }))
}

function toggleComments(postId: string) {
  openPostIds.value[postId] = !openPostIds.value[postId]
}

function toggleComposer() {
  isComposerOpen.value = !isComposerOpen.value
  if (!isComposerOpen.value) composerContent.value = ''
}

// 입력 길이에 맞춰 textarea 높이를 늘려 긴 글이 아래쪽으로 확장되게 한다.
function autoGrowComposer(event: globalThis.Event) {
  const el = event.target as globalThis.HTMLTextAreaElement
  el.style.height = 'auto'
  el.style.height = `${el.scrollHeight}px`
}

function submitPost() {
  const content = composerContent.value.trim()
  if (!content) {
    showToast('내용을 입력해 주세요.')
    return
  }
  // maxlength로 입력은 막히지만, 붙여넣기/우회 입력 대비로 제출 시에도 한 번 더 막는다.
  if (content.length > POST_MAX_LENGTH) {
    showToast(`후기는 ${POST_MAX_LENGTH}자까지 입력할 수 있어요.`)
    return
  }
  posts.value.unshift({
    id: `local-post-${Date.now()}`,
    groupId: groupId.value,
    author: auth.user.nickname,
    isLeader: detail.value?.isOwner ?? false,
    content,
    timeLabel: '방금',
    comments: [],
  })
  composerContent.value = ''
  isComposerOpen.value = false
  showToast('후기를 남겼어요!')
}

// 마지막으로 댓글을 작성한 시각. 짧은 간격의 연속 작성(난사)을 막는 쿨다운 기준이다.
let lastCommentAt = 0

function submitComment(post: CommunityPost) {
  const content = (commentDrafts.value[post.id] ?? '').trim()
  if (!content) return
  // maxlength로 입력은 막히지만, 붙여넣기/우회 입력 대비로 제출 시에도 한 번 더 막는다.
  if (content.length > COMMENT_MAX_LENGTH) {
    showToast(`댓글은 ${COMMENT_MAX_LENGTH}자까지 입력할 수 있어요.`)
    return
  }
  // 연속 작성 쿨다운: 버튼 비활성화만으로는 우회되므로 제출 로직에서 막는다.
  const now = Date.now()
  if (now - lastCommentAt < COMMENT_COOLDOWN_MS) {
    showToast('댓글을 너무 빠르게 작성하고 있어요. 잠시 후 다시 시도해 주세요.')
    return
  }
  // 동일 내용 중복 방지: 이 글의 마지막 댓글과 작성자·내용이 같으면 막는다.
  const lastComment = post.comments[post.comments.length - 1]
  if (
    lastComment &&
    lastComment.author === auth.user.nickname &&
    lastComment.content === content
  ) {
    showToast('같은 내용을 연속으로 작성할 수 없어요.')
    return
  }
  lastCommentAt = now
  post.comments.push({
    id: `local-comment-${Date.now()}`,
    author: auth.user.nickname,
    content,
    timeLabel: '방금',
  })
  commentDrafts.value[post.id] = ''
}

async function handleLeave() {
  if (!detail.value || isBusy.value) return
  isBusy.value = true
  try {
    await leaveGroup(groupId.value)
    showToast('소모임에서 나갔어요.')
    void router.push({ name: 'community' })
  } catch (error) {
    showToast(
      error instanceof ApiError ? error.message : '나가기에 실패했어요.',
    )
  } finally {
    isBusy.value = false
  }
}

async function handleDelete() {
  if (!detail.value || isBusy.value) return
  // 삭제는 되돌릴 수 없으므로 한 번 더 확인한다.
  if (!globalThis.confirm('소모임을 삭제하면 되돌릴 수 없어요. 삭제할까요?'))
    return
  isBusy.value = true
  try {
    await deleteGroup(groupId.value)
    showToast('소모임을 삭제했어요.')
    void router.push({ name: 'community' })
  } catch (error) {
    showToast(error instanceof ApiError ? error.message : '삭제에 실패했어요.')
  } finally {
    isBusy.value = false
  }
}

async function handleKick(userId: number, nickname: string) {
  if (!detail.value || isBusy.value) return
  if (!globalThis.confirm(`'${nickname}' 님을 강퇴할까요?`)) return
  isBusy.value = true
  try {
    await kickMember(groupId.value, userId)
    showToast('멤버를 강퇴했어요.')
    await load()
  } catch (error) {
    showToast(error instanceof ApiError ? error.message : '강퇴에 실패했어요.')
  } finally {
    isBusy.value = false
  }
}

async function handleJoin() {
  if (!detail.value || isBusy.value) return
  isBusy.value = true
  try {
    await joinGroupById(groupId.value)
    showToast('가입했어요!')
    await load()
  } catch (error) {
    showToast(error instanceof ApiError ? error.message : '가입에 실패했어요.')
  } finally {
    isBusy.value = false
  }
}

onMounted(() => {
  void load()
  seedPosts()
})
watch(
  () => auth.isAuthenticated,
  (authenticated) => {
    if (authenticated) void load()
  },
)
</script>

<template>
  <section class="community-detail">
    <PageHeader
      eyebrow="COMMUNITY"
      :title="detail?.name ?? '소모임'"
      description="소모임 정보와 참여자를 확인하세요."
    />

    <button
      type="button"
      class="community-detail__back"
      @click="router.push({ name: 'community' })"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path
          d="M14 6l-6 6 6 6"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
      소모임 목록으로
    </button>

    <div
      v-if="!auth.isAuthenticated"
      class="community-detail__status"
      role="status"
    >
      <p>소모임 상세는 로그인 후 확인할 수 있어요.</p>
      <button
        type="button"
        class="community-detail__primary"
        @click="auth.openLogin"
      >
        로그인하기
      </button>
    </div>

    <p v-else-if="isLoading" class="community-detail__status" role="status">
      소모임을 불러오는 중이에요…
    </p>

    <div v-else-if="errorMessage" class="community-detail__status" role="alert">
      <p>{{ errorMessage }}</p>
      <button type="button" class="community-detail__ghost" @click="load">
        다시 시도
      </button>
    </div>

    <template v-else-if="detail">
      <article class="community-detail__card">
        <div class="community-detail__meta">
          <span
            :class="`community-detail__visibility--${detail.visibility.toLowerCase()}`"
          >
            <svg
              v-if="detail.visibility === 'PUBLIC'"
              class="community-detail__icon"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <circle
                cx="12"
                cy="12"
                r="9"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
              />
              <path
                d="M3 12h18M12 3c3 3.5 3 14.5 0 18M12 3c-3 3.5-3 14.5 0 18"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
              />
            </svg>
            <svg
              v-else
              class="community-detail__icon"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <rect
                x="5"
                y="11"
                width="14"
                height="9"
                rx="2"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
              />
              <path
                d="M8 11V8a4 4 0 0 1 8 0v3"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
              />
            </svg>
            {{ detail.visibility === 'PUBLIC' ? '공개' : '비공개' }}
          </span>
          <span class="community-detail__member-count">
            <svg
              class="community-detail__icon"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <circle
                cx="12"
                cy="8"
                r="3.4"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
              />
              <path
                d="M5 20c1.2-4 4-6 7-6s5.8 2 7 6"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
              />
            </svg>
            {{ detail.members }} / {{ detail.capacity }}명
          </span>
        </div>
        <p class="community-detail__description">{{ detail.description }}</p>
        <p class="community-detail__leader">
          <svg
            class="community-detail__icon"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <path
              d="M4 8l3 3 5-6 5 6 3-3-1.5 10h-13L4 8Z"
              fill="var(--color-gold)"
            />
          </svg>
          리더 <b>{{ detail.leader }}</b>
        </p>
        <p
          v-if="detail.isJoined && detail.joinCode"
          class="community-detail__code"
        >
          <svg
            class="community-detail__icon"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <circle
              cx="7.5"
              cy="15.5"
              r="3"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
            />
            <path
              d="M9.6 13.4 18 5"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
            />
            <path
              d="M14.5 9.5l2 2M16.5 7.5l2 2"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
            />
          </svg>
          참여 코드 <b>{{ detail.joinCode }}</b>
        </p>

        <div class="community-detail__actions">
          <button
            v-if="detail.isOwner"
            type="button"
            class="community-detail__danger"
            :disabled="isBusy"
            @click="handleDelete"
          >
            소모임 삭제
          </button>
          <button
            v-else-if="detail.isJoined"
            type="button"
            class="community-detail__ghost"
            :disabled="isBusy"
            @click="handleLeave"
          >
            소모임 나가기
          </button>
          <button
            v-else-if="!detail.isJoined && detail.visibility === 'PUBLIC'"
            type="button"
            class="community-detail__primary"
            :disabled="isBusy || detail.members >= detail.capacity"
            @click="handleJoin"
          >
            {{ detail.members >= detail.capacity ? '정원 마감' : '가입하기' }}
          </button>
          <p v-else-if="!detail.isJoined" class="community-detail__hint">
            비공개 소모임은 참여 코드로 입장할 수 있어요.
          </p>
        </div>
      </article>

      <section class="community-detail__members" aria-label="참여자 명단">
        <h2>참여자 {{ detail.memberList.length }}명</h2>
        <ul>
          <li v-for="member in detail.memberList" :key="member.userId">
            <span class="community-detail__member-name">{{
              member.nickname
            }}</span>
            <span class="community-detail__member-meta">
              <span class="community-detail__member-role">
                <svg
                  v-if="member.role === 'OWNER'"
                  class="community-detail__icon"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path
                    d="M4 8l3 3 5-6 5 6 3-3-1.5 10h-13L4 8Z"
                    fill="var(--color-gold)"
                  />
                </svg>
                {{ roleLabel(member.role) }}
              </span>
              <button
                v-if="detail.isOwner && member.role !== 'OWNER'"
                type="button"
                class="community-detail__kick"
                :disabled="isBusy"
                @click="handleKick(member.userId, member.nickname)"
              >
                강퇴
              </button>
            </span>
          </li>
        </ul>
      </section>

      <section class="community-detail__board" aria-label="게임 후기 게시판">
        <div class="community-detail__board-heading">
          <h2>게임 후기 게시판</h2>
          <button
            v-if="detail.isJoined || detail.isOwner"
            type="button"
            class="community-detail__primary community-detail__board-write"
            @click="toggleComposer"
          >
            글쓰기
          </button>
        </div>

        <p
          v-if="!(detail.isJoined || detail.isOwner)"
          class="community-detail__hint"
        >
          소모임에 가입하면 후기를 남길 수 있어요.
        </p>

        <form
          v-if="isComposerOpen"
          class="community-detail__composer"
          @submit.prevent="submitPost"
        >
          <textarea
            v-model="composerContent"
            class="community-detail__composer-textarea"
            placeholder="게임 후기를 남겨보세요"
            rows="3"
            :maxlength="POST_MAX_LENGTH"
            @input="autoGrowComposer"
          />
          <div class="community-detail__composer-actions">
            <span class="community-detail__composer-count">
              {{ composerContent.length }}/{{ POST_MAX_LENGTH }}
            </span>
            <button
              type="button"
              class="community-detail__ghost"
              @click="toggleComposer"
            >
              취소
            </button>
            <button type="submit" class="community-detail__primary">
              등록
            </button>
          </div>
        </form>

        <p v-if="posts.length === 0" class="community-detail__board-empty">
          아직 후기가 없어요. 첫 후기를 남겨보세요!
        </p>

        <ul v-else class="community-detail__board-list">
          <li
            v-for="post in posts"
            :key="post.id"
            class="community-detail__post"
          >
            <div class="community-detail__post-header">
              <span class="community-detail__post-author">
                <svg
                  v-if="post.isLeader"
                  class="community-detail__icon"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path
                    d="M4 8l3 3 5-6 5 6 3-3-1.5 10h-13L4 8Z"
                    fill="var(--color-gold)"
                  />
                </svg>
                <svg
                  v-else
                  class="community-detail__icon"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <circle
                    cx="12"
                    cy="8"
                    r="3.4"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.8"
                  />
                  <path
                    d="M5 20c1.2-4 4-6 7-6s5.8 2 7 6"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                  />
                </svg>
                {{ post.author }}
              </span>
              <span class="community-detail__post-time">{{
                post.timeLabel
              }}</span>
            </div>
            <p class="community-detail__post-content">{{ post.content }}</p>
            <button
              type="button"
              class="community-detail__comment-toggle"
              @click="toggleComments(post.id)"
            >
              댓글 {{ post.comments.length }}
            </button>

            <div v-if="openPostIds[post.id]" class="community-detail__comments">
              <ul
                v-if="post.comments.length"
                class="community-detail__comment-list"
              >
                <li v-for="comment in post.comments" :key="comment.id">
                  <span class="community-detail__comment-author">{{
                    comment.author
                  }}</span>
                  <span class="community-detail__comment-content">{{
                    comment.content
                  }}</span>
                  <span class="community-detail__comment-time">{{
                    comment.timeLabel
                  }}</span>
                </li>
              </ul>
              <p v-else class="community-detail__comment-empty">
                아직 댓글이 없어요.
              </p>

              <div
                v-if="detail.isJoined || detail.isOwner"
                class="community-detail__comment-form"
              >
                <input
                  v-model="commentDrafts[post.id]"
                  type="text"
                  :maxlength="COMMENT_MAX_LENGTH"
                  placeholder="댓글을 남겨보세요"
                  @keyup.enter="submitComment(post)"
                />
                <span class="community-detail__comment-count">
                  {{ (commentDrafts[post.id] ?? '').length }}/{{
                    COMMENT_MAX_LENGTH
                  }}
                </span>
                <button
                  type="button"
                  :disabled="!(commentDrafts[post.id] ?? '').trim()"
                  @click="submitComment(post)"
                >
                  등록
                </button>
              </div>
            </div>
          </li>
        </ul>
      </section>
    </template>
  </section>
</template>

<style scoped>
.community-detail {
  padding: 32px 0 54px;
}
.community-detail__back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 16px;
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  font-weight: 700;
  font-size: 13px;
  color: var(--color-muted);
  cursor: pointer;
  transition: color var(--duration-fast) ease;
}
.community-detail__back svg {
  width: 15px;
  height: 15px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
}
.community-detail__back:hover {
  color: var(--color-accent-blue);
}
.community-detail__icon {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
  vertical-align: middle;
}
.community-detail__status {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  margin-top: 32px;
  padding: 40px 20px;
  color: var(--color-muted);
  text-align: center;
}
.community-detail__card {
  padding: 22px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: #fff;
  box-shadow: var(--shadow-card);
}
.community-detail__meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--color-muted);
  font-size: 13px;
  font-weight: 700;
}
.community-detail__visibility--public,
.community-detail__visibility--private {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 9px;
  border-radius: var(--radius-button);
  font-size: 11px;
  font-weight: 800;
}
.community-detail__visibility--public {
  color: #287c66;
  background: var(--color-mint-soft);
}
.community-detail__visibility--private {
  color: #67509d;
  background: var(--color-purple-soft);
}
.community-detail__member-count {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.community-detail__description {
  margin: 14px 0;
  color: var(--color-ink);
  line-height: 1.6;
  word-break: keep-all;
}
.community-detail__leader,
.community-detail__code {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 4px 0;
  color: var(--color-muted);
  font-size: 13px;
}
.community-detail__actions {
  margin-top: 18px;
}
.community-detail__primary,
.community-detail__ghost {
  padding: 10px 18px;
  border-radius: 10px;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}
.community-detail__primary {
  border: 0;
  color: #fff;
  background: var(--color-accent-blue);
}
.community-detail__primary:disabled {
  background: var(--color-muted);
  cursor: not-allowed;
}
.community-detail__ghost {
  border: 1px solid var(--color-line);
  color: var(--color-ink);
  background: #fff;
}
.community-detail__danger {
  border: 1px solid #e2b4b4;
  color: #c0392b;
  background: #fff;
}
.community-detail__danger:disabled {
  color: var(--color-muted);
  cursor: not-allowed;
}
.community-detail__hint {
  color: var(--color-muted);
  font-size: 13px;
}
.community-detail__members {
  margin-top: 24px;
}
.community-detail__members h2 {
  margin-bottom: 12px;
  font-size: 18px;
}
.community-detail__members ul {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.community-detail__members li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border: 1px solid var(--color-line);
  border-radius: 12px;
  background: #fff;
}
.community-detail__member-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}
.community-detail__member-role {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--color-accent-blue);
  font-size: 12px;
  font-weight: 800;
}
.community-detail__kick {
  padding: 4px 10px;
  border: 1px solid #e2b4b4;
  border-radius: 8px;
  background: #fff;
  color: #c0392b;
  font: inherit;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}
.community-detail__kick:disabled {
  color: var(--color-muted);
  cursor: not-allowed;
}
.community-detail__board {
  margin-top: 24px;
}
.community-detail__board-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.community-detail__board-heading h2 {
  font-size: 18px;
}
.community-detail__board-write {
  padding: 8px 16px;
  font-size: 13px;
}
.community-detail__composer {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: #fff;
}
.community-detail__composer-textarea {
  width: 100%;
  min-height: 72px;
  padding: 10px 12px;
  border: 1px solid var(--color-line);
  border-radius: 10px;
  font: inherit;
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-ink);
  /* 긴 글은 옆으로 늘어나지 않고 줄바꿈되며, 높이는 @input에서 내용에 맞춰 늘린다. */
  resize: none;
  overflow: hidden;
  overflow-wrap: break-word;
}
.community-detail__composer-count {
  margin-right: auto;
  align-self: center;
  font-size: 12px;
  color: var(--color-muted);
  font-variant-numeric: tabular-nums;
}
.community-detail__composer-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
}
.community-detail__composer-actions .community-detail__primary,
.community-detail__composer-actions .community-detail__ghost {
  padding: 8px 16px;
  font-size: 13px;
}
.community-detail__board-empty {
  padding: 24px 0;
  color: var(--color-muted);
  font-size: 13px;
  text-align: center;
}
.community-detail__board-list {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.community-detail__post {
  padding: 16px 18px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: #fff;
  box-shadow: var(--shadow-card);
}
.community-detail__post-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.community-detail__post-author {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--color-ink);
  font-size: 13px;
  font-weight: 800;
}
.community-detail__post-time {
  color: var(--color-muted);
  font-size: 12px;
}
.community-detail__post-content {
  margin: 10px 0 12px;
  color: var(--color-ink);
  font-size: 14px;
  line-height: 1.6;
  word-break: keep-all;
  /* 공백 없는 긴 문자열도 옆으로 넘치지 않고 줄바꿈되게 한다(페이지 가로 스크롤 방지). */
  overflow-wrap: anywhere;
}
.community-detail__comment-toggle {
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-accent-blue);
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}
.community-detail__comments {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--color-line);
}
.community-detail__comment-list {
  display: grid;
  gap: 8px;
  max-height: 240px;
  overflow-y: auto;
  overscroll-behavior: contain;
  margin: 0 0 10px;
  padding: 0;
  list-style: none;
}
.community-detail__comment-list li {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 10px;
  background: var(--color-surface-soft);
  font-size: 13px;
}
.community-detail__comment-author {
  color: var(--color-ink);
  font-weight: 800;
}
.community-detail__comment-content {
  flex: 1;
  min-width: 0;
  color: var(--color-ink);
  word-break: normal;
  overflow-wrap: anywhere;
}
.community-detail__comment-time {
  color: var(--color-muted);
  font-size: 12px;
}
.community-detail__comment-empty {
  margin: 0 0 10px;
  color: var(--color-muted);
  font-size: 13px;
}
.community-detail__comment-form {
  display: flex;
  gap: 8px;
}
.community-detail__comment-form input {
  flex: 1;
  min-width: 0;
  padding: 8px 12px;
  border: 1px solid var(--color-line);
  border-radius: 10px;
  font: inherit;
  font-size: 13px;
  color: var(--color-ink);
}
.community-detail__comment-form button {
  padding: 8px 14px;
  border: 0;
  border-radius: 10px;
  color: #fff;
  background: var(--color-accent-blue);
  font: inherit;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}
.community-detail__comment-form button:disabled {
  background: var(--color-muted);
  cursor: not-allowed;
}
.community-detail__comment-count {
  align-self: center;
  flex: 0 0 auto;
  font-size: 12px;
  color: var(--color-muted);
  font-variant-numeric: tabular-nums;
}
</style>
