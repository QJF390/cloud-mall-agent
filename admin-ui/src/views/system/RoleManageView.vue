<script setup>
import { onMounted, reactive, ref } from 'vue'
import { adminSystemApi } from '@/api/modules'
import { toast } from '@/utils/toast'
import PaginationBar from '@/components/PaginationBar.vue'

/** 系统管理 · 角色权限（RBAC） */
const query = reactive({ pageNum: 1, pageSize: 10 })
const list = ref([])
const total = ref(0)
const loading = ref(false)

const showForm = ref(false)
const saving = ref(false)
const form = reactive({ id: null, roleCode: '', roleName: '', permissions: '' })

async function loadList() {
  loading.value = true
  try {
    const res = await adminSystemApi.rolePage({ pageNum: query.pageNum, pageSize: query.pageSize })
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
  Object.assign(form, { id: null, roleCode: '', roleName: '', permissions: '' })
  showForm.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    roleCode: row.roleCode,
    roleName: row.roleName,
    permissions: row.permissions || ''
  })
  showForm.value = true
}

async function handleSave() {
  if (!form.roleCode.trim()) {
    toast.error('请输入角色编码')
    return
  }
  if (!form.roleName.trim()) {
    toast.error('请输入角色名称')
    return
  }
  const permissions = form.permissions?.trim()
  if (permissions) {
    try {
      JSON.parse(permissions)
    } catch {
      toast.error('权限点必须是合法 JSON（如 ["admin:user:list"]）')
      return
    }
  }
  const payload = {
    roleCode: form.roleCode.trim(),
    roleName: form.roleName.trim(),
    permissions: permissions || null
  }
  if (form.id) payload.id = form.id

  saving.value = true
  try {
    await adminSystemApi.saveRole(payload)
    toast.success(form.id ? '角色已更新' : '角色创建成功')
    showForm.value = false
    loadList()
  } catch (e) {
    toast.error(e.message)
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  if (!window.confirm(`确认删除角色「${row.roleName}」？`)) return
  try {
    await adminSystemApi.deleteRole(row.id)
    toast.success('角色已删除')
    query.pageNum = 1
    loadList()
  } catch (e) {
    toast.error(e.message)
  }
}

onMounted(loadList)
</script>

<template>
  <div class="card">
    <div class="page-toolbar">
      <button class="btn" :disabled="loading" @click="loadList">
        {{ loading ? '加载中…' : '刷新' }}
      </button>
      <button class="btn btn-primary" @click="openCreate">新增角色</button>
    </div>

    <div v-if="showForm" class="form-panel">
      <h3 class="form-title">{{ form.id ? '编辑角色' : '新增角色' }}</h3>
      <div class="form-grid">
        <label class="field">
          <span>角色编码</span>
          <input v-model.trim="form.roleCode" class="input" placeholder="如 SUPER_ADMIN" />
        </label>
        <label class="field">
          <span>角色名称</span>
          <input v-model.trim="form.roleName" class="input" placeholder="如 超级管理员" />
        </label>
      </div>
      <label class="field perm-field">
        <span>权限点（JSON 数组，可留空）</span>
        <textarea
          v-model="form.permissions"
          class="input textarea"
          rows="3"
          placeholder='["admin:user:list","admin:product:edit"]'
        />
      </label>
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
          <th>角色编码</th>
          <th>角色名称</th>
          <th>权限点</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in list" :key="row.id">
          <td>{{ row.id }}</td>
          <td>{{ row.roleCode }}</td>
          <td>{{ row.roleName }}</td>
          <td class="perm-cell">{{ row.permissions || '—' }}</td>
          <td>
            <button class="btn-link" @click="openEdit(row)">编辑</button>
            <button class="btn-link delete-btn" @click="handleDelete(row)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>

    <p v-if="!list.length && !loading" class="empty">暂无角色数据</p>

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
.field .input {
  width: 100%;
}
.perm-field {
  margin-top: 12px;
}
.textarea {
  height: auto;
  padding: 8px 10px;
  font-family: inherit;
  resize: vertical;
}
.form-actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}
.perm-cell {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--c-text-sub);
}
.delete-btn {
  margin-left: 12px;
  color: var(--c-danger);
}
</style>
