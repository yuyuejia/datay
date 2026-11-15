<template>
  <div class="data-map-graph" v-loading="loading">
    <div ref="container" class="data-map-graph-canvas"></div>
    <el-empty v-if="!loading && nodes.length === 0" class="data-map-graph-empty" description="暂无数据地图节点" />
    <div v-if="truncated" class="data-map-graph-truncated">
      <font-awesome-icon icon="exclamation-circle" />
      关系图节点较多，已按上限加载；请按数据源或类型缩小范围。
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { echarts } from '@/shared/dashboard/echarts';
import type { ECharts } from '@/shared/dashboard/echarts';
import { buildDataMapGraphOption } from './data-map-graph-option';
import type { DataMapEdge, DataMapNode } from './data-map.service';

const props = withDefaults(
  defineProps<{
    nodes: DataMapNode[];
    links: DataMapEdge[];
    categories: string[];
    loading?: boolean;
    truncated?: boolean;
    layout?: 'force' | 'circular';
    showLabels?: boolean;
    focusMode?: boolean;
    centerNodeId?: string;
  }>(),
  {
    loading: false,
    truncated: false,
    layout: 'force',
    showLabels: false,
    focusMode: false,
    centerNodeId: '',
  },
);

const emit = defineEmits<{
  'node-click': [node: DataMapNode];
  'node-dblclick': [node: DataMapNode];
}>();

const container = ref<HTMLElement | null>(null);
let chart: ECharts | null = null;
let observer: ResizeObserver | null = null;

const render = () => {
  if (!chart) {
    return;
  }
  chart.setOption(
    buildDataMapGraphOption({
      nodes: props.nodes || [],
      links: props.links || [],
      categories: props.categories || [],
      layout: props.layout,
      showLabels: props.showLabels,
      focusMode: props.focusMode,
    }),
    true,
  );
};

const initChart = () => {
  if (!container.value) {
    return;
  }
  chart?.dispose();
  chart = echarts.init(container.value, undefined, { renderer: 'canvas' });
  chart.on('click', (params: any) => {
    if (params?.dataType === 'node' && params.data?.raw) {
      emit('node-click', params.data.raw);
    }
  });
  chart.on('dblclick', (params: any) => {
    if (params?.dataType === 'node' && params.data?.raw) {
      emit('node-dblclick', params.data.raw);
    }
  });
  render();
};

/**
 * 把指定节点平移到画布中心。
 *
 * ECharts graph 使用 graphRoam 动作做视图平移；dx/dy 表示「视图中心需要移动到的
 * 屏幕位置」与当前中心的差值。力导向布局会持续迭代，因此调用方会做一次短延迟重试。
 */
const centerNode = (nodeId: string): boolean => {
  if (!chart || !nodeId) {
    return false;
  }
  const index = (props.nodes || []).findIndex(node => node.id === nodeId);
  if (index < 0) {
    return false;
  }
  try {
    const seriesModel = (chart as any).getModel?.()?.getSeriesByIndex?.(0);
    const data = seriesModel?.getData?.();
    let pixel: number[] | null = null;

    // 优先使用节点图形元素的全局坐标，力导向布局下比 getItemLayout 更可靠。
    const graphicEl = data?.getItemGraphicEl?.(index);
    if (graphicEl?.transformCoordToGlobal) {
      const global = graphicEl.transformCoordToGlobal(0, 0);
      if (global && Number.isFinite(global[0]) && Number.isFinite(global[1])) {
        pixel = [global[0], global[1]];
      }
    }

    if (!pixel) {
      const layout = data?.getItemLayout?.(index);
      if (!layout || !Number.isFinite(layout[0]) || !Number.isFinite(layout[1])) {
        return false;
      }
      const converted = chart.convertToPixel({ seriesIndex: 0 }, [layout[0], layout[1]]);
      if (converted && Number.isFinite(converted[0]) && Number.isFinite(converted[1])) {
        pixel = [converted[0], converted[1]];
      }
    }

    if (!pixel) {
      return false;
    }

    const coordSys = seriesModel?.coordinateSystem;
    const dataCoord = chart.convertFromPixel({ seriesIndex: 0 }, [pixel[0], pixel[1]]);
    if (dataCoord && coordSys?.setCenter) {
      // 把视图中心设置为节点所在的数据坐标，并同步 series 的 center 选项。
      seriesModel?.setCenter?.(dataCoord);
      coordSys.setCenter(dataCoord, chart);
    } else {
      // 兜底：使用 ECharts 的 roam 动作按屏幕偏移平移。
      chart.dispatchAction({
        type: 'graphRoam',
        seriesIndex: 0,
        dx: chart.getWidth() / 2 - pixel[0],
        dy: chart.getHeight() / 2 - pixel[1],
      });
    }

    // GraphView 的 group 变换是渲染时从坐标系统复制的；这里同步一次并刷新，
    // 保证节点在当前帧就移动到画布中心，而不是等下一次完整重绘。
    const view = (chart as any).getViewOfSeriesModel?.(seriesModel);
    const group = view?.group;
    if (group && coordSys) {
      group.attr({
        x: coordSys.x,
        y: coordSys.y,
        scaleX: coordSys.scaleX,
        scaleY: coordSys.scaleY,
      });
      (chart as any).getZr?.()?.refresh?.();
    }
    return true;
  } catch {
    return false;
  }
};

const centerNodeWithRetry = (nodeId: string) => {
  if (!nodeId) {
    return;
  }
  setTimeout(() => centerNode(nodeId), 60);
  setTimeout(() => centerNode(nodeId), 260);
};

const focusNode = (nodeId: string) => {
  if (!chart) {
    return;
  }
  const index = (props.nodes || []).findIndex(node => node.id === nodeId);
  if (index < 0) {
    return;
  }
  chart.dispatchAction({ type: 'downplay', seriesIndex: 0 });
  chart.dispatchAction({ type: 'highlight', seriesIndex: 0, dataIndex: index });
  chart.dispatchAction({ type: 'showTip', seriesIndex: 0, dataIndex: index });
  centerNodeWithRetry(nodeId);
};

const getChart = () => chart;

onMounted(() => {
  initChart();
  if (container.value) {
    observer = new ResizeObserver(() => chart?.resize());
    observer.observe(container.value);
  }
});

watch(
  () => [props.nodes, props.links, props.categories, props.layout, props.showLabels, props.focusMode],
  () => render(),
  { deep: true },
);

watch(
  () => [props.nodes, props.centerNodeId],
  async () => {
    if (!props.centerNodeId) {
      return;
    }
    await nextTick();
    centerNodeWithRetry(props.centerNodeId);
  },
  { deep: true },
);

onBeforeUnmount(() => {
  observer?.disconnect();
  observer = null;
  chart?.dispose();
  chart = null;
});

defineExpose({ focusNode, centerNode, getChart });
</script>

<style scoped>
.data-map-graph {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 480px;
}

.data-map-graph-canvas {
  width: 100%;
  height: 100%;
}

.data-map-graph-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.data-map-graph-truncated {
  position: absolute;
  right: 16px;
  bottom: 16px;
  padding: 6px 10px;
  border-radius: 6px;
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
  font-size: 12px;
  pointer-events: none;
}
</style>
