<script setup>
import { onMounted, reactive, ref } from 'vue'
import { adminSystemApi } from '@/api/modules'
import { toast } from '@/utils/toast'
import PaginationBar from '@/components/PaginationBar.vue'

/** 系统管理 · 管理员账号：新增/编辑 / 分配角色 / 重置密码 */
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })
const list = ref([])
const total = ref(0)
const loading = ref(false)

const roles = ref([])
const showForm = ref(false)
const saving = ref(false)
const form = reactive({ id: null, username: '', nickname: '', roleId: null, password: '', status: 1 })

const roleNameMap = ref({})

async function loadRoles() {
  try {
    const res = await adminSystemApi.rolePage({ pageNum: 1, pageSize: 200 })
    roles.value = res.data?.records || []
    roleNameMap.value = Object.fromEntries(roles.value.map((r) => [String(r.id), r.roleName]))
  } catch (e) {
    // 角色加载失败不阻塞主流程（可能是还没配置角色）
    toast.error(`角色列表加载失败：${e.message}`)
  }
}

async function loadList() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.keyword) params.keyword = query.keyword
    const res = await adminSystemApi.adminPage(params)
    list.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } catch (e) {
    list.value = []
    total.value = 0
    toast.error(e.message)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadList()
}

function handlePageChange(page) {
  query.pageNum = page
  loadList()
}

function handleSizeChange(size) {
  query.pageSize = size
  query.pageNum = 1
  loadList()
}

function openCreate() {
  Object.assign(form, { id: null, username: '', nickname: '', roleId: null, password: '', status: 1 })
  showForm.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    username: row.username,
    nickname: row.nickname,
    roleId: row.roleId ?? null,
    password: '',
    status: row.status ?? 1
  })
  showForm.value = true
}

async function handleSave() {
  if (!form.username.trim()) {
    toast.error('请输入登录名')
    return
  }
  if (!form.id && (!form.password || form.password.length < 6)) {
    toast.error('新增管理员需设置至少 6 位初始密码')
    return
  }
  const payload = {
    username: form.username.trim(),
    nickname: form.nickname?.trim() || null,
    roleId: form.roleId,
    status: form.status
  }
  if (form.id) payload.id = form.id
  else payload.password = form.password

  saving.value = true
  try {
    await adminSystemApi.saveAdmin(payload)
    toast.success(form.id ? '管理员已更新' : '管理员创建成功')
    showForm.value = false
    loadList()
  } catch (e) {
    toast.error(e.message)
  } finally {
    saving.value = false
  }
}

async function handleResetPassword(row) {
  if (!window.confirm(`确认重置「${row.username}」的密码？`)) return
  const input = window.prompt('请输入新密码（至少 6 位）', '')
  if (input === null) return
  if (!input || input.length < 6) {
    toast.error('密码长度不能少于 6 位')
    return
  }
  try {
    await adminSystemApi.resetPassword(row.id, input)
    toast.success('密码重置成功')
  } catch (e) {
    toast.error(e.message)
  }
}

onMounted(() => {
  loadRoles()
  loadList()
})
</script>

<template>
  <div class="card">
    <div class="page-toolbar">
      <input
        v-model.trim="query.keyword"
        class="input"
        placeholder="登录名 / 昵称"
        @keyup.enter="handleSearch"
      />
      <button class="btn btn-primary" @click="handleSearch">查询</button>
      <button class="btn" @click="openCreate">新增管理员</button>
    </div>

    <div v-if="showForm" class="form-panel">
      <h3 class="form-title">{{ form.id ? '编辑管理员' : '新增管理员' }}</h3>
      <div class="form-grid">
        <label class="field">
          <span>登录名</span>
          <input v-model.trim="form.username" class="input" />
        </label>
        <label class="field">
          <span>昵称</span>
          <input v-model.trim="form.nickname" class="input" />
        </label>
        <label class="field">
          <span>角色</span>
          <select v-model="form.roleId" class="select">
            <option :value="null">不分配</option>
            <option v-for="r in roles" :key="r.id" :value="r.id">{{ r.roleName }}</option>
          </select>
        </label>
        <label class="field">
          <span>状态</span>
          <select v-model.number="form.status" class="select">
            <option :value="1">启用</option>
            <option :value="0">禁用</option>
          </select>
        </label>
        <label v-if="!form.id" class="field">
          <span>初始密码</span>
          <input v-model="form.password" class="input" type="password" placeholder="至少 6 位" />
        </label>
      </div>
      <div class="form-actions">
        <button class="btn btn-primary" :disabled="saving" @click="handleSave">
          {{ saving ? '保存中…' : '保存' }}
        </button>
        <button class="btn" @click="showForm = false">取消</button>
      </div>
    </div>

    <table class="table">
      <thead>
        <tr>
          <th>ID</th>
          <th>登录名</th>
          <th>昵称</th>
          <th>角色</th>
          <th>状态</th>
          <th>最后登录</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in list" :key="row.id">
          <td>{{ row.id }}</td>
          <td>{{ row.username }}</td>
          <td>{{ row.nickname || '—' }}</td>
          <td>{{ roleNameMap[String(row.roleId)] || '—' }}</td>
          <td>{{ row.status === 1 ? '启用' : '禁用' }}</td>
          <td>{{ row.lastLoginTime || '—' }}</td>
          <td>
            <button class="btn-link" @click="openEdit(row)">编辑</button>
            <button class="btn-link reset-btn" @click="handleResetPassword(row)">重置密码</button>
          </td>
        </tr>
      </tbody>
    </table>

    <p v-if="!list.length && !loading" class="empty">暂无管理员数据</p>

    <PaginationBar
      :page="query.pageNum"
      :size="query.pageSize"
      :total="total"
      @change="handlePageChange"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<style scoped>
.form-panel {
  margin-bottom: 16px;
  padding: 16px;
  background: #fafbfe;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
}
.form-title {
  font-size: 14px;
  margin-bottom: 12px;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}
.field {
  display: block;
}
.field span {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--c-text-sub);
}
.field .input,
.field .select {
  width: 100%;
}
.form-actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}
.reset-btn {
  margin-left: 12px;
}
</style>
