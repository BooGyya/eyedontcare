import { createRouter, createWebHistory } from 'vue-router'
import AccountPage from '../pages/AccountPage.vue'
import CommunityPage from '../pages/CommunityPage.vue'
import GameDetailPage from '../pages/GameDetailPage.vue'
import GameReadyPage from '../pages/GameReadyPage.vue'
import GamePlayPage from '../pages/GamePlayPage.vue'
import GameResultPage from '../pages/GameResultPage.vue'
import GamesPage from '../pages/GamesPage.vue'
import HomePage from '../pages/HomePage.vue'
import ProfilePage from '../pages/ProfilePage.vue'
import RankingPage from '../pages/RankingPage.vue'

const router = createRouter({
  history: createWebHistory(),
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
    { path: '/ranking', name: 'ranking', component: RankingPage },
    { path: '/community', name: 'community', component: CommunityPage },
    { path: '/profile', name: 'profile', component: ProfilePage },
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

export default router
