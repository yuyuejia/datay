<template>
  <div class="home">
    <!-- Unauthenticated -->
    <section class="login-prompt" v-if="!authenticated">
      <div class="login-prompt-card">
        <div class="login-prompt-icon">
          <font-awesome-icon icon="database" />
        </div>
        <h1 class="login-prompt-title">DataY 数据平台</h1>
        <p class="login-prompt-desc">登录后查看数据资产概览与任务运行情况</p>
        <div class="login-prompt-actions">
          <button class="btn-primary" @click="showLogin()">登录</button>
          <router-link to="/login" custom v-slot="{ navigate }">
            <button class="btn-outline" @click="navigate">前往登录页</button>
          </router-link>
        </div>
      </div>
    </section>

    <template v-else>
      <!-- Welcome -->
      <section class="welcome-section">
        <div class="welcome-content">
          <div>
            <h1 class="welcome-title">数据平台概览</h1>
            <p class="welcome-desc">
              <template v-if="username">你好，{{ username }}，</template>
              这里是您的数据资产与任务运行情况
            </p>
          </div>
          <el-button class="refresh-btn" :disabled="loading" @click="loadData">
            <font-awesome-icon icon="sync" :spin="loading" />
            <span>刷新</span>
          </el-button>
        </div>
      </section>

      <!-- 空租户引导：平台不预置业务数据，数据应用来自应用市场 -->
      <section class="market-cta" v-if="showMarketCta">
        <div class="market-cta-body">
          <font-awesome-icon icon="box-open" class="market-cta-icon" />
          <div>
            <h3 class="market-cta-title">当前租户还没有数据应用</h3>
            <p class="market-cta-desc">
              平台不预置业务数据。到「数据服务应用市场」选择一个业务场景（如「电商销售分析应用」「DataY
              快速入门应用」），一键初始化即可得到完整的数据源、数据模型、指标、ETL 任务与任务编排。
            </p>
          </div>
        </div>
        <el-button type="primary" @click="goTo('/app-package')">
          <font-awesome-icon icon="box-open" />
          <span>去应用市场初始化</span>
        </el-button>
      </section>

      <!-- Overview stats -->
      <section class="stats-section">
        <div class="stats-grid">
          <div class="stat-card">
            <div class="stat-icon stat-icon-blue">
              <font-awesome-icon icon="database" />
            </div>
            <div class="stat-info">
              <span class="stat-value">{{ stats.dataSourceCount }}</span>
              <span class="stat-label">数据源</span>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon stat-icon-purple">
              <font-awesome-icon icon="table" />
            </div>
            <div class="stat-info">
              <span class="stat-value">{{ stats.dataModelCount }}</span>
              <span class="stat-label">数据模型</span>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon stat-icon-green">
              <font-awesome-icon icon="tasks" />
            </div>
            <div class="stat-info">
              <span class="stat-value">{{ stats.taskCount }}</span>
              <span class="stat-label">任务总数</span>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon stat-icon-orange">
              <font-awesome-icon icon="clock" />
            </div>
            <div class="stat-info">
              <span class="stat-value">{{ stats.runningCount }}</span>
              <span class="stat-label">运行中任务</span>
            </div>
          </div>
        </div>
      </section>

      <!-- Distribution + Guide -->
      <section class="dashboard-section">
        <div class="dashboard-grid">
          <div class="panel">
            <div class="panel-header">
              <h2 class="panel-title">
                <font-awesome-icon icon="database" class="panel-title-icon" />
                数据源类型分布
              </h2>
            </div>
            <div class="type-list" v-if="typeDistribution.length">
              <div class="type-item" v-for="item in typeDistribution" :key="item.type">
                <img v-if="item.image" :src="item.image" :alt="item.label" class="type-logo" />
                <div v-else class="type-logo type-logo-placeholder">
                  <font-awesome-icon icon="database" />
                </div>
                <span class="type-name">{{ item.label }}</span>
                <div class="type-bar">
                  <div class="type-bar-fill" :style="{ width: item.percent + '%', background: item.color }"></div>
                </div>
                <span class="type-count" :style="{ color: item.color }">{{ item.count }}</span>
              </div>
            </div>
            <div class="empty-state" v-else>
              <font-awesome-icon icon="database" class="empty-icon" />
              <span>暂无数据源，去添加一个吧</span>
            </div>
          </div>

          <div class="panel">
            <div class="panel-header">
              <h2 class="panel-title">
                <font-awesome-icon icon="rocket" class="panel-title-icon" />
                使用向导
              </h2>
              <span class="panel-subtitle">四步完成数据全链路开发</span>
            </div>
            <div class="guide-flow">
              <div class="guide-step" v-for="(step, index) in guideSteps" :key="step.title" @click="goTo(step.route)">
                <div class="guide-step-index">{{ index + 1 }}</div>
                <div class="guide-step-icon" :style="{ background: step.color + '14', color: step.color }">
                  <font-awesome-icon :icon="step.icon" />
                </div>
                <div class="guide-step-body">
                  <h3 class="guide-step-title">{{ step.title }}</h3>
                  <p class="guide-step-desc">{{ step.desc }}</p>
                </div>
                <font-awesome-icon icon="arrow-right" class="guide-step-arrow" />
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Task operations -->
      <section class="ops-section">
        <div class="ops-grid">
          <div class="panel">
            <div class="panel-header">
              <h2 class="panel-title">
                <font-awesome-icon icon="exclamation-circle" class="panel-title-icon icon-danger" />
                最近一天执行失败的任务
              </h2>
              <router-link to="/job-instance" class="panel-link">查看全部</router-link>
            </div>
            <div class="task-table" v-if="failedInstances.length">
              <div class="task-table-head">
                <span class="col-name">任务名称</span>
                <span class="col-type">类型</span>
                <span class="col-time">开始时间</span>
                <span class="col-status">状态</span>
              </div>
              <div class="task-table-row" v-for="instance in failedInstances" :key="instance.id">
                <span class="col-name" :title="instance.jobName">{{ instance.jobName || '-' }}</span>
                <span class="col-type">{{ instance.type || '-' }}</span>
                <span class="col-time">{{ formatTime(instance.startTime) || '-' }}</span>
                <span class="col-status">
                  <span class="status-badge" :class="statusClass(instance.status)">{{ statusText(instance.status) }}</span>
                </span>
              </div>
            </div>
            <div class="empty-state" v-else>
              <font-awesome-icon icon="heart" class="empty-icon empty-icon-success" />
              <span>最近一天没有失败的任务</span>
            </div>
          </div>

          <div class="panel">
            <div class="panel-header">
              <h2 class="panel-title">
                <font-awesome-icon icon="sync" class="panel-title-icon icon-running" />
                正在运行中的任务
              </h2>
              <router-link to="/job-instance" class="panel-link">查看全部</router-link>
            </div>
            <div class="task-table" v-if="runningInstances.length">
              <div class="task-table-head">
                <span class="col-name">任务名称</span>
                <span class="col-type">类型</span>
                <span class="col-time">开始时间</span>
                <span class="col-status">状态</span>
              </div>
              <div class="task-table-row" v-for="instance in runningInstances" :key="instance.id">
                <span class="col-name" :title="instance.jobName">{{ instance.jobName || '-' }}</span>
                <span class="col-type">{{ instance.type || '-' }}</span>
                <span class="col-time">{{ formatTime(instance.startTime) || '-' }}</span>
                <span class="col-status">
                  <span class="status-badge" :class="statusClass(instance.status)">{{ statusText(instance.status) }}</span>
                </span>
              </div>
            </div>
            <div class="empty-state" v-else>
              <font-awesome-icon icon="clock" class="empty-icon" />
              <span>当前没有正在运行的任务</span>
            </div>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<script lang="ts" src="./home.component.ts"></script>

