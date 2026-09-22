<script setup>
defineProps({
  stage: { type: String, default: '' },
  title: { type: String, default: '' },
  desc: { type: String, default: '' }
})
</script>

<template>
  <div class="container ph">
    <p class="ph-stage">{{ stage }}</p>
    <h1 class="ph-title">{{ title }}</h1>
    <p class="ph-desc">{{ desc }}</p>
    <div class="ph-actions">
      <router-link class="btn btn-primary" to="/products">去市集逛逛</router-link>
      <router-link class="btn btn-ghost" to="/">回到首页</router-link>
    </div>
  </div>
</template>

<style scoped>
.ph {
  padding: 110px 24px 140px;
  text-align: center;
}
.ph-stage {
  font-size: 12.5px;
  letter-spacing: 0.3em;
  color: var(--c-accent-deep);
}
.ph-title {
  margin-top: 16px;
  font-family: var(--font-serif);
  font-size: 32px;
  font-weight: 500;
  letter-spacing: 0.16em;
}
.ph-desc {
  margin: 16px auto 0;
  max-width: 460px;
  font-size: 14px;
  line-height: 2;
  color: var(--c-text-sub);
}
.ph-actions {
  margin-top: 34px;
  display: flex;
  gap: 14px;
  justify-content: center;
  flex-wrap: wrap;
}
</style>
