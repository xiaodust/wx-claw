import { createRouter, createWebHistory } from 'vue-router'
import { AUTH_FLAG_STORAGE } from '../api/client'
import LoginView from '../views/LoginView.vue'
import AdminLayout from '../layouts/AdminLayout.vue'
import OverviewView from '../views/OverviewView.vue'
import BotsView from '../views/BotsView.vue'
import ConversationsView from '../views/ConversationsView.vue'
import ConversationDetailView from '../views/ConversationDetailView.vue'
import InviteCodesView from '../views/InviteCodesView.vue'

const router = createRouter({ history:createWebHistory(), routes:[
  { path:'/login', component:LoginView },
  { path:'/', component:AdminLayout, children:[
    { path:'', redirect:'/overview' },
    { path:'overview', component:OverviewView },
    { path:'bots', component:BotsView },
    { path:'conversations', component:ConversationsView },
    { path:'conversations/:id', component:ConversationDetailView },
    { path:'invite-codes', component:InviteCodesView },
  ]},
  { path:'/:pathMatch(.*)*', redirect:'/overview' },
]})
router.beforeEach(to => {
  const authenticated = sessionStorage.getItem(AUTH_FLAG_STORAGE) === '1'
  if (!authenticated && to.path !== '/login') return '/login'
  if (authenticated && to.path === '/login') return '/overview'
})
export default router
