<script setup lang="ts">
import PageHeader from '../components/common/PageHeader.vue'
import CommunityGroupCard from '../components/groups/CommunityGroupCard.vue'
import { useToast } from '../composables/useToast'
import { communityGroups } from '../mocks/pages'
import type { CommunityGroup } from '../types/pages'

const { showToast } = useToast()

function handleEnterGroup(group: CommunityGroup) {
  showToast(`${group.name} 입장 기능은 다음 구현 단계에서 제공될 예정이에요.`)
}

function handleCreateGroup() {
  showToast('소모임 생성 기능은 준비 중이에요.')
}
</script>

<template>
  <section class="community-page">
    <div class="community-page__heading">
      <PageHeader
        title="소모임"
        description="친구들과 함께 기록을 나누고 게임을 즐겨 보세요."
      />
      <button type="button" @click="handleCreateGroup">+ 소모임 만들기</button>
    </div>
    <div class="community-page__list">
      <CommunityGroupCard
        v-for="group in communityGroups"
        :key="group.id"
        :group="group"
        @enter="handleEnterGroup"
      />
    </div>
  </section>
</template>

<style scoped>
.community-page {
  padding: 32px 0 54px;
}

.community-page__heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 20px;
}

.community-page__heading .page-header {
  margin-bottom: 28px;
}

.community-page__heading > button {
  margin-bottom: 28px;
  padding: 12px 17px;
  border-radius: 10px;
  color: #fff;
  background: var(--color-accent-blue);
  font-weight: 800;
  white-space: nowrap;
  cursor: pointer;
}

.community-page__list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

@media (max-width: 850px) {
  .community-page__list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .community-page {
    padding-top: 24px;
  }

  .community-page__heading {
    align-items: start;
  }

  .community-page__heading > button {
    margin-top: 10px;
    margin-bottom: 0;
    padding: 10px 12px;
    font-size: 12px;
  }
}
</style>
