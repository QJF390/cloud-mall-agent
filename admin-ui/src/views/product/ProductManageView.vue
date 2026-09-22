<script setup>
import { onMounted, reactive, ref } from 'vue'
import { adminProductApi } from '@/api/modules'
import { toast } from '@/utils/toast'
import PaginationBar from '@/components/PaginationBar.vue'

/** 商品管理：列表 / 新增 / 上下架 / 库存调整 */
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: null })
const list = ref([])
const total = ref(0)
const loading = ref(false)

/** 分类选项：与 t_product.category 注释保持一致，避免运营手打出五花八门的分类 */
const CATEGORIES = ['陶瓷', '绘画', '香薰', '饰品', '布艺', '木作', '其他']

const showForm = ref(false)
const saving = ref(false)
const emptyForm = () => ({
  name: '',
  category: CATEGORIES[0],
  price: '',
  stock: 1,
  status: 1,
  coverImage: '',
  images: '',
  tags: '',
  description: '',
  story: ''
})
const form = reactive(emptyForm())

/** 上传中标记：封面与图集分开，避免一个在传时把另一个的入口也锁死 */
const uploadingCover = ref(false)
const uploadingImages = ref(false)

/** 与后端 admin.upload.max-size-mb 保持一致。前端这道只是体验，真正的限制在后端 */
const MAX_UPLOAD_SIZE = 5 * 1024 * 1024

async function loadList() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.keyword) params.keyword = query.keyword
    if (query.status !== null) params.status = query.status
    const res = await adminProductApi.page(params)
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

async function handleStatus(row, status) {
  const action = status === 1 ? '上架' : '下架'
  if (!window.confirm(`确认${action}商品「${row.name}」？`)) return
  try {
    await adminProductApi.updateStatus(row.id, status)
    toast.success(`商品已${action}`)
    loadList()
  } catch (e) {
    toast.error(e.message)
  }
}

async function handleStock(row) {
  const input = window.prompt(`请输入「${row.name}」的新库存（当前 ${row.stock}）`, String(row.stock ?? 0))
  if (input === null) return
  const stock = Number(input)
  if (!Number.isInteger(stock) || stock < 0) {
    toast.error('库存必须是不小于 0 的整数')
    return
  }
  try {
    await adminProductApi.updateStock(row.id, stock)
    toast.success('库存已更新')
    loadList()
  } catch (e) {
    toast.error(e.message)
  }
}

function openCreate() {
  Object.assign(form, emptyForm())
  showForm.value = true
}

/**
 * 把运营输入的 "手作, 限量" 归一成 DB 约定的 JSON 数组字符串。
 * 不这么做的话，tags 会存成 "手作,限量"，C 端 JSON.parse 直接失败 → 标签静默消失（最难查的那种 bug）。
 */
function toJsonArray(input) {
  const raw = (input || '').trim()
  if (!raw) return null
  if (raw.startsWith('[')) return raw
  const items = raw
    .split(/[,，、]/)
    .map((s) => s.trim())
    .filter(Boolean)
  return items.length ? JSON.stringify(items) : null
}

/** 当前已填的图片列表（兼容"逗号分隔"与"JSON 数组"两种历史写法） */
function currentImages() {
  const raw = (form.images || '').trim()
  if (!raw) return []
  if (raw.startsWith('[')) {
    try {
      const arr = JSON.parse(raw)
      return Array.isArray(arr) ? arr : []
    } catch {
      return []
    }
  }
  return raw
    .split(/[,，\n]/)
    .map((s) => s.trim())
    .filter(Boolean)
}

/** 上传单个文件，返回可访问 URL */
async function uploadOne(file) {
  if (!file.type.startsWith('image/')) {
    throw new Error('只能上传图片文件')
  }
  if (file.size > MAX_UPLOAD_SIZE) {
    throw new Error(`单张图片不能超过 ${MAX_UPLOAD_SIZE / 1024 / 1024} MB`)
  }
  const res = await adminProductApi.upload(file)
  const url = res.data?.url
  if (!url) throw new Error('上传失败：服务端未返回图片地址')
  return url
}

