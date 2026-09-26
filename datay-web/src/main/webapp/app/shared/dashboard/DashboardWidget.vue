<template>
  <div class="dashboard-widget-card">
    <div class="dashboard-widget-header">
      <span class="dashboard-widget-title">{{ widget.title || "" }}</span>
      <el-tag v-if="loading" size="small" type="info">加载中</el-tag>
      <el-tag v-else-if="error" size="small" type="danger">加载失败</el-tag>
    </div>
    <div class="dashboard-widget-body">
      <div v-if="error" class="dashboard-widget-error">
        <div class="dashboard-widget-error-title">数据加载失败</div>
        <div class="dashboard-widget-error-detail">{{ error }}</div>
      </div>
      <EChart v-else-if="widget.type === 'echarts'" :option="option" />
      <KpiWidget
        v-else-if="widget.type === 'kpi'"
        :widget="widget"
        :result="result"
      />
      <DataTableWidget
        v-else-if="widget.type === 'table'"
        :widget="widget"
        :result="result"
      />
      <HtmlWidget v-else-if="widget.type === 'html'" :widget="widget" />
      <div v-else class="dashboard-widget-empty">
        不支持的组件类型：{{ widget.type }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";

import DataTableWidget from "./DataTableWidget.vue";
import EChart from "./EChart.vue";
import HtmlWidget from "./HtmlWidget.vue";
import KpiWidget from "./KpiWidget.vue";
import { buildEchartsOption } from "./spec-to-option";
import type { DashboardWidget, DatasetResult } from "./types";

const props = defineProps<{
  widget: DashboardWidget;
  result?: DatasetResult;
  loading?: boolean;
  error?: string;
}>();

const option = computed(() => buildEchartsOption(props.widget, props.result));
</script>

<style scoped>
.dashboard-widget-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: 100%;
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 1px 4px rgb(0 0 0 / 8%);
}

.dashboard-widget-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid #f0f0f0;
}

.dashboard-widget-title {
  color: #303133;
  font-size: 14px;
  font-weight: 600;
}

.dashboard-widget-body {
  flex: 1;
  min-height: 0;
  padding: 8px 12px 12px;
}

.dashboard-widget-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
}

.dashboard-widget-error {
  overflow: auto;
  height: 100%;
  padding: 8px;
  border-radius: 4px;
  background: #fef0f0;
  color: #f56c6c;
}

.dashboard-widget-error-title {
  margin-bottom: 6px;
  font-weight: 600;
}

.dashboard-widget-error-detail {
  font-size: 12px;
  line-height: 1.5;
  word-break: break-all;
  white-space: pre-wrap;
}
</style>
