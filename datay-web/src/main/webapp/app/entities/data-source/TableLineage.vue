<template>
  <LineageGraph
    :nodes="nodes"
    :edges="edges"
    :loading="loading"
    :error="error"
    :root-id="rootId"
    root-label="当前表"
    empty-text="暂无血缘关系"
    @refresh="load"
  />
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';

import LineageGraph from '@/shared/lineage/LineageGraph.vue';
import DataSourceService from './data-source.service';

const props = defineProps<{
  dataSourceId: string;
  schema: string;
  table: string;
}>();

const dataSourceService = new DataSourceService();
const loading = ref(false);
const error = ref('');
const nodes = ref<any[]>([]);
const edges = ref<any[]>([]);
const rootId = ref('');

const load = async () => {
  if (!props.dataSourceId || !props.table) {
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const lineage = await dataSourceService.getTableLineage(props.dataSourceId, props.schema || '', props.table);
    nodes.value = lineage?.nodes || [];
    edges.value = lineage?.edges || [];
    rootId.value = lineage?.currentTableId || '';
  } catch (err: any) {
    error.value = err?.response?.data?.message || err?.response?.data?.detail || err?.message || '血缘加载失败';
    nodes.value = [];
    edges.value = [];
    rootId.value = '';
  } finally {
    loading.value = false;
  }
};

watch(
  () => [props.dataSourceId, props.schema, props.table],
  () => load(),
  { immediate: true },
);
</script>
