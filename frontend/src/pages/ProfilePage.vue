<script setup lang="ts">
import { computed, ref } from 'vue'
import { useToast } from '../composables/useToast'
import { profileData } from '../mocks/profile'

const { showToast } = useToast()
const selectedAvatarId = ref(profileData.avatars[0]?.id ?? '')

const selectedAvatar = computed(
  () =>
    profileData.avatars.find(
      (avatar) => avatar.id === selectedAvatarId.value,
    ) ?? profileData.avatars[0],
)

function handleAvatarSelect(avatarId: string) {
  selectedAvatarId.value = avatarId
}

function handleShowAllActivities() {
  showToast('전체 활동 기록은 준비 중이에요.')
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
        <h1>
          {{ profileData.nickname }} <em>Lv. {{ profileData.level }}</em>
        </h1>
        <p>꾸준히 눈을 쉬게 해준 지 {{ profileData.journeyDays }}일째예요.</p>
      </div>
      <div class="profile-page__score">
        <span>이번 주 점수</span>
        <strong>{{ profileData.weeklyScore }}</strong>
        <small
          >지난주보다 <b>{{ profileData.weeklyChange }}</b></small
        >
      </div>
    </section>

    <section
      class="profile-page__customizer"
      aria-labelledby="profile-style-title"
    >
      <header>
        <div>
          <span>PROFILE STYLE</span>
          <h2 id="profile-style-title">프로필 이미지</h2>
        </div>
        <p>나를 표현하는 캐릭터를 골라보세요.</p>
      </header>
      <div
        class="profile-page__avatar-picker"
        role="radiogroup"
        aria-label="프로필 이미지 선택"
      >
        <button
          v-for="avatar in profileData.avatars"
          :key="avatar.id"
          :aria-checked="avatar.id === selectedAvatarId"
          :class="{
            'profile-page__avatar-option--selected':
              avatar.id === selectedAvatarId,
          }"
          role="radio"
          type="button"
          @click="handleAvatarSelect(avatar.id)"
        >
          <img :src="avatar.image" :alt="`${avatar.name} 프로필 이미지`" />
          <span>{{ avatar.name }}</span>
        </button>
      </div>
    </section>

    <section class="profile-page__stats" aria-label="내 활동 통계">
      <article v-for="stat in profileData.stats" :key="stat.label">
        <span>{{ stat.label }}</span>
        <strong>{{ stat.value }}</strong>
        <small>{{ stat.caption }}</small>
      </article>
    </section>

    <section
      class="profile-page__activities"
      aria-labelledby="recent-activity-title"
    >
      <header>
        <div>
          <span>RECENT ACTIVITY</span>
          <h2 id="recent-activity-title">최근 활동</h2>
        </div>
        <button type="button" @click="handleShowAllActivities">
          전체 보기 →
        </button>
      </header>
      <ul v-if="profileData.activities.length">
        <li v-for="activity in profileData.activities" :key="activity.id">
          <span
            :class="`profile-page__activity-icon--${activity.tone}`"
            aria-hidden="true"
          >
            {{ activity.icon }}
          </span>
          <p>
            <b>{{ activity.title }}</b
            >{{ activity.description }}<small>{{ activity.time }}</small>
          </p>
          <strong>{{ activity.score }}</strong>
        </li>
      </ul>
      <p v-else class="profile-page__empty">최근 활동이 아직 없어요.</p>
    </section>
  </section>
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
.profile-page__customizer header > div > span,
.profile-page__activities header > div > span {
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
.profile-page__identity h1 em {
  display: inline-block;
  padding: 5px 10px;
  border-radius: var(--radius-button);
  color: #4568bf;
  background: var(--color-blue-soft);
  font-size: 14px;
  font-style: normal;
  letter-spacing: 0;
  vertical-align: middle;
}
.profile-page__identity p {
  margin: 0;
  color: var(--color-muted);
  font-size: 15px;
  word-break: keep-all;
}
.profile-page__score {
  display: grid;
  min-width: 165px;
  gap: 4px;
  padding: 17px 21px;
  border: 1px solid rgba(79, 116, 219, 0.17);
  border-radius: 16px;
  background: #fff;
}
.profile-page__score span,
.profile-page__score small {
  color: var(--color-muted);
  font-size: 12px;
}
.profile-page__score strong {
  color: var(--color-ink);
  font-size: 29px;
  line-height: 1.1;
}
.profile-page__score b {
  color: var(--color-accent-mint);
}
.profile-page__customizer,
.profile-page__activities {
  margin-top: 21px;
  padding: clamp(21px, 3vw, 30px);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: #fff;
  box-shadow: var(--shadow-card);
}
.profile-page__customizer header,
.profile-page__activities header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 20px;
}
.profile-page__customizer h2,
.profile-page__activities h2 {
  margin: 5px 0 0;
  font-size: 23px;
  letter-spacing: -0.04em;
}
.profile-page__customizer header p {
  margin: 0;
  color: var(--color-muted);
  font-size: 13px;
}
.profile-page__avatar-picker {
  display: flex;
  gap: 13px;
  overflow-x: auto;
  margin-top: 23px;
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
.profile-page__avatar-picker button:hover {
  color: var(--color-accent-blue);
  background: var(--color-surface-soft);
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
.profile-page__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 15px;
  margin-top: 21px;
}
.profile-page__stats article {
  display: grid;
  gap: 5px;
  padding: 22px;
  border: 1px solid var(--color-line);
  border-radius: 17px;
  background: #fff;
  box-shadow: var(--shadow-card);
}
.profile-page__stats span,
.profile-page__stats small {
  color: var(--color-muted);
  font-size: 13px;
}
.profile-page__stats strong {
  color: var(--color-ink);
  font-size: 27px;
}
.profile-page__activities header button {
  padding: 8px 0;
  color: var(--color-accent-blue);
  background: transparent;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}
.profile-page__activities ul {
  display: grid;
  margin: 22px 0 0;
  padding: 0;
  list-style: none;
}
.profile-page__activities li {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 13px;
  align-items: center;
  padding: 15px 0;
  border-top: 1px solid var(--color-line);
}
.profile-page__activities li:first-child {
  border-top: 0;
}
.profile-page__activity-icon--mint,
.profile-page__activity-icon--purple,
.profile-page__activity-icon--yellow {
  display: grid;
  width: 37px;
  height: 37px;
  place-items: center;
  border-radius: 12px;
  font-weight: 800;
}
.profile-page__activity-icon--mint {
  color: #2f9275;
  background: var(--color-mint-soft);
}
.profile-page__activity-icon--purple {
  color: #7255a9;
  background: var(--color-purple-soft);
}
.profile-page__activity-icon--yellow {
  color: #a97815;
  background: var(--color-yellow-soft);
}
.profile-page__activities p {
  min-width: 0;
  margin: 0;
  color: var(--color-muted);
  font-size: 14px;
}
.profile-page__activities p b {
  color: var(--color-ink);
}
.profile-page__activities p small {
  display: block;
  margin-top: 4px;
  color: var(--color-muted);
  font-size: 11px;
}
.profile-page__activities li > strong {
  color: var(--color-accent-mint);
  font-size: 14px;
}
.profile-page__empty {
  margin: 22px 0 0;
  padding: 28px;
  border-radius: 12px;
  color: var(--color-muted);
  background: var(--color-surface-soft);
  text-align: center;
}

@media (max-width: 760px) {
  .profile-page__hero {
    grid-template-columns: auto minmax(0, 1fr);
  }
  .profile-page__score {
    grid-column: 1 / -1;
    grid-template-columns: auto auto 1fr;
    align-items: center;
  }
  .profile-page__score strong {
    font-size: 25px;
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
  .profile-page__score {
    grid-column: auto;
    grid-template-columns: 1fr;
    text-align: left;
  }
  .profile-page__customizer header,
  .profile-page__activities header {
    align-items: start;
    flex-direction: column;
    gap: 8px;
  }
  .profile-page__stats {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .profile-page__stats article {
    grid-template-columns: 1fr auto;
    align-items: center;
    gap: 2px;
  }
  .profile-page__stats small {
    grid-column: 1 / -1;
  }
  .profile-page__activities li {
    gap: 10px;
  }
}
</style>