async function onCoverChange(e) {
  const file = e.target.files?.[0]
  // 清空 input 的值：否则连续两次选同一个文件不会触发 change（浏览器认为 value 没变）
  e.target.value = ''
  if (!file) return
  uploadingCover.value = true
  try {
    form.coverImage = await uploadOne(file)
    toast.success('封面图上传成功')
  } catch (err) {
    toast.error(err.message)
  } finally {
    uploadingCover.value = false
  }
}

async function onImagesChange(e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  if (!files.length) return
  uploadingImages.value = true
  try {
    const urls = []
    // 串行上传：一次选十几张并发打过去容易撞到网关/容器连接数限制，
    // 而且部分失败时很难判断哪张成了，串行的结果才可控
    for (const file of files) {
      urls.push(await uploadOne(file))
    }
    form.images = [...currentImages(), ...urls].join(', ')
    toast.success(`已上传 ${urls.length} 张图片`)
  } catch (err) {
    toast.error(err.message)
  } finally {
    uploadingImages.value = false
  }
}

function removeImage(index) {
  const arr = currentImages()
  arr.splice(index, 1)
  form.images = arr.join(', ')
}

async function handleSave() {
  const name = form.name.trim()
  if (!name) {
    toast.error('请输入商品名称')
    return
  }
  if (name.length > 100) {
    toast.error('商品名称不能超过 100 个字符')
    return
  }
  if (form.price === '' || form.price === null) {
    toast.error('请输入商品价格')
    return
  }
  const price = Number(form.price)
  if (Number.isNaN(price) || price < 0) {
    toast.error('商品价格必须是不小于 0 的数字')
    return
  }
  const stock = Number(form.stock)
  if (!Number.isInteger(stock) || stock < 0) {
    toast.error('库存必须是不小于 0 的整数')
    return
  }

  const payload = {
    name,
    category: form.category || null,
    price,
    stock,
    status: form.status,
    coverImage: form.coverImage.trim() || null,
    images: toJsonArray(form.images),
    tags: toJsonArray(form.tags),
    description: form.description.trim() || null,
    story: form.story.trim() || null
  }

  saving.value = true
  try {
    await adminProductApi.save(payload)
    toast.success(form.status === 1 ? '商品创建成功，已上架' : '商品创建成功（已下架）')
    showForm.value = false
    query.pageNum = 1
    loadList()
  } catch (e) {
    toast.error(e.message)
  } finally {
    saving.value = false
  }
}

onMounted(loadList)
</script>

