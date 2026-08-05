<script setup lang="ts">
import { computed } from 'vue'
import type { CommunityGroup } from '../../types/community'
import fallbackGroupImage from '../../assets/images/illustrations/illustration-teamwork.png'

const props = defineProps<{
  group: CommunityGroup
  isGuest?: boolean
}>()

// 대표 이미지 로드가 실패하면(네트워크/에셋 문제 등) 빈 카드가 되지 않도록 기본 이미지로 대체한다.
// 대체 이미지마저 실패해 무한 반복되는 것을 dataset 플래그로 막는다.
function handleImageError(event: globalThis.Event) {
  const image = event.target as globalThis.HTMLImageElement
  if (image.dataset.fallbackApplied) return
  image.dataset.fallbackApplied = 'true'
  image.src = fallbackGroupImage
}

const emit = defineEmits<{
  join: [group: CommunityGroup]
  enter: [group: CommunityGroup]
}>()

// 공개 소모임은 가입 여부와 무관하게 상세로 입장한다(가입은 상세 화면 우측 상단에서 한다).
// 비공개 소모임은 가입 전이면 코드 입력으로, 가입 후에는 상세 입장으로 보낸다.
const entersDetail = computed(
  () => props.group.isJoined || props.group.visibility === 'public',
)

const actionLabel = computed(() => {
  if (props.isGuest) return '로그인 후 이용'
  if (entersDetail.value) return '입장하기'
  return '코드로 가입'
})

function handleAction() {
  if (entersDetail.value) {
    emit('enter', props.group)
    return
  }

  emit('join', props.group)
}
</script>

<template>
  <article
    class="community-group-card"
    :data-testid="`community-group-${group.id}`"
  >
    <div class="community-group-card__image">
      <img
        :src="group.image || fallbackGroupImage"
        :alt="`${group.name} 대표 이미지`"
        loading="lazy"
        @error="handleImageError"
      />
      <span :class="`community-group-card__visibility--${group.visibility}`">
        {{ group.visibility === 'public' ? '공개' : '비공개' }}
      </span>
    </div>
    <div class="community-group-card__content">
      <div class="community-group-card__meta">
        <span>{{ group.members }} / {{ group.capacity }}명</span>
      </div>
      <h2>{{ group.name }}</h2>
      <p>{{ group.description }}</p>
      <footer>
        <span
          >리더 <b>{{ group.leader }}</b></span
        >
        <button
          :data-testid="`community-group-action-${group.id}`"
          type="button"
          @click="handleAction"
        >
          {{ actionLabel }}
        </button>
      </footer>
    </div>
  </article>
</template>

<style scoped>
.community-group-card {
  display: grid;
  overflow: hidden;
  min-width: 0;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: #fff;
  box-shadow: var(--shadow-card);
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.community-group-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-float);
}
.community-group-card__image {
  position: relative;
  height: 132px;
  overflow: hidden;
  background: var(--color-purple-soft);
}
.community-group-card__image img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.community-group-card__visibility--public,
.community-group-card__visibility--private {
  position: absolute;
  top: 14px;
  right: 14px;
  padding: 5px 9px;
  border-radius: var(--radius-button);
  font-size: 11px;
  font-weight: 800;
}
.community-group-card__visibility--public {
  color: #287c66;
  background: var(--color-mint-soft);
}
.community-group-card__visibility--private {
  color: #67509d;
  background: var(--color-purple-soft);
}
.community-group-card__content {
  display: grid;
  padding: 18px;
}
.community-group-card__meta {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  color: var(--color-accent-blue);
  font-size: 12px;
  font-weight: 800;
}
.community-group-card__meta span:last-child {
  color: var(--color-muted);
  font-weight: 700;
  white-space: nowrap;
}
.community-group-card h2 {
  margin: 7px 0 6px;
  overflow: hidden;
  font-size: 20px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.community-group-card p {
  display: -webkit-box;
  min-height: 42px;
  margin: 0 0 17px;
  overflow: hidden;
  color: var(--color-muted);
  font-size: 13px;
  line-height: 1.6;
  word-break: normal;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.community-group-card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--color-muted);
  font-size: 12px;
}
.community-group-card footer b {
  color: var(--color-ink);
}
.community-group-card button {
  padding: 9px 13px;
  border: 1px solid var(--color-accent-blue);
  border-radius: 10px;
  color: var(--color-accent-blue);
  background: #fff;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
  cursor: pointer;
}
.community-group-card button:hover:not(:disabled) {
  color: #fff;
  background: var(--color-accent-blue);
}
.community-group-card button:active:not(:disabled) {
  transform: translateY(1px);
}
.community-group-card button:focus-visible {
  outline: 3px solid rgba(79, 116, 219, 0.5);
  outline-offset: 2px;
}
.community-group-card button:disabled {
  border-color: var(--color-line);
  color: var(--color-muted);
  background: var(--color-surface-soft);
  cursor: not-allowed;
}

@media (max-width: 640px) {
  .community-group-card__image {
    height: 118px;
  }
}
</style>
