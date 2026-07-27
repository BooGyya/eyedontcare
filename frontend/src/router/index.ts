import { createRouter, createWebHistory } from 'vue-router'
import AccountPage from '../pages/AccountPage.vue'
import CommunityPage from '../pages/CommunityPage.vue'
import GamesPage from '../pages/GamesPage.vue'
import HomePage from '../pages/HomePage.vue'
import ProfilePage from '../pages/ProfilePage.vue'
import RankingPage from '../pages/RankingPage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomePage },
    { path: '/games', name: 'games', component: GamesPage },
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
