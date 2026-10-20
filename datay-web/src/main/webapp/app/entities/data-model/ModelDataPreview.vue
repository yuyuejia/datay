<template>
  <div class="model-data-preview">
    <div class="preview-toolbar">
      <div class="preview-meta">
        <span class="preview-table-name">{{ displayTable || '-' }}</span>
        <span v-if="dataSourceName" class="preview-datasource">{{ dataSourceName }}</span>
        <span v-if="loading" class="preview-status">加载中…</span>
        <span v-else-if="!error && columns.length" class="preview-status"> {{ rows.length }} 行 · {{ columns.length }} 列 </span>
      </div>
      <div class="preview-actions">
        <el-select v-model="limit" size="small" class="limit-select" @change="load">
          <el-option :value="10" label="10 行" />
          <el-option :value="50" label="50 行" />
          <el-option :value="100" label="100 行" />
        </el-select>
        <el-button size="small" text :loading="loading" @click="load">
          <font-awesome-icon icon="sync" />
          <span>刷新</span>
        </el-button>
      </div>
    </div>

    <el-empty v-if="!hasPhysicalTable" description="模型尚未绑定物理表，请先物化或使用注册模式" />
    <el-alert v-else-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-table v-else v-loading="loading" :data="rows" stripe border size="small" height="420" style="width: 100%">
      <el-table-column v-for="col in columns" :key="col" :label="col" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ formatCell(row[col]) }}</template>
      </el-table-column>
      <template #empty>
        <el-empty description="查询成功，无数据返回" />
      </template>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import DataModelService from './data-model.service';
import type { IDataModel } from '@/shared/model/data-model.model';

const props = defineProps<{
  model: IDataModel | null;
}>();

const dataModelService = new DataModelService();
const limit = ref(100);
const loading = ref(false);
const error = ref('');
const columns = ref<string[]>([]);
const rows = ref<Record<string, any>[]>([]);
const displayTable = ref('');
const dataSourceName = ref('');

const hasPhysicalTable = computed(() => !!props.model?.dataSourceId && !!props.model?.tableName);

const load = async () => {
  const modelId = props.model?.id;
  if (!modelId || !hasPhysicalTable.value) {
    columns.value = [];
    rows.value = [];
    error.value = '';
    displayTable.value = '';
    dataSourceName.value = '';
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const res = await dataModelService.preview(modelId, limit.value);
    columns.value = res?.columns || [];
    rows.value = res?.rows || [];
    displayTable.value = res?.table || '';
    dataSourceName.value = res?.dataSourceName || '';
  } catch (err: any) {
    error.value = err?.response?.data?.message || err?.response?.data?.detail || err?.message || '数据预览加载失败';
    columns.value = [];
    rows.value = [];
  } finally {
    loading.value = false;
  }
};

const formatCell = (value: any) => {
  if (value === null || value === undefined) {
    return '-';
  }
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value);
    } catch {
      return String(value);
    }
  }
  return value;
};

watch(
  () => [props.model?.id, props.model?.dataSourceId, props.model?.schemaName, props.model?.tableName],
  () => load(),
  { immediate: true },
);
</script>

<style scoped>
.model-data-preview {
  min-height: 420px;
}

.preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.preview-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.preview-table-name {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace;
  font-size: 13px;
  color: #303133;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 3px;
  padding: 2px 8px;
  word-break: break-all;
}

.preview-datasource,
.preview-status {
  font-size: 13px;
  color: #909399;
}

.preview-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.limit-select {
  width: 96px;
}
</style>
