<template>
  <div class="shell-task-container">
    <div class="task-header">
      <div class="header-left">
        <router-link :to="{ name: 'ShellJob' }" class="back-link">
          <font-awesome-icon icon="arrow-left" />
          <span>返回</span>
        </router-link>
        <h3 class="task-title">
          {{ isEditMode ? "编辑 Shell 任务" : "新建 Shell 任务" }}
        </h3>
      </div>
      <div class="header-center">
        <div class="form-item">
          <label class="field-label">任务名称</label>
          <el-input v-model="shellJob.jobName" placeholder="请输入任务名称" />
        </div>
        <div class="form-item">
          <label class="field-label">调度设置</label>
          <div class="cron-field">
            <CronExpressionSelector v-model:value="shellJob.cron" compact />
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button type="primary" :loading="isSaving" @click="save"
          >保存</el-button
        >
      </div>
    </div>

    <div class="task-body">
      <div class="section-header">
        <span>Shell 脚本</span>
        <span class="shortcut-hint">Ctrl+S 保存</span>
      </div>
      <div ref="editorContainer" class="shell-editor"></div>
    </div>
  </div>
</template>

<script lang="ts" src="./shell-job-update.component.ts"></script>

<style scoped>
.shell-task-container {
  height: calc(100vh - 60px);
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color, #fff);
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
  background: var(--el-bg-color, #fff);
  flex-shrink: 0;
  gap: 12px;
  flex-wrap: wrap;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-link {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--el-text-color-regular, #606266);
  text-decoration: none;
  cursor: pointer;
  font-size: 14px;
}

.back-link:hover {
  color: var(--el-color-primary, #409eff);
}

.task-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.header-center {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  min-width: 0;
}

.form-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.field-label {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular, #606266);
  white-space: nowrap;
  margin: 0;
}

.cron-field {
  width: 320px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.task-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary, #303133);
  background: var(--el-fill-color-lighter, #f5f7fa);
  flex-shrink: 0;
  gap: 12px;
}

.shortcut-hint {
  font-weight: normal;
  font-size: 12px;
  color: var(--el-text-color-placeholder, #a8abb2);
}

.shell-editor {
  flex: 1;
  overflow: hidden;
  min-height: 0;
}

.shell-editor :deep(.cm-editor) {
  height: 100%;
  font-size: 13px;
}

.shell-editor :deep(.cm-scroller) {
  font-family: "Menlo", "Monaco", "Courier New", monospace;
}

.shell-editor :deep(.cm-gutters) {
  border-right: 1px solid rgba(255, 255, 255, 0.1);
}
</style>
