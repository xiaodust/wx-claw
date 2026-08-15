import { defineStore } from 'pinia'
import { ref } from 'vue'
import { AUTH_FLAG_STORAGE } from '../api/client'

export const useAuthStore = defineStore('auth', () => {
  const authenticated = ref(sessionStorage.getItem(AUTH_FLAG_STORAGE) === '1')
  function login() { authenticated.value = true; sessionStorage.setItem(AUTH_FLAG_STORAGE, '1') }
  function logout() { authenticated.value = false; sessionStorage.removeItem(AUTH_FLAG_STORAGE) }
  return { authenticated, login, logout }
})
