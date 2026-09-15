import { defineStore } from 'pinia'
import { ref } from 'vue'

export type CurrentUser = {
  id: string
  username: string
  nickname: string
  role: string
  avatarUrl?: string
}

const USER_KEY = 'notemind_admin_user'

function readStoredUser(): CurrentUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    if (!raw) return null
    return JSON.parse(raw) as CurrentUser
  } catch {
    return null
  }
}

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const currentUser = ref<CurrentUser | null>(readStoredUser())

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setCurrentUser(user: CurrentUser) {
    currentUser.value = user
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  }

  function clearCurrentUser() {
    currentUser.value = null
    localStorage.removeItem(USER_KEY)
  }

  return { sidebarCollapsed, toggleSidebar, currentUser, setCurrentUser, clearCurrentUser }
})
