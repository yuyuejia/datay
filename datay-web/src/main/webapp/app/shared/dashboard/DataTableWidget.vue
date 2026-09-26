<template>
  <el-table :data="rows" height="100%" size="small" border stripe>
    <el-table-column
      v-for="column in columns"
      :key="column"
      :prop="column"
      :label="columnLabel(column)"
      show-overflow-tooltip
      min-width="120"
    />
  </el-table>
</template>

<script setup lang="ts">
import { computed } from "vue";

import { resolveNameField } from "./spec-to-option";
import type { DashboardWidget, DatasetResult } from "./types";

const props = defineProps<{
  widget: DashboardWidget;
  result?: DatasetResult;
}>();

// 默认展示业务名称：存在对应 name 列时隐藏 id/sk 代理键列
const columns = computed(() => {
  const cols = props.result?.columns ?? [];
  return cols.filter((column) => {
    const lower = column.toLowerCase();
    if (!(lower.endsWith("_id") || lower.endsWith("_sk"))) {
      return true;
    }
    return resolveNameField(column, cols) === column;
  });
});
const rows = computed(() => props.result?.rows ?? []);

// 列名优先显示字段描述，描述为空回退字段名
const columnLabel = (column: string) =>
  props.result?.columnLabels?.[column] || column;
</script>

<style scoped>
:deep(.el-table) {
  height: 100%;
}
</style>
