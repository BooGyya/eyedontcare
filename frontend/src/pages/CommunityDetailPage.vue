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

async function handleLeave() {
  if (!detail.value || isBusy.value) return
  isBusy.value = true
  try {
    await leaveGroup(groupId.value)
    showToast('소모임에서 나갔어요.')
    void router.push({ name: 'community' })
  } catch (error) {
    showToast(error instanceof ApiError ? error.message : '나가기에 실패했어요.')
  } finally {
    isBusy.value = false
  }
}

async function handleDelete() {
  if (!detail.value || isBusy.value) return
  // 삭제는 되돌릴 수 없으므로 한 번 더 확인한다.
  if (!globalThis.confirm('소모임을 삭제하면 되돌릴 수 없어요. 삭제할까요?')) return
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

onMounted(load)
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
      ← 목록으로
    </button>

    <div v-if="!auth.isAuthenticated" class="community-detail__status" role="status">
      <p>소모임 상세는 로그인 후 확인할 수 있어요.</p>
      <button type="button" class="community-detail__primary" @click="auth.openLogin">
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
          <span :class="`community-detail__visibility--${detail.visibility.toLowerCase()}`">
            {{ detail.visibility === 'PUBLIC' ? '공개' : '비공개' }}
          </span>
          <span>{{ detail.members }} / {{ detail.capacity }}명</span>
        </div>
        <p class="community-detail__description">{{ detail.description }}</p>
        <p class="community-detail__leader">
          리더 <b>{{ detail.leader }}</b>
        </p>
        <p v-if="detail.isJoined && detail.joinCode" class="community-detail__code">
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
            <span class="community-detail__member-name">{{ member.nickname }}</span>
            <span class="community-detail__member-meta">
              <span class="community-detail__member-role">{{ roleLabel(member.role) }}</span>
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
    </template>
  </section>
</template>

<style scoped>
.community-detail {
  padding: 32px 0 54px;
}
.community-detail__back {
  margin-bottom: 16px;
  padding: 6px 12px;
  border: 1px solid var(--color-line);
  border-radius: 10px;
  background: #fff;
  font: inherit;
  color: var(--color-ink);
  cursor: pointer;
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
.community-detail__description {
  margin: 14px 0;
  color: var(--color-ink);
  line-height: 1.6;
  word-break: keep-all;
}
.community-detail__leader,
.community-detail__code {
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
</style>
