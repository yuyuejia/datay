<template>
  <div v-if="filters.length" class="dashboard-filters">
    <div
      v-for="filter in filters"
      :key="filter.id"
      class="dashboard-filter-item"
    >
      <span class="dashboard-filter-label">{{
        filter.label || filter.id
      }}</span>

      <div
        v-if="filter.type === 'dateRange'"
        class="dashboard-filter-date-range"
      >
        <input
          type="date"
          class="form-control form-control-sm dashboard-filter-date"
          :value="dateValue(filter, 'start')"
          @change="(e) => onDateChange(filter, 'start', e.target.value)"
        />
        <span class="dashboard-filter-range-sep">~</span>
        <input
          type="date"
          class="form-control form-control-sm dashboard-filter-date"
          :value="dateValue(filter, 'end')"
          @change="(e) => onDateChange(filter, 'end', e.target.value)"
        />
      </div>

      <el-select
        v-else-if="filter.type === 'select' || filter.type === 'multiSelect'"
        :multiple="filter.type === 'multiSelect'"
        :model-value="selectValue(filter)"
        filterable
        clearable
        collapse-tags
        placeholder="全部"
        :loading="loadingOptions[filter.id]"
        @update:model-value="(value) => onSelectChange(filter, value)"
        @visible-change="(visible) => visible && loadOptions(filter)"
      >
        <el-option
          v-for="option in optionsMap[filter.id] || []"
          :key="option"
          :label="option"
          :value="option"
        />
      </el-select>

      <el-input
        v-else-if="filter.type === 'input'"
        :model-value="local[filter.id] ?? ''"
        clearable
        placeholder="请输入"
        @update:model-value="(value) => onInputChange(filter, value)"
      />

      <div
        v-else-if="filter.type === 'numberRange'"
        class="dashboard-filter-range"
      >
        <el-input-number
          :model-value="numberValue(filter, 'min')"
          :controls="false"
          placeholder="最小值"
          @update:model-value="(value) => onNumberChange(filter, 'min', value)"
        />
        <span class="dashboard-filter-range-sep">~</span>
        <el-input-number
          :model-value="numberValue(filter, 'max')"
          :controls="false"
          placeholder="最大值"
          @update:model-value="(value) => onNumberChange(filter, 'max', value)"
        />
      </div>
    </div>

    <el-button text type="primary" @click="reset">重置</el-button>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from "vue";

import DashboardService from "./dashboard.service";
import type { DashboardFilter, DashboardSpec, FilterValues } from "./types";

const props = defineProps<{
  filters: DashboardFilter[];
  modelValue: FilterValues;
  dashboardId?: number;
  spec?: DashboardSpec;
}>();

const emit = defineEmits<{ "update:modelValue": [FilterValues] }>();

const service = new DashboardService();
const local = ref<FilterValues>({ ...(props.modelValue || {}) });
const optionsMap = ref<Record<string, string[]>>({});
const loadingOptions = ref<Record<string, boolean>>({});

const defaults = (): FilterValues => {
  const values: FilterValues = {};
  for (const filter of props.filters) {
    values[filter.id] = filter.defaultValue ?? defaultForType(filter);
  }
  return values;
};

const defaultForType = (filter: DashboardFilter) => {
  if (filter.type === "multiSelect") {
    return [];
  }
  if (filter.type === "dateRange" || filter.type === "numberRange") {
    return null;
  }
  return undefined;
};

const emitChange = () => emit("update:modelValue", { ...local.value });

const dateValue = (filter: DashboardFilter, key: "start" | "end"): string => {
  const value = local.value[filter.id];
  if (Array.isArray(value)) {
    return (key === "start" ? value[0] : value[1]) || "";
  }
  if (value && typeof value === "object") {
    return value[key] || "";
  }
  return "";
};

const onDateChange = (
  filter: DashboardFilter,
  key: "start" | "end",
  value: string,
) => {
  const current = local.value[filter.id];
  const next =
    current && typeof current === "object" && !Array.isArray(current)
      ? { ...current }
      : { start: "", end: "" };
  next[key] = value || "";
  local.value[filter.id] = next.start || next.end ? next : null;
  emitChange();
};

const selectValue = (filter: DashboardFilter): any =>
  local.value[filter.id] ?? (filter.type === "multiSelect" ? [] : undefined);
const numberValue = (
  filter: DashboardFilter,
  key: "min" | "max",
): number | undefined => {
  const value = local.value[filter.id];
  return value && typeof value === "object" ? value[key] : undefined;
};

const onSelectChange = (filter: DashboardFilter, value: any) => {
  local.value[filter.id] = value;
  emitChange();
};

const onInputChange = (filter: DashboardFilter, value: string) => {
  local.value[filter.id] = value;
  emitChange();
};

const onNumberChange = (
  filter: DashboardFilter,
  key: "min" | "max",
  value: number | undefined,
) => {
  const current = local.value[filter.id];
  const next =
    current && typeof current === "object"
      ? { ...current }
      : { min: undefined, max: undefined };
  next[key] = value;
  local.value[filter.id] = next;
  emitChange();
};

const loadOptions = async (filter: DashboardFilter) => {
  if (filter.type !== "select" && filter.type !== "multiSelect") {
    return;
  }
  const mode = filter.options?.mode ?? "static";
  if (mode !== "dimension" || (!props.dashboardId && !props.spec)) {
    optionsMap.value[filter.id] = filter.options?.values ?? [];
    return;
  }
  loadingOptions.value[filter.id] = true;
  try {
    const result = props.dashboardId
      ? await service.loadFilterOptions(props.dashboardId, filter.id)
      : await service.previewFilterOptions(
          props.spec as DashboardSpec,
          filter.id,
        );
    optionsMap.value[filter.id] = result.values ?? [];
  } catch {
    optionsMap.value[filter.id] = filter.options?.values ?? [];
  } finally {
    loadingOptions.value[filter.id] = false;
  }
};

const reset = () => {
  local.value = defaults();
  emitChange();
};

watch(
  () => props.modelValue,
  (value) => {
    local.value = { ...(value || {}) };
  },
  { deep: true },
);

onMounted(() => {
  for (const filter of props.filters) {
    loadOptions(filter);
  }
});
</script>

<style scoped>
.dashboard-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 20px;
  padding: 12px 16px;
  margin-bottom: 16px;
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 1px 4px rgb(0 0 0 / 8%);
}

.dashboard-filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dashboard-filter-label {
  color: #606266;
  font-size: 13px;
  white-space: nowrap;
}

.dashboard-filter-range {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dashboard-filter-date-range {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dashboard-filter-date {
  width: 150px;
}

.dashboard-filter-range-sep {
  color: #909399;
}

.dashboard-filter-range :deep(.el-input-number) {
  width: 110px;
}
</style>
