<template>
  <div class="error-page d-flex align-items-center justify-content-center">
    <div class="error-box text-center">
      <div class="error-illustration">
        <div class="error-code">{{ statusCode }}</div>
        <div class="error-icon">
          <font-awesome-icon :icon="errorIcon" />
        </div>
      </div>
      <h2 class="error-title">{{ errorTitle }}</h2>
      <p class="error-desc">{{ errorDescription }}</p>
      <div class="error-actions">
        <router-link to="/" class="btn btn-primary">
          <font-awesome-icon icon="home" class="mr-1" />返回首页
        </router-link>
        <button type="button" class="btn btn-outline-secondary" @click="goBack">
          <font-awesome-icon icon="arrow-left" class="mr-1" />返回上一页
        </button>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./error.component.ts"></script>

<style scoped>
.error-page {
  min-height: 70vh;
  padding: 2rem 1rem;
}

.error-box {
  max-width: 520px;
}

.error-illustration {
  position: relative;
  display: inline-block;
  margin-bottom: 1rem;
}

.error-code {
  font-size: 7rem;
  font-weight: 800;
  line-height: 1;
  letter-spacing: 0.25rem;
  background: linear-gradient(135deg, #3e8acc 0%, #6f42c1 100%);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  color: transparent;
}

.error-icon {
  position: absolute;
  right: -1.5rem;
  bottom: 0.75rem;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3rem;
  height: 3rem;
  font-size: 1.35rem;
  color: #3e8acc;
  background: #eef4fb;
  border-radius: 50%;
  box-shadow: 0 4px 12px rgba(62, 138, 204, 0.2);
}

.error-title {
  margin-bottom: 0.75rem;
  font-weight: 600;
  color: #212529;
}

.error-desc {
  margin-bottom: 1.75rem;
  color: #6c757d;
}

.error-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  justify-content: center;
}

@media (max-width: 576px) {
  .error-code {
    font-size: 5rem;
  }
}
</style>
