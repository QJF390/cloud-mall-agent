import { defineStore } from 'pinia'
import { authApi } from '@/api/modules'
import { getToken, setToken, getUser, setUser, clearAuth } from '@/utils/auth'

/**
 * 管理员登录态。
 * token 由 admin-service 登录成功后通过 Sa-Token 生成，前端持久化到 localStorage。
 */
export const useAdminStore = defineStore('admin', {
  state: () => ({
    token: getToken(),
    profile: getUser()
  }),

  getters: {
    isLogin: (state) => !!state.token,
    username: (state) => state.profile?.username ?? '',
    nickname: (state) => state.profile?.nickname ?? '',
    roleCode: (state) => state.profile?.roleCode ?? ''
  },

  actions: {
    async login({ username, password }) {
      // TODO: 后端登录接口实现后，替换为真实返回结构
      const res = await authApi.login({ username, password })
      this.saveSession(res.data)
      return res
    },

    saveSession(data) {
      if (!data) return
      this.token = data.token || ''
      this.profile = {
        id: data.id,
        username: data.username,
        nickname: data.nickname,
        roleCode: data.roleCode
      }
      setToken(this.token)
      setUser(this.profile)
    },

    /** 刷新当前管理员信息（页面刷新后可用 token 重新拉一次，保证资料不过期） */
    async fetchProfile() {
      const res = await authApi.me()
      if (res?.data) {
        this.profile = { ...this.profile, ...res.data }
        setUser(this.profile)
      }
      return res
    },

    async logout() {
      try {
        await authApi.logout()
      } finally {
        this.token = ''
        this.profile = null
        clearAuth()
      }
    }
  }
})
