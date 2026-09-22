import request from './request'

export const kefuApi = {
  /** 会话列表（客服视角：所有咨询平台客服的会话） */
  sessions: () => request.get('/chat/admin/session/list'),
  /** 历史消息：cursor 为空取最新一页 */
  history: (sessionId, cursor, size = 30) =>
    request.get('/chat/admin/message/list', { params: { sessionId, cursor, size } }),
  /** 以客服身份回复 */
  reply: (sessionId, content) => request.post('/chat/admin/reply', { sessionId, content }),
  /** 标记已读（清掉客服侧未读） */
  read: (sessionId) => request.post('/chat/admin/message/read', null, { params: { sessionId } })
}
