import { createRouter, createWebHistory } from 'vue-router'
import { API_KEY_STORAGE } from '../api/client'
import LoginView from '../views/LoginView.vue'
import UserLayout from '../layouts/UserLayout.vue'
import BotsView from '../views/BotsView.vue'
import BotDetailView from '../views/BotDetailView.vue'
import SettingsView from '../views/SettingsView.vue'

const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/login', component: LoginView },
  { path: '/', component: UserLayout, children: [
    { path: '', redirect: '/bots' },
    { path: 'bots', component: BotsView },
    { path: 'bots/:botId', component: BotDetailView },
    { path: 'settings', component: SettingsView },
  ]},
  { path: '/:pathMatch(.*)*', redirect: '/bots' },
]})

router.beforeEach(to => {
  const authenticated = Boolean(sessionStorage.getItem(API_KEY_STORAGE))
  if (!authenticated && to.path !== '/login') return '/login'
  if (authenticated && to.path === '/login') return '/bots'
})

export default router
