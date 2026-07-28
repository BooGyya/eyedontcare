<script setup lang="ts">
import logoImage from '../../assets/images/brand/logo.png'
import { useToast } from '../../composables/useToast'

const { showToast } = useToast()

const footerLinks = [
  '이용약관',
  '개인정보처리방침',
  '커뮤니티 가이드',
  '고객센터',
]
</script>

<template>
  <footer class="app-footer">
    <RouterLink class="app-footer__brand" to="/" aria-label="eye dont care 홈">
      <img :src="logoImage" alt="eye dont care" />
    </RouterLink>
    <small>© 2026 eye dont care. All rights reserved.</small>
    <div class="app-footer__links">
      <button
        v-for="link in footerLinks"
        :key="link"
        type="button"
        @click="showToast(`${link}은 다음 단계에서 준비할 예정이에요.`)"
      >
        {{ link }}
      </button>
    </div>
    <div class="app-footer__social" aria-label="소셜 미디어">
      <button
        type="button"
        aria-label="유튜브"
        @click="showToast('소셜 링크는 아직 연결되지 않았어요.')"
      >
        ▶
      </button>
      <button
        type="button"
        aria-label="인스타그램"
        @click="showToast('소셜 링크는 아직 연결되지 않았어요.')"
      >
        ◎
      </button>
      <button
        type="button"
        aria-label="디스코드"
        @click="showToast('소셜 링크는 아직 연결되지 않았어요.')"
      >
        ♟
      </button>
      <button
        type="button"
        aria-label="엑스"
        @click="showToast('소셜 링크는 아직 연결되지 않았어요.')"
      >
        𝕏
      </button>
    </div>
  </footer>
</template>

<style scoped>
.app-footer {
  display: flex;
  align-items: center;
  gap: 28px;
  width: min(var(--content-width), calc(100% - 120px));
  min-height: 100px;
  margin: auto auto 0;
  padding: 16px 0 12px;
  border-top: 1px solid var(--color-line);
  color: #17345e;
  font-size: 12px;
}

.app-footer__brand img {
  width: 105px;
  height: 72px;
  object-fit: contain;
}

.app-footer small {
  margin-right: 52px;
}

.app-footer__links,
.app-footer__social {
  display: flex;
  gap: 20px;
}

.app-footer__links button,
.app-footer__social button {
  padding: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
}

.app-footer__social {
  gap: 12px;
  margin-left: auto;
}

.app-footer__social button {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  border: 1px solid var(--color-line);
  border-radius: 50%;
  box-shadow: 0 4px 11px rgba(34, 46, 85, 0.08);
  font-size: 17px;
}

@media (max-width: 1100px) {
  .app-footer {
    width: min(900px, calc(100% - 52px));
  }

  .app-footer small {
    margin-right: 0;
  }

  .app-footer__links {
    gap: 16px;
  }
}

@media (max-width: 640px) {
  .app-footer {
    flex-wrap: wrap;
    gap: 7px 14px;
    width: calc(100% - 32px);
    min-height: 80px;
    font-size: 10px;
  }

  .app-footer__brand img {
    width: 87px;
    height: 58px;
  }

  .app-footer small {
    width: 100%;
    order: 3;
  }

  .app-footer__links {
    display: none;
  }

  .app-footer__social button {
    width: 32px;
    height: 32px;
    font-size: 13px;
  }
}
</style>
