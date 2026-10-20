<template>
  <LineageGraph
    :nodes="nodes"
    :edges="edges"
    :loading="loading"
    :error="error"
    empty-text="暂无血缘关系"
    :root-id="`model:${modelId}`"
    root-label="当前模型"
    @refresh="load"
    @node-click="onNodeClick"
  />
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';

import LineageGraph from '@/shared/lineage/LineageGraph.vue';
import DataModelService from './data-model.service';

const props = defineProps<{
  modelId: string;
}>();

const emit = defineEmits<{
  'select-model': [modelId: string];
}>();

const dataModelService = new DataModelService();
const loading = ref(false);
const error = ref('');
const nodes = ref<any[]>([]);
const edges = ref<any[]>([]);

const load = async () => {
  if (!props.modelId) {
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const lineage = await dataModelService.lineage(props.modelId);
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

const onNodeClick = (node: any) => {
  if (node?.type !== 'MODEL') {
    return;
  }
  const modelId = String(node.id || '').replace(/^model:/, '');
  if (modelId && modelId !== props.modelId) {
    emit('select-model', modelId);
  }
};

watch(
  () => props.modelId,
  () => load(),
  { immediate: true },
);
</script>
