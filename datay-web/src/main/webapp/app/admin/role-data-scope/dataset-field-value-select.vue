<template>
  <el-select
    :model-value="modelValue"
    :multiple="multiple"
    filterable
    remote
    reserve-keyword
    allow-create
    default-first-option
    clearable
    :remote-method="onSearch"
    :loading="loading"
    :disabled="disabled"
    :placeholder="placeholder"
    class="dataset-field-value-select"
    @update:model-value="onUpdate"
    @visible-change="onVisibleChange"
  >
    <el-option v-for="value in options" :key="value" :label="value" :value="value" />
  </el-select>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';

import RoleDatasetScopeService from './role-dataset-scope.service';

const props = withDefaults(
  defineProps<{
    modelValue?: string | string[];
    datasetId?: string;
    fieldName?: string;
    multiple?: boolean;
    placeholder?: string;
    disabled?: boolean;
    size?: number;
  }>(),
  {
    modelValue: '',
    datasetId: '',
    fieldName: '',
    multiple: false,
    placeholder: '请选择或输入',
    disabled: false,
    size: 30,
  },
);

const emit = defineEmits<{
  'update:modelValue': [value: string | string[]];
}>();

const service = new RoleDatasetScopeService();

const options = ref<string[]>([]);
const loading = ref(false);
const opened = ref(false);
let debounceTimer: ReturnType<typeof setTimeout> | null = null;
let requestSeq = 0;

const selectedValues = (): string[] => {
  if (Array.isArray(props.modelValue)) {
    return props.modelValue;
  }
  if (props.modelValue === undefined || props.modelValue === null || props.modelValue === '') {
    return [];
  }
  return [String(props.modelValue)];
};

/** 保留已选值，避免编辑时已选值不在搜索结果中而显示为空。 */
const mergeSelected = (values: string[]) => Array.from(new Set([...selectedValues(), ...values]));

const fetchValues = async (keyword?: string) => {
  if (props.disabled || !props.datasetId || !props.fieldName) {
    options.value = mergeSelected([]);
    return;
  }
  loading.value = true;
  const seq = ++requestSeq;
  try {
    const data = await service.pageFieldValues(props.datasetId, props.fieldName, keyword ?? '', 1, props.size);
    if (seq !== requestSeq) {
      return;
    }
    options.value = mergeSelected(data.values ?? []);
  } catch {
    if (seq === requestSeq) {
      options.value = mergeSelected([]);
    }
  } finally {
    if (seq === requestSeq) {
      loading.value = false;
    }
  }
};

const onSearch = (keyword: string) => {
  if (debounceTimer) {
    clearTimeout(debounceTimer);
  }
  debounceTimer = setTimeout(() => fetchValues(keyword), 300);
};

const onVisibleChange = (visible: boolean) => {
  opened.value = visible;
  if (visible) {
    fetchValues('');
  }
};

const onUpdate = (value: string | string[] | undefined) => {
  if (props.multiple) {
    emit('update:modelValue', Array.isArray(value) ? value : []);
    return;
  }
  emit('update:modelValue', Array.isArray(value) ? (value[0] ?? '') : (value ?? ''));
};

watch(
  () => [props.datasetId, props.fieldName],
  () => {
    // 保留已选值用于回显；字段变化后仅在面板打开时立即刷新候选值
    options.value = mergeSelected([]);
    if (opened.value && props.datasetId && props.fieldName) {
      fetchValues('');
    }
  },
);

watch(
  () => props.modelValue,
  () => {
    options.value = mergeSelected(options.value);
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  if (debounceTimer) {
    clearTimeout(debounceTimer);
  }
});
</script>

<style scoped>
.dataset-field-value-select {
  width: 100%;
}
</style>
