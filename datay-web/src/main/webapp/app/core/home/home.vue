<template>
  <div class="home row">
    <div class="col-md-12">
      <h2 class="">使用指南</h2>
      <div id="guide" class="row">
        <div class="col-md-4 col-sm-12 mb-3" v-for="(step, index) in guideSteps" :key="step.title">
          <div class="card h-100 shadow-sm guide-card">
            <div class="card-body">
              <div class="guide-step-number">{{ index + 1 }}</div>
              <h4 class="card-title">{{ step.title }}</h4>
              <p class="card-text text-muted">{{ step.desc }}</p>
              <button class="btn btn-primary btn-sm" @click="step.action">{{ step.button }}</button>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="col-md-12">
      <h2 class="">数据源类型</h2>
      <p class="lead"></p>
      <div id="datasource" class="row">
        <div class="col-md-3 col-sm-6 mb-3" v-for="datasource in datasources" :key="datasource.name">
          <div class="card h-100 shadow-sm">
            <div class="card-body">
              <h4 class="card-title">
                <img :src="datasource.image" :alt="datasource.name" class="datasource-image" /> {{ datasource.name }}
              </h4>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="col-md-12">
      <h2 class="">数据处理组件</h2>
      <p class="lead"></p>
      <div id="component" class="row">
        <div class="col-md-3 col-sm-6 mb-3" v-for="component in components" :key="component.name">
          <div class="card h-100 shadow-sm">
            <div class="card-body">
              <h4 class="card-title"><img :src="component.image" class="component-image" />{{ component.name }}</h4>
              <small class="text-muted">{{ component.desc }}</small>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./home.component.ts"></script>

<style scoped>
.guide-card {
  border: none;
  border-radius: 12px;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.guide-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.guide-step-number {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4e8cff, #2b6bff);
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 16px;
}

.guide-card .card-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.guide-card .card-text {
  margin-bottom: 16px;
  line-height: 1.5;
}
</style>
