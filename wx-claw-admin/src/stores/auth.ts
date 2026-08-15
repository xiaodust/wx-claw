import { defineStore } from 'pinia'
import { ref } from 'vue'
import { AUTH_FLAG_STORAGE, clearApiKey, setApiKey } from '../api/client'

export const useAuthStore = defineStore('auth', () => {
  const authenticated = ref(sessionStorage.getItem(AUTH_FLAG_STORAGE) === '1')
  function login(value?: string) {
    authenticated.value = true
    sessionStorage.setItem(AUTH_FLAG_STORAGE, '1')
    if (value) setApiKey(value); else clearApiKey()
  }
  function logout() {
    authenticated.value = false
    sessionStorage.removeItem(AUTH_FLAG_STORAGE)
    clearApiKey()
  }
  return { authenticated, login, logout }
})