<template>
  <div class="card">
    <div class="page-toolbar">
      <input
        v-model.trim="query.keyword"
        class="input"
        placeholder="商品名称 / 分类"
        @keyup.enter="handleSearch"
      />
      <select v-model="query.status" class="select">
        <option :value="null">全部状态</option>
        <option :value="1">上架中</option>
        <option :value="0">已下架</option>
      </select>
      <button class="btn btn-primary" @click="handleSearch">查询</button>
      <button class="btn" :disabled="loading" @click="loadList">
        {{ loading ? '加载中…' : '刷新' }}
      </button>
      <button class="btn btn-primary" @click="openCreate">新增商品</button>
    </div>

    <div v-if="showForm" class="form-panel">
      <h3 class="form-title">新增商品</h3>
      <div class="form-grid">
        <label class="field">
          <span>商品名称 *</span>
          <input v-model.trim="form.name" class="input" placeholder="如：粗陶手作咖啡杯" />
        </label>
        <label class="field">
          <span>分类</span>
          <select v-model="form.category" class="select">
            <option v-for="c in CATEGORIES" :key="c" :value="c">{{ c }}</option>
          </select>
        </label>
        <label class="field">
          <span>售价（元）*</span>
          <input v-model="form.price" class="input" type="number" min="0" step="0.01" placeholder="0.00" />
        </label>
        <label class="field">
          <span>库存</span>
          <input v-model="form.stock" class="input" type="number" min="0" step="1" />
        </label>
        <label class="field">
          <span>状态</span>
          <select v-model.number="form.status" class="select">
            <option :value="1">上架中（前端立即可见）</option>
            <option :value="0">已下架</option>
          </select>
        </label>
        <div class="field">
          <span>封面图</span>
          <input
            v-model.trim="form.coverImage"
            class="input"
            placeholder="上传后自动填入，也可手填 /products/xxx.png"
          />
          <input
            class="input file"
            type="file"
            accept="image/png,image/jpeg,image/webp,image/gif"
            :disabled="uploadingCover"
            @change="onCoverChange"
          />
          <span class="hint">{{ uploadingCover ? '上传中…' : '从本地选择图片，单张不超过 5MB' }}</span>
          <img v-if="form.coverImage" class="preview" :src="form.coverImage" alt="封面预览" />
        </div>
        <div class="field">
          <span>商品图集（可多选）</span>
          <input
            v-model.trim="form.images"
            class="input"
            placeholder="上传后自动填入，逗号分隔"
          />
          <input
            class="input file"
            type="file"
            accept="image/png,image/jpeg,image/webp,image/gif"
            multiple
            :disabled="uploadingImages"
            @change="onImagesChange"
          />
          <span class="hint">{{ uploadingImages ? '上传中…' : '可一次选择多张，也可手填 /products/a.png, /products/b.png' }}</span>
          <div v-if="currentImages().length" class="thumb-list">
            <div v-for="(img, i) in currentImages()" :key="img + i" class="thumb-item">
              <img :src="img" :alt="`商品图 ${i + 1}`" />
              <button type="button" class="thumb-del" title="移除" @click="removeImage(i)">×</button>
            </div>
          </div>
        </div>
        <label class="field">
          <span>标签（逗号分隔）</span>
          <input v-model.trim="form.tags" class="input" placeholder="手作, 限量" />
        </label>
        <label class="field field-wide">
          <span>商品描述</span>
          <textarea v-model.trim="form.description" class="input textarea" rows="2" />
        </label>
        <label class="field field-wide">
          <span>创作故事（展示在详情页）</span>
          <textarea v-model.trim="form.story" class="input textarea" rows="2" />
        </label>
      </div>
      <div class="form-actions">
        <button class="btn btn-primary" :disabled="saving" @click="handleSave">
          {{ saving ? '保存中…' : form.status === 1 ? '保存并上架' : '保存为下架' }}
        </button>
        <button class="btn" @click="showForm = false">取消</button>
      </div>
    </div>

    <table class="table">
      <thead>
        <tr>
          <th>ID</th>
          <th>商品名称</th>
          <th>分类</th>
          <th>价格</th>
          <th>库存</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in list" :key="row.id">
          <td>{{ row.id }}</td>
          <td>{{ row.name }}</td>
          <td>{{ row.category || '—' }}</td>
          <td>￥{{ row.price }}</td>
          <td>{{ row.stock }}</td>
          <td>
            <span :class="row.status === 1 ? 'tag tag-success' : 'tag tag-muted'">
              {{ row.status === 1 ? '上架中' : '已下架' }}
            </span>
          </td>
          <td>
            <button
              v-if="row.status === 1"
              class="btn-link btn-link-danger"
              @click="handleStatus(row, 0)"
            >
              下架
            </button>
            <button v-else class="btn-link" @click="handleStatus(row, 1)">上架</button>
            <button class="btn-link stock-btn" @click="handleStock(row)">改库存</button>
          </td>
        </tr>
      </tbody>
    </table>

    <p v-if="!list.length && !loading" class="empty">暂无商品数据</p>

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
.field-wide {
  grid-column: 1 / -1;
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
.field .hint {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--c-text-sub);
}
.file {
  margin-top: 8px;
  padding: 6px 8px;
  font-size: 12px;
  cursor: pointer;
}
.preview {
  display: block;
  margin-top: 8px;
  width: 88px;
  height: 88px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid var(--c-border);
  background: #fff;
}
.thumb-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
}
.thumb-item {
  position: relative;
}
.thumb-item img {
  display: block;
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid var(--c-border);
  background: #fff;
}
.thumb-del {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 18px;
  height: 18px;
  padding: 0;
  line-height: 16px;
  border: none;
  border-radius: 50%;
  background: var(--c-danger);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}
.textarea {
  height: auto;
  padding: 8px 10px;
  line-height: 1.5;
  resize: vertical;
}
.form-actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}
.tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}
.tag-success {
  background: rgba(23, 166, 115, 0.12);
  color: var(--c-success);
}
.tag-muted {
  background: rgba(107, 118, 136, 0.12);
  color: var(--c-text-sub);
}
.btn-link-danger {
  color: var(--c-danger);
}
.stock-btn {
  margin-left: 10px;
}
</style>
