<script setup lang="ts">
import comingSoonImage from '../../assets/images/games/game-plus-transparent.png'
</script>

<template>
  <article class="coming-soon-card">
    <div class="coming-soon-card__image">
      <img :src="comingSoonImage" alt="" />
      <span class="coming-soon-card__pill">COMING SOON</span>
    </div>
    <div class="coming-soon-card__content">
      <span>NEXT GAME</span>
      <h2>새로운 게임 준비 중!</h2>
      <p>새로운 눈 운동 게임을 열심히 만들고 있어요. 조금만 기대해 주세요!</p>
      <div
        class="coming-soon-card__loading"
        role="status"
        aria-label="새 게임 준비 중"
      >
        <i></i><i></i><i></i>
        <b>곧 만나요</b>
      </div>
    </div>
  </article>
</template>

<style scoped>
.coming-soon-card {
  overflow: hidden;
  border: 1.5px dashed #c2c8ee;
  border-radius: var(--radius-card);
  background: #fbfbff;
}

.coming-soon-card__image {
  position: relative;
  height: 156px;
  background: var(--color-purple-soft);
}

.coming-soon-card__image img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  padding: 10px 0;
}

.coming-soon-card__pill {
  position: absolute;
  top: 13px;
  right: 13px;
  padding: 5px 9px;
  border-radius: var(--radius-button);
  color: #6b7cf5;
  background: #e7e9fc;
  font-size: 11px;
  font-weight: 800;
}

.coming-soon-card__content {
  padding: 17px;
}

.coming-soon-card__content > span {
  color: #6b7cf5;
  font-size: 12px;
  font-weight: 800;
}

.coming-soon-card h2 {
  margin: 4px 0 7px;
  font-size: 20px;
}

.coming-soon-card p {
  min-height: 44px;
  margin: 0 0 16px;
  color: var(--color-muted);
  font-size: 13px;
  line-height: 1.6;
  word-break: keep-all;
}

.coming-soon-card__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 10px;
  border: 1px dashed #c2c8ee;
  border-radius: 10px;
  color: #6b7cf5;
}
.coming-soon-card__loading b {
  margin-left: 4px;
  font-size: 14px;
  font-weight: 800;
}
.coming-soon-card__loading i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #8f9bf0;
  animation: coming-soon-bounce 1.2s ease-in-out infinite;
}
.coming-soon-card__loading i:nth-child(2) {
  animation-delay: 0.15s;
}
.coming-soon-card__loading i:nth-child(3) {
  animation-delay: 0.3s;
}
@keyframes coming-soon-bounce {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.55;
  }
  30% {
    transform: translateY(-6px);
    opacity: 1;
  }
}
</style>
