import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AccountPage from '../pages/AccountPage.vue'
import CommunityPage from '../pages/CommunityPage.vue'
import CommunityDetailPage from '../pages/CommunityDetailPage.vue'
import GameDetailPage from '../pages/GameDetailPage.vue'
import GameReadyPage from '../pages/GameReadyPage.vue'
import GamePlayPage from '../pages/GamePlayPage.vue'
import GameResultPage from '../pages/GameResultPage.vue'
import GamesPage from '../pages/GamesPage.vue'
import HomePage from '../pages/HomePage.vue'
import KakaoCallbackPage from '../pages/KakaoCallbackPage.vue'
import ProfilePage from '../pages/ProfilePage.vue'
import RankingPage from '../pages/RankingPage.vue'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to, _from, savedPosition) {
    if (to.name === 'game-play' && to.params.gameId === 'air') return false

    return savedPosition ?? { top: 0 }
  },
  routes: [
    { path: '/', name: 'home', component: HomePage },
    { path: '/games', name: 'games', component: GamesPage },
    {
      path: '/games/:gameId(air|hold|draw|rhythm|blink)',
      name: 'game-detail',
      component: GameDetailPage,
    },
    {
      path: '/games/:gameId(air|hold|draw|rhythm|blink)/ready',
      name: 'game-ready',
      component: GameReadyPage,
    },
    {
      path: '/games/:gameId(air|hold|draw|rhythm|blink)/play',
      name: 'game-play',
      component: GamePlayPage,
    },
    {
      path: '/games/:gameId(air|hold|draw|rhythm|blink)/result',
      name: 'game-result',
      component: GameResultPage,
    },
    {
      path: '/auth/kakao/callback',
      name: 'kakao-callback',
      component: KakaoCallbackPage,
    },
    { path: '/ranking', name: 'ranking', component: RankingPage },
    { path: '/community', name: 'community', component: CommunityPage },
    {
      path: '/community/:groupId',
      name: 'community-detail',
      component: CommunityDetailPage,
    },
    {
      path: '/profile',
      name: 'profile',
      component: ProfilePage,
      meta: { requiresAuth: true },
    },
    {
      path: '/notifications',
      name: 'notifications',
      component: AccountPage,
      props: {
        title: '알림',
        description: '새로운 소식과 게임 관련 알림을 확인하세요.',
        items: ['내 알림', '랭킹 알림 설정'],
      },
    },
    {
      path: '/settings',
      name: 'settings',
      component: AccountPage,
      props: {
        title: '설정',
        description: '서비스 이용에 필요한 기본 설정을 관리하세요.',
        items: ['계정 설정', '알림 설정', '접근성 설정'],
      },
    },
  ],
})

// 인증 전용 페이지 가드. 새로고침 직후에는 세션 복원이 끝난 뒤 판정해 로그인
// 사용자를 잘못 내보내지 않는다. 비로그인 접근은 홈으로 보내고 로그인 창을 연다.
router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) return true
  const auth = useAuthStore()
  await auth.waitForSessionRestore()
  if (auth.isAuthenticated) return true
  auth.openLogin()
  return { name: 'home' }
})

export default router
