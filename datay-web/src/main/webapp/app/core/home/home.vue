<template>
  <div class="home">
    <!-- Hero Banner -->
    <section class="hero-banner" v-if="!authenticated">
      <div class="hero-bg">
        <svg id="svg-layer" viewBox="0 0 1440 842" fill="none" xmlns="http://www.w3.org/2000/svg" preserveAspectRatio="xMidYMid slice">
          <g clip-path="url(#clip0)">
            <path class="track-path" d="M374.547 1138L374.547 786.998C374.547 764.907 356.638 746.998 334.547 746.998L107.037 746.998C84.9459 746.998 67.0373 729.089 67.0373 706.998L67.0373 496.287C67.0373 474.196 49.1287 456.287 27.0373 456.287L-100.497 456.287" stroke="var(--hero-line-color)" stroke-width="1.5"/>
            <path class="track-path" d="M1072.53 -29.502V235.028C1072.53 257.119 1090.44 275.028 1112.53 275.028H1294.03C1316.12 275.028 1334.03 292.936 1334.03 315.028V614.85C1334.03 636.941 1351.94 654.85 1374.03 654.85H1583.29" stroke="var(--hero-line-color)" stroke-width="1.5"/>
            <path class="track-path" d="M547.208 -30.0958L547.208 86.498C547.208 108.589 529.299 126.498 507.208 126.498L148.641 126.498C126.55 126.498 108.641 108.589 108.641 86.498L108.641 67.9955C108.641 45.9041 90.7325 27.9955 68.6411 27.9955L-31.502 27.9955" stroke="var(--hero-line-color)" stroke-width="1.5"/>
            <path class="track-path" d="M1367.6 908.533L1369.38 803.43C1369.76 781.077 1351.75 762.752 1329.39 762.752L777.72 762.752C755.628 762.752 737.72 780.661 737.72 802.752L737.72 892.498" stroke="var(--hero-line-color)" stroke-width="1.5"/>
            <path class="track-path" d="M-19.9199 352.589L230.846 352.59C252.937 352.59 270.846 334.681 270.846 312.59L270.846 166.498C270.846 144.407 288.755 126.498 310.846 126.498L507 126.498C529.091 126.498 547 108.589 547 86.4981L547 -31.5" stroke="var(--hero-line-color)" stroke-width="1.5"/>
            <rect x="70.0449" y="358.998" width="13" height="28" rx="6.5" transform="rotate(-90 70.0449 358.998)" fill="var(--hero-line-color)"/>
            <rect x="1169.50" y="268" width="12" height="25" rx="6" transform="rotate(90 1168.54 268)" fill="var(--hero-line-color)"/>
            <path class="track-path" d="M1161.5 951L1161.5 566.467C1161.5 544.388 1179.38 526.485 1201.46 526.467L1294.01 526.39C1316.1 526.372 1334.02 544.26 1334.04 566.348L1334.09 614.936C1334.12 636.997 1352 654.875 1374.06 654.894L1499.63 655.002" stroke="var(--hero-line-color)" stroke-width="1.5"/>
            <path d="M1368 -107L1368 168.503C1368 190.594 1385.91 208.503 1408 208.503L1512.5 208.503" stroke="var(--hero-line-color)" stroke-width="1.5"/>
          </g>
          <defs>
            <clipPath id="clip0"><rect width="1440" height="842" fill="white"/></clipPath>
          </defs>
        </svg>
      </div>
      <div class="hero-content">
        <div class="hero-text">
          <h1 class="hero-title">轻量、可嵌入、可扩展、高性能、流批一体的数据平台</h1>
          <p class="hero-subtitle">
            DataY 是面向企业的数据集成与开发平台，支持多种数据源接入，提供可视化 ETL 设计、实时数据同步与调度，让数据流转更简单、更高效。
          </p>
          <div class="hero-actions">
            <button
              v-for="(action, idx) in heroActions"
              :key="idx"
              class="btn"
              :class="action.style === 'primary' ? 'hero-btn-primary' : 'hero-btn-outline'"
              @click="action.action"
            >
              {{ action.label }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- Stats Overview (authenticated only) -->
    <section class="stats-section" v-if="authenticated">
      <div class="container-fluid">
        <h2 class="section-title">
          <font-awesome-icon icon="tachometer-alt" class="section-title-icon" />
          概览统计
        </h2>
        <div class="stats-grid">
          <div class="stat-card" v-if="!loadingStats">
            <div class="stat-icon stat-icon-blue">
              <font-awesome-icon icon="database" />
            </div>
            <div class="stat-info">
              <span class="stat-value">{{ stats.dataSourceCount }}</span>
              <span class="stat-label">数据源</span>
            </div>
          </div>
          <div class="stat-card" v-if="!loadingStats">
            <div class="stat-icon stat-icon-purple">
              <font-awesome-icon icon="tasks" />
            </div>
            <div class="stat-info">
              <span class="stat-value">{{ stats.etlTaskCount }}</span>
              <span class="stat-label">ETL 任务</span>
            </div>
          </div>
          <div class="stat-card" v-if="!loadingStats">
            <div class="stat-icon stat-icon-green">
              <font-awesome-icon icon="sync" />
            </div>
            <div class="stat-info">
              <span class="stat-value">{{ stats.dataSyncCount }}</span>
              <span class="stat-label">数据同步</span>
            </div>
          </div>
          <div class="stat-card" v-if="!loadingStats">
            <div class="stat-icon stat-icon-orange">
              <font-awesome-icon icon="clock" />
            </div>
            <div class="stat-info">
              <span class="stat-value">{{ stats.runningInstanceCount }}</span>
              <span class="stat-label">运行实例</span>
            </div>
          </div>
          <div class="stat-card stat-card-placeholder" v-else v-for="n in 4" :key="n">
            <div class="stat-icon stat-icon-placeholder"></div>
            <div class="stat-info">
              <span class="stat-value placeholder"></span>
              <span class="stat-label placeholder"></span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Quick Start -->
    <section class="quickstart-section">
      <div class="container-fluid">
        <h2 class="section-title">
          <font-awesome-icon icon="rocket" class="section-title-icon" />
          快速开始
        </h2>
        <p class="section-desc">三步开启您的数据集成之旅</p>
        <div class="quickstart-grid">
          <div
            class="quickstart-card"
            v-for="(step, index) in guideSteps"
            :key="step.title"
            @click="step.action"
          >
            <div class="quickstart-step-number">{{ index + 1 }}</div>
            <div class="quickstart-icon" :style="{ background: step.color }">
              <font-awesome-icon :icon="step.icon" />
            </div>
            <h3 class="quickstart-title">{{ step.title }}</h3>
            <p class="quickstart-desc">{{ step.desc }}</p>
            <button class="quickstart-btn" :style="{ color: step.color }">
              {{ step.button }}
              <font-awesome-icon icon="arrow-right" class="ml-1" />
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- Data Source Ecosystem -->
    <section class="datasource-section">
      <div class="container-fluid">
        <h2 class="section-title">
          <font-awesome-icon icon="database" class="section-title-icon" />
          数据源生态
        </h2>
        <p class="section-desc">支持主流数据库与数据仓库，轻松接入各类数据源</p>
        <div class="datasource-grid">
          <div
            class="datasource-card"
            v-for="datasource in datasources"
            :key="datasource.name"
          >
            <img :src="datasource.image" :alt="datasource.name" class="datasource-logo" />
            <span class="datasource-name">{{ datasource.name }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- Core Components -->
    <section class="components-section">
      <div class="container-fluid">
        <h2 class="section-title">
          <font-awesome-icon icon="cubes" class="section-title-icon" />
          核心组件
        </h2>
        <p class="section-desc">丰富的数据处理组件，覆盖数据集成全链路</p>
        <div class="components-grid">
          <div
            class="component-card"
            v-for="component in components"
            :key="component.name"
          >
            <div class="component-icon" :style="{ background: component.color + '15', color: component.color }">
              <font-awesome-icon :icon="component.icon" />
            </div>
            <div class="component-info">
              <h4 class="component-name">{{ component.name }}</h4>
              <p class="component-desc">{{ component.desc }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script lang="ts" src="./home.component.ts"></script>

<style scoped>
.home {
  overflow-x: hidden;
}

/* ========== Hero Banner ========== */
.hero-banner {
  position: relative;
  padding: 80px 0 48px;
  overflow: hidden;
  --hero-line-color: rgba(99, 102, 241, 0.18);
}

.hero-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, #f8fafc 0%, #eef2f7 100%);
  z-index: 0;
}

.hero-bg svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.hero-bg::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  right: 0;
  height: 60px;
  background: linear-gradient(180deg, transparent, #f7f9fc);
  z-index: 2;
  pointer-events: none;
}

.hero-bg .track-path {
  stroke-dasharray: 4 4;
  stroke-linecap: round;
  animation: dash-move 30s linear infinite;
}

@keyframes dash-move {
  to {
    stroke-dashoffset: -100;
  }
}

.hero-bg::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 70% 50% at 50% 30%, rgba(99, 102, 241, 0.05) 0%, transparent 70%);
  z-index: 1;
  pointer-events: none;
}

