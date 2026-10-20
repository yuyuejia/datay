<template>
  <LineageGraph
    :nodes="nodes"
    :edges="edges"
    :loading="loading"
    :error="error"
    empty-text="暂无血缘关系"
    :root-id="`metric:${metricId}`"
    root-label="当前指标"
    @refresh="load"
    @node-click="node => emit('node-click', node)"
  />
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';

import LineageGraph from '@/shared/lineage/LineageGraph.vue';
import MetricService from './metric.service';

const props = defineProps<{
  metricId: string;
}>();

const emit = defineEmits<{
  'node-click': [node: any];
}>();

const metricService = new MetricService();
const loading = ref(false);
const error = ref('');
const nodes = ref<any[]>([]);
const edges = ref<any[]>([]);

const load = async () => {
  if (!props.metricId) {
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const lineage = await metricService.lineage(props.metricId);
    nodes.value = lineage?.nodes || [];
    edges.value = lineage?.edges || [];
  } catch (err: any) {
    error.value = err?.response?.data?.message || err?.response?.data?.detail || err?.message || '血缘加载失败';
    nodes.value = [];
    edges.value = [];
  } finally {
    loading.value = false;
  }
};

watch(
  () => props.metricId,
  () => load(),
  { immediate: true },
);
</script>
