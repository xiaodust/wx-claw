import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { API_KEY_STORAGE } from '../api/client'

export const useAuthStore = defineStore('auth', () => {
  const apiKey = ref(sessionStorage.getItem(API_KEY_STORAGE) || '')
  const authenticated = computed(() => Boolean(apiKey.value))
  function login(value:string) { apiKey.value = value; sessionStorage.setItem(API_KEY_STORAGE, value) }
  function logout() { apiKey.value = ''; sessionStorage.removeItem(API_KEY_STORAGE) }
  return { apiKey, authenticated, login, logout }
})
