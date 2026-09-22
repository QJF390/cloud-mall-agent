import request from './request'

/** 用户模块 */
export const userApi = {
  register: (data) => request.post('/user/register', data),
  login: (data) => request.post('/user/login', data),
  sendSms: (phone) => request.post('/user/sms/send', { phone }),
  loginByPhone: (phone, code) => request.post('/user/login/phone', { phone, code }),
  detail: (id) => request.get(`/user/get/${id}`)
}

/** 商品模块（列表/搜索/分类公开；详情需登录，由网关拦截） */
export const productApi = {
  list: () => request.get('/product/list'),
  /** 搜索/分类浏览：params = { keyword, category, page, size } */
  search: (params) => request.get('/product/search', { params }),
  /** 分类导航（上架商品的真实分类） */
  categories: () => request.get('/product/categories'),
  detail: (id) => request.get(`/product/get/${id}`)
}

/** 购物车模块（全部需登录） */
export const cartApi = {
  add: (data) => request.post('/cart/add', data),
  updateQuantity: (params) => request.put('/cart/quantity', null, { params }),
  remove: (params) => request.delete('/cart/remove', { params }),
  list: (userId) => request.get(`/cart/list/${userId}`),
  clear: (userId) => request.delete(`/cart/clear/${userId}`),
  checkout: (data) => request.post('/cart/checkout', data)
}

/** 订单模块（需登录） */
export const orderApi = {
  create: (data) => request.post('/order/create', data),
  detail: (id) => request.get(`/order/get/${id}`),
  listByUser: (userId) => request.get(`/order/list/user/${userId}`),
  /** 我买到的 */
  listBuy: (userId) => request.get(`/order/list/buy/${userId}`),
  /** 批量取商品快照：orderIds 用逗号拼接，Spring 自动转 List<Long> */
  items: (orderIds) =>
    request.get('/order/items', { params: { orderIds: (orderIds || []).join(',') } }),
  cancel: (params) => request.post('/order/cancel', null, { params }),
  receive: (params) => request.post('/order/receive', null, { params }),
  refund: (params) => request.post('/order/refund', null, { params })
}

/** 账户模块（需登录） */
export const accountApi = {
  balance: (userId) => request.get('/account/balance', { params: { userId } })

}

/** 充值模块（走支付宝支付） */
export const rechargeApi = {
  /** 发起充值：返回 { rechargeNo, payUrl, amount } */
  create: (params) => request.post('/account/recharge/create', null, { params }),
  /** 查询充值状态：返回 { rechargeNo, status, amount, payTime } */
  status: (params) => request.get('/account/recharge/status', { params })
}