.hero-content {
  position: relative;
  z-index: 2;
  max-width: 820px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.hero-text {
  color: #1a1f3a;
  text-align: center;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 14px;
  background: #ffffff;
  border: 1px solid #e3e8f2;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
  color: #4e8cff;
  margin-bottom: 24px;
  box-shadow: 0 2px 8px rgba(26, 31, 58, 0.04);
}

.hero-title {
  font-size: 52px;
  font-weight: 800;
  line-height: 1.12;
  margin-bottom: 20px;
  letter-spacing: -0.8px;
  background: linear-gradient(180deg, #1a1f3a 0%, #2d3561 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-subtitle {
  font-size: 18px;
  line-height: 1.7;
  color: #5a6478;
  margin: 0 auto 36px;
  max-width: 620px;
}

.hero-actions {
  display: flex;
  gap: 14px;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
}

.welcome-text {
  color: #5a6478;
  font-size: 15px;
  margin-right: 8px;
}

.hero-btn-primary {
  padding: 12px 28px;
  background: linear-gradient(135deg, #4e8cff, #2b6bff);
  border: none;
  border-radius: 10px;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s ease;
  box-shadow: 0 6px 20px rgba(78, 140, 255, 0.3);
}

.hero-btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 28px rgba(78, 140, 255, 0.45);
}

.hero-btn-outline {
  padding: 12px 28px;
  background: #ffffff;
  border: 1.5px solid #e3e8f2;
  border-radius: 10px;
  color: #1a1f3a;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s ease;
}

.hero-btn-outline:hover {
  border-color: #c7d1e3;
  background: #f7f9fc;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(26, 31, 58, 0.06);
}

/* ========== Section Common ========== */
.section-title {
  display: flex;
  align-items: center;
  font-size: 24px;
  font-weight: 700;
  color: #1a1f3a;
  margin-bottom: 6px;
}

.section-title-icon {
  color: #4e8cff;
  margin-right: 10px;
  font-size: 22px;
}

.section-desc {
  color: #6c757d;
  font-size: 15px;
  margin-bottom: 32px;
}

/* ========== Stats Section ========== */
.stats-section {
  padding: 32px 0 32px;
  background: #f7f9fc;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  transition: all 0.25s ease;
}

.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 12px;
  font-size: 24px;
  flex-shrink: 0;
}

.stat-icon-blue {
  background: #e8f0ff;
  color: #4e8cff;
}

.stat-icon-purple {
  background: #f3e8ff;
  color: #6f42c1;
}

.stat-icon-green {
  background: #e6f7ec;
  color: #28a745;
}

.stat-icon-orange {
  background: #fff3e6;
  color: #fd7e14;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #1a1f3a;
  line-height: 1.2;
}

.stat-label {
  font-size: 14px;
  color: #6c757d;
  margin-top: 2px;
}

.stat-card-placeholder {
  opacity: 0.6;
}

.stat-icon-placeholder {
  background: #e9ecef;
}

.placeholder {
  display: block;
  height: 20px;
  background: #e9ecef;
  border-radius: 4px;
  width: 80%;
}

/* ========== Quick Start Section ========== */
.quickstart-section {
  padding: 64px 0;
}

.quickstart-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.quickstart-card {
  position: relative;
  padding: 32px 28px;
  background: #fff;
  border-radius: 16px;
  border: 1px solid #edf0f5;
  cursor: pointer;
  transition: all 0.3s ease;
  overflow: hidden;
}

.quickstart-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, #4e8cff, #6f42c1);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.quickstart-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.08);
  border-color: transparent;
}

