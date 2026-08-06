<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '../components/common/PageHeader.vue'
import { avatarForUserId } from '../api/ranking'
import { useToast } from '../composables/useToast'
import { useAuthStore } from '../stores/auth'
import { ApiError } from '../api/http'
import {
  getGroup,
  joinGroupById,
  leaveGroup,
  deleteGroup,
  kickMember,
  getGroupPosts,
  createGroupPost,
  updateGroupPost,
  deleteGroupPost,
  createGroupComment,
  updateGroupComment,
  deleteGroupComment,
  toCommunityComment,
  toCommunityPost,
  toTimeLabel,
  type GroupDetailResponse,
} from '../api/group'
import type { CommunityComment, CommunityPost } from '../types/community'
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
      error instanceof ApiError ? error.message : '길드를 불러오지 못했어요.'
  } finally {
    isLoading.value = false
  }
}

// --- Talk 게시판 (백엔드 저장) ---
const posts = ref<CommunityPost[]>([])
const openPostIds = ref<Record<string, boolean>>({})
const commentDrafts = ref<Record<string, string>>({})
const isComposerOpen = ref(false)
const composerContent = ref('')

async function loadPosts() {
  if (!auth.isAuthenticated) return
  try {
    const response = await getGroupPosts(groupId.value)
    posts.value = response.posts.map((post) =>
      toCommunityPost(post, groupId.value),
    )
  } catch {
    // 목록 로드 실패는 화면을 막지 않는다(빈 게시판으로 표시).
    posts.value = []
  }
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

async function submitPost() {
  const content = composerContent.value.trim()
  if (!content) {
    showToast('내용을 입력해 주세요.')
    return
  }
  // maxlength로 입력은 막히지만, 붙여넣기/우회 입력 대비로 제출 시에도 한 번 더 막는다.
  if (content.length > POST_MAX_LENGTH) {
    showToast(`글은 ${POST_MAX_LENGTH}자까지 입력할 수 있어요.`)
    return
  }
  try {
    const saved = await createGroupPost(groupId.value, content)
    posts.value.unshift(toCommunityPost(saved, groupId.value))
    composerContent.value = ''
    isComposerOpen.value = false
    showToast('글을 남겼어요!')
  } catch (error) {
    showToast(
      error instanceof ApiError ? error.message : '글 저장에 실패했어요.',
    )
  }
}

// --- 글 수정/삭제 (본인 글만) ---
const editingPostId = ref<string | null>(null)
const postEditDrafts = ref<Record<string, string>>({})

function startPostEdit(post: CommunityPost) {
  editingPostId.value = post.id
  postEditDrafts.value[post.id] = post.content
}

function cancelPostEdit() {
  editingPostId.value = null
}

async function savePostEdit(post: CommunityPost) {
  const content = (postEditDrafts.value[post.id] ?? '').trim()
  if (!content) {
    showToast('내용을 입력해 주세요.')
    return
  }
  try {
    const saved = await updateGroupPost(groupId.value, post.id, content)
    post.content = saved.content
    post.timeLabel = toTimeLabel(saved.createdAt)
    editingPostId.value = null
    showToast('글을 수정했어요.')
  } catch (error) {
    showToast(
      error instanceof ApiError ? error.message : '글 수정에 실패했어요.',
    )
  }
}

async function handleDeletePost(post: CommunityPost) {
  if (!globalThis.confirm('글을 삭제할까요? 댓글도 함께 삭제돼요.')) return
  try {
    await deleteGroupPost(groupId.value, post.id)
    posts.value = posts.value.filter((item) => item.id !== post.id)
    showToast('글을 삭제했어요.')
  } catch (error) {
    showToast(
      error instanceof ApiError ? error.message : '글 삭제에 실패했어요.',
    )
  }
}

// 마지막으로 댓글을 작성한 시각. 짧은 간격의 연속 작성(난사)을 막는 쿨다운 기준이다.
let lastCommentAt = 0

async function submitComment(post: CommunityPost) {
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
  try {
    const saved = await createGroupComment(groupId.value, post.id, content)
    post.comments.push(toCommunityComment(saved))
    commentDrafts.value[post.id] = ''
    lastCommentAt = now
  } catch (error) {
    showToast(
      error instanceof ApiError ? error.message : '댓글 저장에 실패했어요.',
    )
  }
}

// --- 댓글 수정/삭제 (본인 댓글만) ---
const editingCommentId = ref<string | null>(null)
const editDrafts = ref<Record<string, string>>({})

function findComment(post: CommunityPost, commentId: string) {
  return post.comments.find((comment) => comment.id === commentId)
}

function startCommentEdit(comment: CommunityComment) {
  editingCommentId.value = comment.id
  editDrafts.value[comment.id] = comment.content
}

function cancelCommentEdit() {
  editingCommentId.value = null
}

async function saveCommentEdit(post: CommunityPost) {
  const commentId = editingCommentId.value
  if (!commentId) return
  const content = (editDrafts.value[commentId] ?? '').trim()
  if (!content) {
    showToast('내용을 입력해 주세요.')
    return
  }
  try {
    const saved = await updateGroupComment(
      groupId.value,
      post.id,
      commentId,
      content,
    )
    const target = findComment(post, commentId)
    if (target) {
      target.content = saved.content
      target.timeLabel = toTimeLabel(saved.createdAt)
    }
    editingCommentId.value = null
    showToast('댓글을 수정했어요.')
  } catch (error) {
    showToast(
      error instanceof ApiError ? error.message : '댓글 수정에 실패했어요.',
    )
  }
}

async function handleDeleteComment(post: CommunityPost, commentId: string) {
  if (!globalThis.confirm('댓글을 삭제할까요?')) return
  try {
    await deleteGroupComment(groupId.value, post.id, commentId)
    post.comments = post.comments.filter((comment) => comment.id !== commentId)
    showToast('댓글을 삭제했어요.')
  } catch (error) {
    showToast(
      error instanceof ApiError ? error.message : '댓글 삭제에 실패했어요.',
    )
  }
}

async function handleLeave() {
  if (!detail.value || isBusy.value) return
  isBusy.value = true
  try {
    await leaveGroup(groupId.value)
    showToast('길드에서 나갔어요.')
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
  if (!globalThis.confirm('길드를 삭제하면 되돌릴 수 없어요. 삭제할까요?'))
    return
  isBusy.value = true
  try {
    await deleteGroup(groupId.value)
    showToast('길드를 삭제했어요.')
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
    showToast('길드원을 강퇴했어요.')
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
  void loadPosts()
})
watch(
  () => auth.isAuthenticated,
  (authenticated) => {
    if (authenticated) {
      void load()
      void loadPosts()
    }
  },
)
</script>

<template>
  <section class="community-detail">
    <PageHeader
      eyebrow="COMMUNITY"
      :title="detail?.name ?? '길드'"
      description="길드 정보와 길드원을 확인하세요."
    />

    <div class="community-detail__toolbar">
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
        길드 목록으로
      </button>

      <div v-if="detail" class="community-detail__top-actions">
        <button
          v-if="detail.isOwner"
          type="button"
          class="community-detail__top-action-quiet"
          :disabled="isBusy"
          @click="handleDelete"
        >
          길드 삭제
        </button>
        <button
          v-else-if="detail.isJoined"
          type="button"
          class="community-detail__top-action-quiet"
          :disabled="isBusy"
          @click="handleLeave"
        >
          길드 나가기
        </button>
        <button
          v-else-if="detail.visibility === 'PUBLIC'"
          type="button"
          class="community-detail__primary community-detail__top-action"
          :disabled="isBusy || detail.members >= detail.capacity"
          @click="handleJoin"
        >
          {{ detail.members >= detail.capacity ? '정원 마감' : '가입하기' }}
        </button>
      </div>
    </div>

    <div
      v-if="!auth.isAuthenticated"
      class="community-detail__status"
      role="status"
    >
      <p>길드 상세는 로그인 후 확인할 수 있어요.</p>
      <button
        type="button"
        class="community-detail__primary"
        @click="auth.openLogin"
      >
        로그인하기
      </button>
    </div>

    <p v-else-if="isLoading" class="community-detail__status" role="status">
      길드를 불러오는 중이에요…
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

        <p
          v-if="!detail.isJoined && detail.visibility !== 'PUBLIC'"
          class="community-detail__hint community-detail__hint--card"
        >
          비공개 길드는 참여 코드로 입장할 수 있어요.
        </p>
      </article>

      <section class="community-detail__members" aria-label="길드원 명단">
        <h2>길드원 {{ detail.memberList.length }}명</h2>
        <ul class="community-detail__member-grid">
          <li
            v-for="member in detail.memberList"
            :key="member.userId"
            class="community-detail__member-card"
          >
            <button
              v-if="detail.isOwner && member.role !== 'OWNER'"
              type="button"
              class="community-detail__member-kick"
              aria-label="강퇴"
              :disabled="isBusy"
              @click="handleKick(member.userId, member.nickname)"
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M6 6l12 12M18 6 6 18"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2.4"
                  stroke-linecap="round"
                />
              </svg>
            </button>
            <svg
              v-if="member.role === 'OWNER'"
              class="community-detail__member-crown"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                d="M4 8l3 3 5-6 5 6 3-3-1.5 10h-13L4 8Z"
                fill="var(--color-gold)"
              />
            </svg>
            <span class="community-detail__member-avatar">
              <img
                :src="avatarForUserId(member.userId)"
                :alt="`${member.nickname} 프로필`"
              />
            </span>
            <span class="community-detail__member-name">
              <span class="community-detail__member-name-text">{{
                member.nickname
              }}</span>
            </span>
          </li>
        </ul>
      </section>

      <section class="community-detail__board" aria-label="Talk">
        <div class="community-detail__board-heading">
          <div class="community-detail__board-heading-text">
            <h2>Talk</h2>
            <p class="community-detail__board-sub">
              길드원들과 자유롭게 이야기해요. 게임 후기도, 같이 할 사람 구하기도
              좋아요!
            </p>
          </div>
          <button
            v-if="detail.isJoined || detail.isOwner"
            type="button"
            class="community-detail__primary community-detail__board-write"
            @click="toggleComposer"
          >
            글쓰기
          </button>
          <p v-else class="community-detail__board-hint">
            길드에 가입하면 이야기를 남길 수 있어요.
          </p>
        </div>

        <form
          v-if="isComposerOpen"
          class="community-detail__composer"
          @submit.prevent="submitPost"
        >
          <textarea
            v-model="composerContent"
            class="community-detail__composer-textarea"
            placeholder="길드원들에게 하고 싶은 이야기를 남겨보세요"
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
          아직 이야기가 없어요. 첫 글을 남겨보세요!
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
              <span class="community-detail__post-header-right">
                <span class="community-detail__post-time">{{
                  post.timeLabel
                }}</span>
                <span
                  v-if="post.mine && editingPostId !== post.id"
                  class="community-detail__post-owner-actions"
                >
                  <button
                    type="button"
                    class="community-detail__post-edit"
                    @click="startPostEdit(post)"
                  >
                    수정
                  </button>
                  <button
                    type="button"
                    class="community-detail__post-delete"
                    @click="handleDeletePost(post)"
                  >
                    삭제
                  </button>
                </span>
              </span>
            </div>
            <template v-if="editingPostId === post.id">
              <textarea
                v-model="postEditDrafts[post.id]"
                class="community-detail__post-edit-textarea"
                :maxlength="POST_MAX_LENGTH"
                @input="autoGrowComposer"
              />
              <div class="community-detail__post-edit-actions">
                <span class="community-detail__composer-count">
                  {{ (postEditDrafts[post.id] ?? '').length }}/{{
                    POST_MAX_LENGTH
                  }}
                </span>
                <button
                  type="button"
                  class="community-detail__ghost"
                  @click="cancelPostEdit"
                >
                  취소
                </button>
                <button
                  type="button"
                  class="community-detail__primary"
                  @click="savePostEdit(post)"
                >
                  저장
                </button>
              </div>
            </template>
            <p v-else class="community-detail__post-content">
              {{ post.content }}
            </p>
            <button
              type="button"
              class="community-detail__comment-toggle"
              :aria-expanded="!!openPostIds[post.id]"
              @click="toggleComments(post.id)"
            >
              댓글 {{ post.comments.length }}
              <svg
                class="community-detail__comment-toggle-icon"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path
                  v-if="openPostIds[post.id]"
                  d="M6 15l6-6 6 6"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <path
                  v-else
                  d="M6 9l6 6 6-6"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </button>

            <div v-if="openPostIds[post.id]" class="community-detail__comments">
              <ul
                v-if="post.comments.length"
                class="community-detail__comment-list"
              >
                <li v-for="comment in post.comments" :key="comment.id">
                  <template v-if="editingCommentId === comment.id">
                    <input
                      v-model="editDrafts[comment.id]"
                      type="text"
                      class="community-detail__comment-edit-input"
                      :maxlength="COMMENT_MAX_LENGTH"
                      @keyup.enter="saveCommentEdit(post)"
                    />
                    <span class="community-detail__comment-owner-actions">
                      <button
                        type="button"
                        class="community-detail__comment-save"
                        @click="saveCommentEdit(post)"
                      >
                        저장
                      </button>
                      <button
                        type="button"
                        class="community-detail__comment-cancel"
                        @click="cancelCommentEdit"
                      >
                        취소
                      </button>
                    </span>
                  </template>
                  <template v-else>
                    <span class="community-detail__comment-author">{{
                      comment.author
                    }}</span>
                    <span class="community-detail__comment-content">{{
                      comment.content
                    }}</span>
                    <span class="community-detail__comment-time">{{
                      comment.timeLabel
                    }}</span>
                    <span
                      v-if="comment.mine"
                      class="community-detail__comment-owner-actions"
                    >
                      <button
                        type="button"
                        class="community-detail__comment-edit"
                        @click="startCommentEdit(comment)"
                      >
                        수정
                      </button>
                      <button
                        type="button"
                        class="community-detail__comment-delete"
                        @click="handleDeleteComment(post, comment.id)"
                      >
                        삭제
                      </button>
                    </span>
                  </template>
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
.community-detail__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.community-detail__top-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}
.community-detail__top-action {
  padding: 8px 16px;
  font-size: 13px;
}
.community-detail__top-action-quiet {
  padding: 6px 12px;
  border: 1px solid #e2b4b4;
  border-radius: 8px;
  color: #c0392b;
  background: #fff;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}
.community-detail__top-action-quiet:disabled {
  color: var(--color-muted);
  cursor: not-allowed;
}
.community-detail__back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
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
  /* 공백 없는 긴 소개글도 옆으로 넘치지 않고 아래로 줄바꿈되게 한다. */
  overflow-wrap: anywhere;
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
.community-detail__hint--card {
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
.community-detail__member-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.community-detail__member-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 10px 6px;
  border: 1px solid var(--color-line);
  border-radius: 12px;
  background: #fff;
}
.community-detail__member-avatar {
  display: block;
  width: 44px;
  height: 44px;
}
.community-detail__member-avatar img {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--color-surface-soft);
}
.community-detail__member-name {
  display: flex;
  align-items: center;
  gap: 3px;
  max-width: 100%;
  color: var(--color-ink);
  font-size: 12px;
  font-weight: 700;
}
.community-detail__member-name-text {
  overflow: hidden;
  min-width: 0;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.community-detail__member-crown {
  position: absolute;
  top: 8px;
  left: 8px;
  width: 18px;
  height: 18px;
}
.community-detail__member-kick {
  position: absolute;
  top: 4px;
  right: 4px;
  display: grid;
  width: 18px;
  height: 18px;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 50%;
  color: var(--color-muted);
  background: var(--color-surface-soft);
  cursor: pointer;
  transition:
    color var(--duration-fast) ease,
    background-color var(--duration-fast) ease;
}
.community-detail__member-kick svg {
  width: 10px;
  height: 10px;
  fill: none;
  stroke: currentColor;
}
.community-detail__member-kick:hover {
  color: #c0392b;
  background: #fce9e9;
}
.community-detail__member-kick:disabled {
  color: var(--color-muted);
  cursor: not-allowed;
}
.community-detail__board {
  margin-top: 24px;
}
.community-detail__board-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.community-detail__board-heading h2 {
  font-family: var(--font-display);
  font-size: 20px;
}
.community-detail__board-sub {
  margin: 4px 0 0;
  color: var(--color-muted);
  font-size: 13px;
  line-height: 1.6;
  word-break: keep-all;
}
.community-detail__board-write {
  padding: 8px 16px;
  font-size: 13px;
}
.community-detail__board-hint {
  flex-shrink: 0;
  margin: 6px 0 0;
  color: var(--color-muted);
  font-size: 13px;
  text-align: right;
  white-space: nowrap;
}
@media (max-width: 480px) {
  .community-detail__board-heading {
    flex-wrap: wrap;
  }
  .community-detail__board-hint {
    text-align: left;
    white-space: normal;
  }
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
  display: flex;
  flex-direction: column;
  padding: 18px;
  border: 1px solid var(--color-line);
  border-radius: 16px;
  background: #fff;
  box-shadow: var(--shadow-card);
  transition:
    transform var(--duration-fast) ease,
    box-shadow var(--duration-fast) ease;
}
.community-detail__post:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-float);
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
.community-detail__post-header-right {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 8px;
}
.community-detail__post-time {
  color: var(--color-muted);
  font-size: 12px;
}
.community-detail__post-owner-actions {
  display: inline-flex;
  flex-shrink: 0;
  gap: 8px;
}
.community-detail__post-edit,
.community-detail__post-delete {
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}
.community-detail__post-edit {
  color: var(--color-accent-blue);
}
.community-detail__post-delete {
  color: #c0392b;
}
.community-detail__post-edit-textarea {
  width: 100%;
  min-height: 72px;
  margin: 10px 0 12px;
  padding: 10px 12px;
  border: 1px solid var(--color-accent-blue);
  border-radius: 10px;
  font: inherit;
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-ink);
  resize: none;
  overflow: hidden;
  overflow-wrap: break-word;
}
.community-detail__post-edit-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 12px;
}
.community-detail__post-edit-actions .community-detail__primary,
.community-detail__post-edit-actions .community-detail__ghost {
  padding: 6px 14px;
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
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-accent-blue);
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}
.community-detail__comment-toggle-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
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
.community-detail__comment-owner-actions {
  display: inline-flex;
  flex-shrink: 0;
  gap: 8px;
}
.community-detail__comment-edit,
.community-detail__comment-delete,
.community-detail__comment-save,
.community-detail__comment-cancel {
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}
.community-detail__comment-edit,
.community-detail__comment-save {
  color: var(--color-accent-blue);
}
.community-detail__comment-delete {
  color: #c0392b;
}
.community-detail__comment-cancel {
  color: var(--color-muted);
}
.community-detail__comment-edit-input {
  flex: 1;
  min-width: 0;
  padding: 6px 10px;
  border: 1px solid var(--color-accent-blue);
  border-radius: 8px;
  font: inherit;
  font-size: 13px;
  color: var(--color-ink);
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
