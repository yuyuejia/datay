<template>
  <div ref="viewportRef" class="dashboard-stage-viewport" :class="`fit-${stageMode}`" :style="viewportStyle">
    <div class="dashboard-stage-frame" :style="frameStyle">
      <div class="dashboard-stage" :style="stageStyle">
        <slot :scale="scaleX" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { clamp } from './designer/grid-layout';
import type { ScreenFitMode } from './types';

type StageMode = ScreenFitMode | 'manual';

/**
 * 固定分辨率画布舞台。
 *
 * <p>画布按逻辑分辨率（如 1920×1080）渲染，再通过 CSS transform 缩放到当前可视区。
 * 缩放使用 transform 而不是修改布局尺寸，保证网格坐标、ECharts 尺寸始终以设计分辨率为准。
 * 设计器传入数字 zoom 时使用可滚动的编辑模式；浏览页传入 fit 时按 fitMode 适配屏幕。
 */
const props = withDefaults(
  defineProps<{
    width: number;
    height: number;
    /** 'fit' 自动适应可视区；数字表示固定缩放比例（1 = 100%），用于设计器。 */
    zoom?: number | 'fit';
    /** 可视区四周留白，参与 fit 比例计算。 */
    padding?: number;
    /** 大屏在浏览页的适配方式；设计器手动缩放时忽略。 */
    fitMode?: ScreenFitMode;
    /** 固定分辨率画布的背景样式（纯色 / 渐变 / 图片）。 */
    backgroundStyle?: Record<string, string>;
  }>(),
  {
    zoom: 'fit',
    padding: 16,
    fitMode: 'stretch',
    backgroundStyle: undefined,
  },
);

const emit = defineEmits<{
  'scale-change': [number];
}>();

const viewportRef = ref<HTMLElement | null>(null);
const viewportWidth = ref(0);
const viewportHeight = ref(0);
let observer: ResizeObserver | null = null;

const MIN_SCALE = 0.05;
const MAX_SCALE = 4;

const stageMode = computed<StageMode>(() => (typeof props.zoom === 'number' ? 'manual' : props.fitMode));

const availableWidth = computed(() => viewportWidth.value - props.padding * 2);
const availableHeight = computed(() => viewportHeight.value - props.padding * 2);
const hasViewport = computed(() => availableWidth.value > 0 && availableHeight.value > 0 && props.width > 0 && props.height > 0);

const scaleX = computed(() => {
  if (!hasViewport.value) {
    return MIN_SCALE;
  }
  if (typeof props.zoom === 'number') {
    return clamp(props.zoom, MIN_SCALE, MAX_SCALE);
  }
  const ratioX = availableWidth.value / props.width;
  const ratioY = availableHeight.value / props.height;
  switch (props.fitMode) {
    case 'cover':
      return clamp(Math.max(ratioX, ratioY), MIN_SCALE, MAX_SCALE);
    case 'stretch':
      return clamp(ratioX, MIN_SCALE, MAX_SCALE);
    case 'contain':
    default:
      return clamp(Math.min(ratioX, ratioY), MIN_SCALE, MAX_SCALE);
  }
});

const scaleY = computed(() => {
  if (!hasViewport.value) {
    return MIN_SCALE;
  }
  if (props.fitMode === 'stretch' && typeof props.zoom !== 'number') {
    return clamp(availableHeight.value / props.height, MIN_SCALE, MAX_SCALE);
  }
  return scaleX.value;
});

const viewportStyle = computed(() => ({
  padding: `${props.padding}px`,
}));

const frameStyle = computed(() => {
  if (stageMode.value === 'stretch') {
    return {
      width: `${Math.max(1, availableWidth.value)}px`,
      height: `${Math.max(1, availableHeight.value)}px`,
    };
  }
  return {
    width: `${Math.max(1, props.width * scaleX.value)}px`,
    height: `${Math.max(1, props.height * scaleY.value)}px`,
  };
});

const stageStyle = computed(() => ({
  width: `${props.width}px`,
  height: `${props.height}px`,
  transform: `scale(${scaleX.value}, ${scaleY.value})`,
  transformOrigin: 'top left',
  ...(props.backgroundStyle ?? { backgroundColor: '#f5f7fa' }),
}));

const measure = () => {
  const el = viewportRef.value;
  if (!el) {
    return;
  }
  viewportWidth.value = el.clientWidth;
  viewportHeight.value = el.clientHeight;
};

onMounted(() => {
  measure();
  if (viewportRef.value && typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(measure);
    observer.observe(viewportRef.value);
  }
});

onBeforeUnmount(() => {
  observer?.disconnect();
  observer = null;
});

watch(scaleX, value => emit('scale-change', value), { immediate: true });
</script>

<style scoped>
.dashboard-stage-viewport {
  position: relative;
  box-sizing: border-box;
  width: 100%;
  height: 100%;
}

.dashboard-stage-frame {
  flex: 0 0 auto;
}

.dashboard-stage {
  overflow: hidden;
}

/* 等比适应 / 设计器手动缩放 */
.fit-contain,
.fit-manual {
  display: flex;
}

.fit-contain {
  overflow: hidden;
}

.fit-manual {
  overflow: auto;
}

.fit-contain .dashboard-stage-frame,
.fit-manual .dashboard-stage-frame {
  margin: auto;
}

/* 裁切：等比铺满，左侧/右侧居中裁切，顶部对齐避免裁掉顶部留白 */
.fit-cover {
  overflow: hidden;
}

.fit-cover .dashboard-stage-frame {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
}

/* 拉伸铺满：横向和纵向分别缩放 */
.fit-stretch {
  overflow: hidden;
}
</style>
