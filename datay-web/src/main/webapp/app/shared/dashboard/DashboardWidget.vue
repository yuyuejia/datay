<template>
  <div
    class="dashboard-widget-card"
    :class="[resolved.cardClasses, { 'is-headerless': !showHeader, 'is-plain': isPlain }]"
    :style="resolved.cardStyle"
  >
    <div v-if="showHeader" class="dashboard-widget-header" :class="resolved.headerClasses" :style="resolved.headerStyle">
      <span class="dashboard-widget-title" :style="resolved.titleStyle">
        <font-awesome-icon v-if="resolved.titleIcon" :icon="resolved.titleIcon" class="dashboard-widget-title-icon" />
        {{ widget.title || '' }}
      </span>
      <el-tag v-if="loading" size="small" type="info">加载中</el-tag>
      <el-tag v-else-if="error" size="small" type="danger">加载失败</el-tag>
    </div>
    <div class="dashboard-widget-body" :style="resolved.bodyStyle">
      <div v-if="error" class="dashboard-widget-error">
        <div class="dashboard-widget-error-title">数据加载失败</div>
        <div class="dashboard-widget-error-detail">{{ error }}</div>
      </div>
      <EChart
        v-else-if="widget.type === 'echarts'"
        :option="option"
        :key="chartKey"
        :theme="theme"
        :palette="palette"
        :clickable="canDrill"
        @click="onChartClick"
      />
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
      <TitleWidget v-else-if="widget.type === 'title'" :widget="widget" />
      <ClockWidget v-else-if="widget.type === 'clock'" :widget="widget" />
      <div v-else-if="widget.type === 'container'" class="dashboard-widget-container">
        <slot name="container" />
      </div>
      <div v-else class="dashboard-widget-empty">不支持的组件类型：{{ widget.type }}</div>
    </div>
  </div>

  <DashboardDrillDialog
    v-if="drillLoader"
    v-model="drillVisible"
    :widget="widget"
    :dimensions="drillDimensions"
    :loader="drillLoader"
    :chart-result="result"
    :theme="theme"
  />
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';

import ClockWidget from './ClockWidget.vue';
import DashboardDrillDialog from './DashboardDrillDialog.vue';
import DataTableWidget from './DataTableWidget.vue';
import EChart from './EChart.vue';
import { isDrillableWidget, resolveDrillDimensions } from './drill-utils';
import HtmlWidget from './HtmlWidget.vue';
import KpiWidget from './KpiWidget.vue';
import PivotWidget from './PivotWidget.vue';
import TitleWidget from './TitleWidget.vue';
import { applyChartStyle } from './chart-style';
import { buildEchartsOption } from './spec-to-option';
import type {
  ChartType,
  DashboardTheme,
  DashboardWidget,
  DatasetResult,
  DrillDimension,
  TablePageLoader,
  ThemeMapTokens,
  WidgetDrillLoader,
} from './types';
import { resolveWidgetStyle } from './widget-style';

const props = defineProps<{
  widget: DashboardWidget;
  result?: DatasetResult;
  loading?: boolean;
  error?: string;
  theme?: DashboardTheme;
  /** 主题序列色。 */
  palette?: string[];
  /** 地图主题色（区域/边界/高亮/标签）。 */
  mapTheme?: ThemeMapTokens;
  /** 全量数据集结果，供地图多图层按 datasetId 取数。 */
  datasetData?: Record<string, DatasetResult>;
  serverPaged?: boolean;
  pageLoader?: TablePageLoader;
  pageResetKey?: number;
  drillLoader?: WidgetDrillLoader;
}>();

const option = computed(() => {
  const built = buildEchartsOption(props.widget, props.result, props.datasetData, {
    mapTheme: props.mapTheme,
    palette: props.palette,
  });
  if (!props.widget.style?.chart) {
    return built;
  }
  const chartType = (props.widget.chart?.type ?? built.series?.[0]?.type ?? 'bar') as ChartType;
  return applyChartStyle(built, {
    chartType,
    style: props.widget.style,
    palette: props.palette ?? [],
    dark: props.theme === 'dark',
  });
});
const resolved = computed(() => resolveWidgetStyle(props.widget.style));
/** 图表类型切换时强制重建 ECharts，避免增量合并残留旧 series。 */
const chartKey = computed(() => `${props.widget.id}:${props.widget.chart?.type ?? 'echarts'}`);

/**
 * 标题栏显隐：显式配置优先；默认 HTML 组件无标题时不渲染，其余组件始终渲染。
 */
const showHeader = computed(() => {
  // 标题栏与日期时间组件自带内容，不渲染通用 header。
  if (props.widget.type === 'title' || props.widget.type === 'clock') {
    return false;
  }
  const explicit = props.widget.style?.title?.show;
  if (explicit === false) {
    return false;
  }
  if (explicit === true) {
    return true;
  }
  return props.widget.type !== 'html' || !!props.widget.title?.trim();
});

/** 标题栏 / 日期时间 / 容器没有内容区留白需求。 */
const isPlain = computed(() => props.widget.type === 'title' || props.widget.type === 'clock' || props.widget.type === 'container');

const canDrill = computed(() => !!props.drillLoader && isDrillableWidget(props.widget));
const drillVisible = ref(false);
const drillDimensions = ref<DrillDimension[]>([]);

const onChartClick = (params: Record<string, any>) => {
  if (!props.drillLoader) {
    return;
  }
  const dimensions = resolveDrillDimensions(params, props.widget, option.value);
  if (!dimensions) {
    return;
  }
  drillDimensions.value = dimensions;
  drillVisible.value = true;
};
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
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--dashboard-border, #f0f0f0);
}

.dashboard-widget-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--dashboard-text, #303133);
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-widget-title-icon {
  margin-right: 6px;
  opacity: 0.9;
}

.dashboard-widget-body {
  flex: 1;
  min-height: 0;
  padding: 8px 12px 12px;
}

.dashboard-widget-card.is-headerless .dashboard-widget-body {
  padding: 4px 8px;
}

.dashboard-widget-card.is-plain .dashboard-widget-body {
  padding: 0;
}

.dashboard-widget-container {
  height: 100%;
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
