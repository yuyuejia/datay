import * as echarts from 'echarts/core';
import {
  BarChart,
  BoxplotChart,
  FunnelChart,
  GaugeChart,
  GraphChart,
  HeatmapChart,
  LineChart,
  PieChart,
  RadarChart,
  SankeyChart,
  ScatterChart,
  TreemapChart,
} from 'echarts/charts';
import {
  DataZoomComponent,
  DatasetComponent,
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  RadarComponent,
  TitleComponent,
  ToolboxComponent,
  TooltipComponent,
  VisualMapComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

/**
 * ECharts 按需注册。
 *
 * 作为共享模块被主应用设计页与独立看板页共同引用，构建时自动抽取为公共 chunk，
 * 避免两个入口重复打包。
 */
echarts.use([
  LineChart,
  BarChart,
  PieChart,
  ScatterChart,
  GaugeChart,
  FunnelChart,
  RadarChart,
  HeatmapChart,
  TreemapChart,
  GraphChart,
  SankeyChart,
  BoxplotChart,
  GridComponent,
  RadarComponent,
  VisualMapComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DatasetComponent,
  DataZoomComponent,
  ToolboxComponent,
  MarkLineComponent,
  CanvasRenderer,
]);

const darkText = '#cbd5e1';
const darkSecondaryText = '#94a3b8';
const darkLine = '#475569';
const darkSplitLine = '#334155';

/** 看板大屏暗色主题：与 dashboard-theme-dark 的卡片配色保持一致。 */
echarts.registerTheme('dashboard-dark', {
  darkMode: true,
  backgroundColor: 'transparent',
  color: ['#60a5fa', '#34d399', '#fbbf24', '#f87171', '#a78bfa', '#22d3ee', '#fb923c', '#e879f9'],
  textStyle: { color: darkText },
  title: { textStyle: { color: darkText } },
  legend: { textStyle: { color: darkText } },
  tooltip: {
    backgroundColor: '#1e293b',
    borderColor: '#334155',
    textStyle: { color: '#e5e7eb' },
  },
  axisPointer: {
    lineStyle: { color: darkLine },
    crossStyle: { color: darkLine },
    label: { backgroundColor: '#1e293b', color: '#e5e7eb' },
  },
  dataZoom: {
    borderColor: darkLine,
    fillerColor: 'rgba(96, 165, 250, 0.15)',
    handleStyle: { color: '#60a5fa' },
    textStyle: { color: darkSecondaryText },
  },
  categoryAxis: {
    axisLine: { lineStyle: { color: darkLine } },
    axisLabel: { color: darkSecondaryText },
    splitLine: { show: false },
  },
  valueAxis: {
    axisLine: { lineStyle: { color: darkLine } },
    axisLabel: { color: darkSecondaryText },
    splitLine: { lineStyle: { color: darkSplitLine } },
  },
});

export { echarts };
export type ECharts = echarts.ECharts;