.quickstart-card:hover::before {
  opacity: 1;
}

.quickstart-step-number {
  position: absolute;
  top: 16px;
  right: 20px;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f7f9fc;
  border-radius: 50%;
  font-size: 14px;
  font-weight: 700;
  color: #6c757d;
}

.quickstart-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 14px;
  color: #fff;
  font-size: 26px;
  margin-bottom: 20px;
}

.quickstart-title {
  font-size: 20px;
  font-weight: 700;
  color: #1a1f3a;
  margin-bottom: 10px;
}

.quickstart-desc {
  font-size: 14px;
  color: #6c757d;
  line-height: 1.7;
  margin-bottom: 20px;
}

.quickstart-btn {
  background: none;
  border: none;
  padding: 0;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: opacity 0.2s ease;
}

.quickstart-btn:hover {
  opacity: 0.75;
}

/* ========== Data Source Section ========== */
.datasource-section {
  padding: 64px 0;
  background: #f7f9fc;
}

.datasource-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 16px;
}

.datasource-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #edf0f5;
  transition: all 0.25s ease;
}

.datasource-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  border-color: transparent;
}

.datasource-logo {
  width: 48px;
  height: 48px;
  margin-bottom: 12px;
  object-fit: contain;
}

.datasource-name {
  font-size: 14px;
  font-weight: 600;
  color: #1a1f3a;
  text-align: center;
}

