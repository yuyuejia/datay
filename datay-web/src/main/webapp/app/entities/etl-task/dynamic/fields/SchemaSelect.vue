<template>
  <el-select
    :model-value="modelValue || ''"
    :placeholder="placeholder || '请选择 Schema'"
    :loading="loading"
    :clearable="!required"
    filterable
    allow-create
    default-first-option
    class="schema-select"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-option v-for="schema in schemas" :key="schema" :value="schema" :label="schema" />
  </el-select>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElMessage } from 'element-plus';

import DataSourceService from '@/entities/data-source/data-source.service';

const props = defineProps<{
  dataSourceId?: string | null;
  modelValue?: string | null;
  placeholder?: string;
  required?: boolean;
}>();

const emit = defineEmits(['update:modelValue']);

const dataSourceService = new DataSourceService();
const schemas = ref<string[]>([]);
const loading = ref(false);
let requestId = 0;

const fetchSchemas = async () => {
  const currentRequestId = ++requestId;
  if (!props.dataSourceId) {
    schemas.value = [];
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    const res = await dataSourceService.getSchemas(String(props.dataSourceId));
    if (currentRequestId !== requestId) {
      return;
    }
    schemas.value = Array.isArray(res.data) ? res.data : [];
  } catch (error) {
    if (currentRequestId !== requestId) {
      return;
    }
    schemas.value = [];
    console.error('获取 Schema 失败', error);
    ElMessage.warning('获取 Schema 失败');
  } finally {
    if (currentRequestId === requestId) {
      loading.value = false;
    }
  }
};

watch(() => props.dataSourceId, fetchSchemas, { immediate: true });
</script>

<style scoped>
.schema-select {
  width: 100%;
}
</style>
