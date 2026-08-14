import { createRouter, createWebHistory } from 'vue-router'
import { API_KEY_STORAGE } from '../api/client'
import HomeView from '../views/HomeView.vue'
import RegisterView from '../views/RegisterView.vue'
import LoginView from '../views/LoginView.vue'
import UserLayout from '../layouts/UserLayout.vue'
import BotsView from '../views/BotsView.vue'
import BotDetailView from '../views/BotDetailView.vue'
import SettingsView from '../views/SettingsView.vue'

const PUBLIC_PATHS = new Set(['/', '/register', '/login'])

const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/', component: HomeView },
  { path: '/register', component: RegisterView },
  { path: '/login', component: LoginView },
  { path: '/', component: UserLayout, children: [
    { path: '', redirect: '/bots' },
    { path: 'bots', component: BotsView },
    { path: 'bots/:botId', component: BotDetailView },
    { path: 'settings', component: SettingsView },
  ]},
  { path: '/:pathMatch(.*)*', redirect: '/' },
]})

router.beforeEach(to => {
  const authenticated = Boolean(sessionStorage.getItem(API_KEY_STORAGE))
  if (!authenticated && !PUBLIC_PATHS.has(to.path)) return '/'
  if (authenticated && to.path === '/login') return '/bots'
})

export default router
