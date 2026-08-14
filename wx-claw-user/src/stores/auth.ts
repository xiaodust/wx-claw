import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { AUTH_TOKEN_STORAGE } from '../api/client'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(sessionStorage.getItem(AUTH_TOKEN_STORAGE) || '')
  const authenticated = computed(() => Boolean(token.value))
  function login(value: string) { token.value = value; sessionStorage.setItem(AUTH_TOKEN_STORAGE, value) }
  function logout() { token.value = ''; sessionStorage.removeItem(AUTH_TOKEN_STORAGE) }
  return { token, authenticated, login, logout }
})