/* ========== Components Section ========== */
.components-section {
  padding: 64px 0 80px;
}

.components-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.component-card {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 20px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #edf0f5;
  transition: all 0.25s ease;
}

.component-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
  border-color: transparent;
}

.component-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 10px;
  font-size: 20px;
  flex-shrink: 0;
}

.component-info {
  flex: 1;
  min-width: 0;
}

.component-name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1f3a;
  margin-bottom: 4px;
}

.component-desc {
  font-size: 13px;
  color: #6c757d;
  line-height: 1.5;
  margin: 0;
}

/* ========== Responsive ========== */
@media (max-width: 992px) {
  .hero-banner {
    padding: 64px 0 32px;
  }

  .hero-content {
    padding: 0 20px;
  }

  .hero-title {
    font-size: 38px;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .quickstart-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .hero-banner {
    padding: 48px 0 24px;
  }

  .hero-title {
    font-size: 30px;
  }

  .hero-subtitle {
    font-size: 15px;
  }

  .hero-actions {
    gap: 12px;
  }

  .hero-btn-primary,
  .hero-btn-outline {
    padding: 10px 22px;
    font-size: 14px;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
  }

  .stat-card {
    padding: 18px;
  }

  .stat-value {
    font-size: 26px;
  }

  .section-title {
    font-size: 20px;
  }

  .datasource-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>