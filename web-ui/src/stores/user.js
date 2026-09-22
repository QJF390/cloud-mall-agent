import { defineStore } from 'pinia'
import { userApi } from '@/api/modules'
import {
  getToken,
  setToken,
  getUser,
  setUser,
  clearAuth
} from '@/utils/auth'

/**
 * 用户登录态。
 * token 由 user-service 登录成功后通过 Sa-Token 生成，前端持久化到 localStorage，
 * 之后所有请求由 request.js 自动带上 satoken 请求头，交给网关校验。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    profile: getUser()
  }),

  getters: {
    isLogin: (state) => !!state.token,
    userId: (state) => state.profile?.id ?? null,
    username: (state) => state.profile?.username ?? ''
  },

  actions: {
    /** 用户名 + 密码登录 */
    async login({ username, password }) {
      const res = await userApi.login({ username, password })
      this.saveSession(res.data)
      return res
    },

    /** 手机号 + 验证码登录 */
    async loginByPhone({ phone, code }) {
      const res = await userApi.loginByPhone(phone, code)
      this.saveSession(res.data)
      return res
    },

    /** 注册（注册接口同样会下发 token，可直接进入登录态） */
    async register(payload) {
      const res = await userApi.register(payload)
      this.saveSession(res.data)
      return res
    },

    saveSession(data) {
      if (!data) return
      this.token = data.token || ''
      this.profile = { id: data.id, username: data.username }
      setToken(data.token)
      setUser(this.profile)
    },

    logout() {
      this.token = ''
      this.profile = null
      clearAuth()
    }
  }
})
