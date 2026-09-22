import request from './request'

/**
 * 后台管理接口定义。
 * 所有接口都走网关 /admin/** 前缀，最终由 admin-service 处理。
 */

/** 认证 */
export const authApi = {
  login: (data) => request.post('/admin/auth/login', data),
  logout: () => request.post('/admin/auth/logout'),
  me: () => request.get('/admin/auth/me')
}

/** 控制台 */
export const adminDashboardApi = {
  stats: () => request.get('/admin/dashboard/stats'),
  /** 近 N 天销量 / 成交额趋势 */
  trend: (days) => request.get('/admin/dashboard/trend', { params: { days } })
}

/** 用户管理 */
export const adminUserApi = {
  page: (params) => request.get('/admin/user/page', { params }),
  detail: (id) => request.get(`/admin/user/${id}`),
  disable: (id) => request.post(`/admin/user/${id}/disable`),
  enable: (id) => request.post(`/admin/user/${id}/enable`)
}

/** 商品管理 */
export const adminProductApi = {
  page: (params) => request.get('/admin/product/page', { params }),
  detail: (id) => request.get(`/admin/product/${id}`),
  save: (data) => request.post('/admin/product/save', data),

  upload: (file) => {
    const data = new FormData()
    data.append('file', file)
    return request.post('/admin/product/upload', data)
  },
  updateStatus: (id, status) =>
    request.post(`/admin/product/${id}/status`, null, { params: { status } }),
  updateStock: (id, stock) =>
    request.post(`/admin/product/${id}/stock`, null, { params: { stock } })
}

/** 订单管理 */
export const adminOrderApi = {
  page: (params) => request.get('/admin/order/page', { params }),
  detail: (id) => request.get(`/admin/order/${id}`),
  ship: (id) => request.post(`/admin/order/${id}/ship`),
  cancel: (id, reason) =>
    request.post(`/admin/order/${id}/cancel`, null, { params: { reason } }),
  refund: (id, reason) =>
    request.post(`/admin/order/${id}/refund`, null, { params: { reason } })
}

/** 系统管理：管理员 / 角色 / 日志 / 配置 */
export const adminSystemApi = {
  adminPage: (params) => request.get('/admin/system/admin/page', { params }),
  saveAdmin: (data) => request.post('/admin/system/admin/save', data),
  resetPassword: (id, newPassword) =>
    request.post(`/admin/system/admin/${id}/reset-password`, null, { params: { newPassword } }),

  rolePage: (params) => request.get('/admin/system/role/page', { params }),
  saveRole: (data) => request.post('/admin/system/role/save', data),
  deleteRole: (id) => request.delete(`/admin/system/role/${id}`),

  logPage: (params) => request.get('/admin/system/log/page', { params }),

  configList: (group) => request.get('/admin/system/config/list', { params: { group } }),
  updateConfig: (configKey, configValue) =>
    request.post('/admin/system/config/update', null, { params: { configKey, configValue } })
}