<style scoped>
.home {
  overflow-x: hidden;
  min-height: 100%;
  background: #f4f5f7;
  padding-bottom: 48px;
  color: #1f2329;
  font-variant-numeric: tabular-nums;
}

/* ========== Buttons ========== */
.btn-primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 9px 24px;
  background: #1677ff;
  border: 1px solid #1677ff;
  border-radius: 3px;
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease;
}

.btn-primary:hover {
  background: #0e5fd8;
  border-color: #0e5fd8;
}

.btn-outline {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 9px 18px;
  background: #fff;
  border: 1px solid #d9dde4;
  border-radius: 3px;
  color: #1f2329;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    color 0.2s ease,
    background 0.2s ease;
}

.btn-outline:hover:not(:disabled) {
  border-color: #1677ff;
  color: #1677ff;
}

.btn-outline:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* ========== Login Prompt ========== */
.login-prompt {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 64px);
  padding: 40px 20px;
}

.login-prompt-card {
  text-align: center;
  background: #fff;
  border: 1px solid #e6e9ef;
  border-top: 3px solid #1677ff;
  border-radius: 3px;
  padding: 48px 44px;
  box-shadow: 0 4px 16px rgba(31, 35, 41, 0.06);
  max-width: 420px;
  width: 100%;
}

