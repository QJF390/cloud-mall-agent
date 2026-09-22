<script setup>
defineProps({
  product: { type: Object, required: true },
  // 大卡片：首页纵向图文卡片用
  large: { type: Boolean, default: false }
})

const emit = defineEmits(['select'])
</script>

<template>
  <article
    :class="['product-card', 'card', { 'is-large': large }]"
    @click="emit('select', product)"
  >
    <div class="card-media">
      <img
        :src="product.coverImage || '/products/hero.png'"
        :alt="product.name"
        loading="lazy"
        @error="(e) => (e.target.style.opacity = 0.15)"
      />
      <span v-if="product.category" class="card-cat">{{ product.category }}</span>
    </div>

    <div class="card-body">
      <h3 class="card-title">{{ product.name }}</h3>
      <p class="card-desc">{{ product.story || product.description || '一件慢慢做出来的东西。' }}</p>
      <div class="card-foot">
        <span class="price">{{ Number(product.price ?? 0).toFixed(2) }}</span>
        <span class="text-mute">余 {{ product.stock ?? 0 }}</span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.product-card {
  cursor: pointer;
  display: flex;
  flex-direction: column;
}

.card-media {
  position: relative;
  overflow: hidden;
  /* 图片占卡片 70% 高度：大卡片更高，形成 ONE 式大图阅读感 */
  aspect-ratio: 4 / 5;
}
.is-large .card-media {
  aspect-ratio: 16 / 11;
}

.card-media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.7s var(--ease), filter 0.4s var(--ease);
}
.product-card:hover .card-media img {
  transform: scale(1.045);
}

.card-cat {
  position: absolute;
  top: 14px;
  left: 14px;
  padding: 3px 12px;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: #fff;
  background: rgba(51, 51, 51, 0.34);
  backdrop-filter: blur(6px);
  border-radius: var(--r-pill);
}

.card-body {
  padding: 18px 20px 22px;
}
.card-title {
  font-size: 16.5px;
  font-weight: 500;
  letter-spacing: 0.03em;
}
.card-desc {
  margin-top: 8px;
  font-size: 13.5px;
  line-height: 1.85;
  color: var(--c-text-sub);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-foot {
  margin-top: 16px;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.card-foot .price {
  font-size: 18px;
}
</style>
