import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, getCurrentUser } from '@/api'

interface User {
  id: number
  username: string
  displayName: string
  role: string
  alipayAccount: string | null
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const user = ref<User | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  async function login(username: string, password: string) {
    const response = await loginApi(username, password)
    token.value = response.data.token
    user.value = response.data.user
    localStorage.setItem('token', response.data.token)
  }

  async function fetchUser() {
    if (!token.value) return
    try {
      const response = await getCurrentUser()
      user.value = response.data
    } catch {
      logout()
    }
  }

  function logout() {
    token.value = null
    user.value = null
    localStorage.removeItem('token')
  }

  function updateAlipayAccount(account: string) {
    if (user.value) {
      user.value.alipayAccount = account
    }
  }

  return {
    token,
    user,
    isLoggedIn,
    isAdmin,
    login,
    fetchUser,
    logout,
    updateAlipayAccount
  }
})