.login-prompt-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 4px;
  background: #1677ff;
  color: #fff;
  font-size: 26px;
  margin-bottom: 22px;
}

.login-prompt-title {
  font-size: 24px;
  font-weight: 600;
  color: #1f2329;
  margin-bottom: 10px;
}

.login-prompt-desc {
  font-size: 14px;
  color: #86909c;
  margin-bottom: 26px;
}

.login-prompt-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}

/* ========== Welcome ========== */
.welcome-section {
  padding: 24px 24px 4px;
}

/* ========== 空租户：引导去应用市场 ========== */
.market-cta {
  margin: 16px 24px 0;
  padding: 18px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  border: 1px solid #cfe0ff;
  border-radius: 10px;
  background: linear-gradient(90deg, #f2f7ff 0%, #ffffff 100%);
}

.market-cta-body {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.market-cta-icon {
  font-size: 22px;
  color: #1677ff;
  margin-top: 2px;
}

.market-cta-title {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
}

.market-cta-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #4e5969;
  max-width: 720px;
}

.welcome-content {
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.welcome-title {
  font-size: 22px;
  font-weight: 600;
  color: #1f2329;
  margin-bottom: 4px;
}

.welcome-desc {
  font-size: 13px;
  color: #86909c;
  margin: 0;
}

.refresh-btn {
  white-space: nowrap;
}

/* ========== Common ========== */
.stats-section,
.dashboard-section,
.ops-section {
  padding: 16px 24px 0;
}

.stats-grid,
.dashboard-grid,
.ops-grid {
  margin: 0 auto;
}

/* ========== Stats ========== */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #fff;
  border: 1px solid #e6e9ef;
  border-left: 3px solid #d9dde4;
  border-radius: 3px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.stat-card:hover {
  box-shadow: 0 4px 14px rgba(31, 35, 41, 0.07);
}

.stats-grid .stat-card:nth-child(1) {
  border-left-color: #1677ff;
}

.stats-grid .stat-card:nth-child(2) {
  border-left-color: #722ed1;
}

.stats-grid .stat-card:nth-child(3) {
  border-left-color: #00b42a;
}

.stats-grid .stat-card:nth-child(4) {
  border-left-color: #ff7d00;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 4px;
  font-size: 20px;
  flex-shrink: 0;
}

.stat-icon-blue {
  background: #e8f1ff;
  color: #1677ff;
}

.stat-icon-purple {
  background: #f2eafd;
  color: #722ed1;
}

.stat-icon-green {
  background: #e6f9ec;
  color: #00b42a;
}

.stat-icon-orange {
  background: #fff1e3;
  color: #ff7d00;
}

.stat-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.stat-value {
  font-size: 30px;
  font-weight: 600;
  color: #1f2329;
  line-height: 1.15;
}

.stat-label {
  font-size: 13px;
  color: #86909c;
  margin-top: 2px;
}

/* ========== Panels ========== */
.dashboard-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.ops-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.panel {
  background: #fff;
  border: 1px solid #e6e9ef;
  border-radius: 3px;
  padding: 20px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eef0f3;
}

.panel-title {
  display: flex;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
  margin: 0;
}

.panel-title-icon {
  color: #1677ff;
  margin-right: 8px;
  font-size: 14px;
}

.icon-danger {
  color: #f53f3f;
}

.icon-running {
  color: #00b42a;
}

.panel-subtitle {
  font-size: 12px;
  color: #86909c;
}

.panel-link {
  font-size: 12px;
  color: #1677ff;
  text-decoration: none;
}

.panel-link:hover {
  text-decoration: underline;
}

/* ========== Type distribution ========== */
.type-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.type-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.type-logo {
  width: 26px;
  height: 26px;
  object-fit: contain;
  flex-shrink: 0;
}

.type-logo-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #86909c;
  background: #f2f3f5;
  border-radius: 2px;
  font-size: 12px;
}

.type-name {
  width: 110px;
  font-size: 13px;
  color: #1f2329;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 0;
}

.type-bar {
  flex: 1;
  height: 6px;
  background: #eef0f3;
  overflow: hidden;
}

.type-bar-fill {
  height: 100%;
  transition: width 0.5s ease;
}

.type-count {
  width: 28px;
  text-align: right;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}

/* ========== Guide ========== */
.guide-flow {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.guide-step {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #eef0f3;
  border-left: 3px solid #eef0f3;
  border-radius: 2px;
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease;
}

.guide-step:hover {
  background: #f7f8fa;
  border-left-color: #1677ff;
}

.guide-step-index {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 2px;
  background: #f2f3f5;
  color: #4e5969;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.guide-step-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 3px;
  font-size: 16px;
  flex-shrink: 0;
}

.guide-step-body {
  flex: 1;
  min-width: 0;
}

.guide-step-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
  margin: 0 0 2px;
}

.guide-step-desc {
  font-size: 12px;
  color: #86909c;
  margin: 0;
  line-height: 1.5;
}

.guide-step-arrow {
  color: #c9cdd4;
  font-size: 12px;
  flex-shrink: 0;
}

/* ========== Task tables ========== */
.task-table {
  display: flex;
  flex-direction: column;
}

.task-table-head,
.task-table-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
}

