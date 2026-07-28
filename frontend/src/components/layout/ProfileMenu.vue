<script setup lang="ts">
import { ref } from 'vue'
import { profileData, profileState } from '../../mocks/profile'

const isOpen = ref(false)

function closeMenu() {
  isOpen.value = false
}
</script>

<template>
  <div class="profile-menu">
    <button
      class="profile-menu__button"
      type="button"
      aria-label="프로필 메뉴"
      :aria-expanded="isOpen"
      @click="isOpen = !isOpen"
    >
      <img class="profile-menu__avatar" :src="profileState.avatar" alt="" />
      <span class="profile-menu__meta">
        <strong>{{ profileState.nickname }}</strong
        ><small>Lv. {{ profileData.level }}</small>
      </span>
    </button>
    <section
      v-if="isOpen"
      class="profile-menu__panel"
      aria-label="프로필 미리보기"
    >
      <div class="profile-menu__heading">
        <img
          :src="profileState.avatar"
          :alt="`${profileState.nickname} 프로필`"
        />
        <div>
          <strong>{{ profileState.nickname }}</strong
          ><span>Lv. {{ profileData.level }} · 눈 건강 챌린저</span>
        </div>
      </div>
      <div class="profile-menu__stats">
        <span
          ><b>{{ profileData.weeklyScore }}</b
          >이번 주 점수</span
        >
        <span
          ><b>{{ profileData.stats[1]?.value }}</b
          >완료한 라운드</span
        >
        <span
          ><b>{{ profileData.stats[2]?.value }}</b
          >친구 중 순위</span
        >
      </div>
      <RouterLink to="/profile" @click="closeMenu"
        >마이페이지 <span>→</span></RouterLink
      >
      <RouterLink to="/settings" @click="closeMenu"
        >설정 <span>→</span></RouterLink
      >
    </section>
  </div>
</template>

<style scoped>
.profile-menu {
  position: relative;
}
.profile-menu__button {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 0;
  color: var(--color-ink);
  background: transparent;
  cursor: pointer;
}
.profile-menu__avatar {
  width: 48px;
  height: 48px;
  border: 2px solid #fff;
  border-radius: 50%;
  object-fit: cover;
  background: var(--color-blue-soft);
  box-shadow: 0 2px 7px rgba(26, 35, 78, 0.12);
}
.profile-menu__meta {
  display: grid;
  gap: 1px;
  text-align: left;
  font-size: 14px;
}
.profile-menu__meta small,
.profile-menu__heading span,
.profile-menu__stats {
  color: var(--color-muted);
  font-size: 11px;
}
.profile-menu__panel {
  position: absolute;
  top: calc(100% + 12px);
  right: 0;
  display: grid;
  width: 290px;
  gap: 12px;
  padding: 18px;
  border: 1px solid var(--color-line);
  border-radius: 18px;
  background: #fff;
  box-shadow: var(--shadow-float);
}
.profile-menu__heading {
  display: flex;
  align-items: center;
  gap: 10px;
}
.profile-menu__heading img {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--color-blue-soft);
}
.profile-menu__heading div,
.profile-menu__stats span {
  display: grid;
  gap: 2px;
}
.profile-menu__stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 12px 0;
  border-top: 1px solid var(--color-line);
  border-bottom: 1px solid var(--color-line);
  text-align: center;
}
.profile-menu__stats b {
  color: var(--color-ink);
  font-size: 13px;
}
.profile-menu__panel a {
  display: flex;
  justify-content: space-between;
  color: var(--color-ink);
  font-weight: 700;
}
.profile-menu__panel a span {
  color: var(--color-accent-blue);
}
@media (max-width: 640px) {
  .profile-menu__avatar {
    width: 31px;
    height: 31px;
  }
  .profile-menu__meta {
    display: none;
  }
}
</style>
