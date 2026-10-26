<template>
  <div class="dashboard-widget-card" :class="{ 'is-headerless': !showHeader }">
    <div v-if="showHeader" class="dashboard-widget-header">
      <span class="dashboard-widget-title">{{ widget.title || '' }}</span>
      <el-tag v-if="loading" size="small" type="info">加载中</el-tag>
      <el-tag v-else-if="error" size="small" type="danger">加载失败</el-tag>
    </div>
    <div class="dashboard-widget-body">
      <div v-if="error" class="dashboard-widget-error">
        <div class="dashboard-widget-error-title">数据加载失败</div>
        <div class="dashboard-widget-error-detail">{{ error }}</div>
      </div>
      <EChart v-else-if="widget.type === 'echarts'" :option="option" :theme="theme" />
      <KpiWidget v-else-if="widget.type === 'kpi'" :widget="widget" :result="result" />
      <DataTableWidget
        v-else-if="widget.type === 'table'"
        :widget="widget"
        :result="result"
        :server-paged="serverPaged"
        :page-loader="pageLoader"
        :page-reset-key="pageResetKey"
      />
      <PivotWidget v-else-if="widget.type === 'pivot'" :widget="widget" :result="result" />
      <HtmlWidget v-else-if="widget.type === 'html'" :widget="widget" />
      <div v-else class="dashboard-widget-empty">不支持的组件类型：{{ widget.type }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import DataTableWidget from './DataTableWidget.vue';
import EChart from './EChart.vue';
import HtmlWidget from './HtmlWidget.vue';
import KpiWidget from './KpiWidget.vue';
import PivotWidget from './PivotWidget.vue';
import { buildEchartsOption } from './spec-to-option';
import type { DashboardTheme, DashboardWidget, DatasetResult, TablePageLoader } from './types';

const props = defineProps<{
  widget: DashboardWidget;
  result?: DatasetResult;
  loading?: boolean;
  error?: string;
  theme?: DashboardTheme;
  serverPaged?: boolean;
  pageLoader?: TablePageLoader;
  pageResetKey?: number;
}>();

const option = computed(() => buildEchartsOption(props.widget, props.result));

/** 文本组件未设置标题时不渲染标题栏，让 HTML 内容占满整个组件。 */
const showHeader = computed(() => props.widget.type !== 'html' || !!props.widget.title?.trim());
</script>

<style scoped>
.dashboard-widget-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: 100%;
  color: var(--dashboard-text, #303133);
  background: var(--dashboard-surface, #fff);
  border-radius: 6px;
  box-shadow: var(--dashboard-shadow, 0 1px 4px rgb(0 0 0 / 8%));
}

.dashboard-widget-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--dashboard-border, #f0f0f0);
}

.dashboard-widget-title {
  color: var(--dashboard-text, #303133);
  font-size: 14px;
  font-weight: 600;
}

.dashboard-widget-body {
  flex: 1;
  min-height: 0;
  padding: 8px 12px 12px;
}

.dashboard-widget-card.is-headerless .dashboard-widget-body {
  padding: 4px 8px;
}

.dashboard-widget-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--dashboard-text-secondary, #909399);
}

.dashboard-widget-error {
  overflow: auto;
  height: 100%;
  padding: 8px;
  border-radius: 4px;
  background: var(--dashboard-error-bg, #fef0f0);
  color: var(--dashboard-error-text, #f56c6c);
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