.task-table-head {
  border-bottom: 1px solid #eef0f3;
  font-size: 12px;
  color: #86909c;
  font-weight: 500;
  padding-top: 0;
}

.task-table-row {
  border-bottom: 1px solid #f2f3f5;
  font-size: 13px;
  color: #4e5969;
}

.task-table-row:last-child {
  border-bottom: none;
}

.col-name {
  flex: 1 1 auto;
  min-width: 0;
  font-weight: 500;
  color: #1f2329;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.col-type {
  flex: 0 0 70px;
}

.col-time {
  flex: 0 0 130px;
  color: #86909c;
}

.col-status {
  flex: 0 0 64px;
  text-align: right;
}

.status-badge {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 2px;
  font-size: 12px;
  font-weight: 500;
  background: #f2f3f5;
  color: #4e5969;
}

.status-badge.status-running,
.status-badge.status-appending {
  background: #e6f9ec;
  color: #00b42a;
}

.status-badge.status-failed,
.status-badge.status-timeout {
  background: #fdecec;
  color: #f53f3f;
}

.status-badge.status-waiting {
  background: #fff1e3;
  color: #ff7d00;
}

/* ========== Empty ========== */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 34px 0;
  color: #86909c;
  font-size: 13px;
}

.empty-icon {
  font-size: 24px;
  color: #c9cdd4;
}

.empty-icon-success {
  color: #a8e0bc;
}

/* ========== Responsive ========== */
@media (max-width: 992px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .dashboard-grid,
  .ops-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .welcome-section,
  .stats-section,
  .dashboard-section,
  .ops-section {
    padding-left: 16px;
    padding-right: 16px;
  }

  .welcome-title {
    font-size: 20px;
  }

  .stats-grid {
    gap: 12px;
  }

  .stat-card {
    padding: 16px;
    gap: 12px;
  }

  .stat-value {
    font-size: 24px;
  }

  .type-name {
    width: 84px;
  }

  .col-type {
    display: none;
  }

  .col-time {
    flex: 0 0 96px;
  }
}
</style>
