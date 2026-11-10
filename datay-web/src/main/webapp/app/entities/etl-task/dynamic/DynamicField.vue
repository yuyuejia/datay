<template>
  <el-input
    v-if="fieldType === 'INPUT'"
    :model-value="modelValue ?? ''"
    :placeholder="field.placeholder || ''"
    :type="field.secret ? 'password' : 'text'"
    :show-password="field.secret"
    clearable
    @update:model-value="emitValue"
  />

  <el-input
    v-else-if="fieldType === 'PASSWORD'"
    :model-value="modelValue ?? ''"
    :placeholder="field.placeholder || ''"
    type="password"
    show-password
    clearable
    @update:model-value="emitValue"
  />

  <el-input
    v-else-if="fieldType === 'TEXTAREA'"
    :model-value="modelValue ?? ''"
    :placeholder="field.placeholder || ''"
    :rows="4"
    type="textarea"
    @update:model-value="emitValue"
  />

  <el-input-number
    v-else-if="fieldType === 'NUMBER'"
    :model-value="numberValue"
    :min="field.min ?? undefined"
    :max="field.max ?? undefined"
    :step="field.step ?? 1"
    :controls="false"
    :placeholder="field.placeholder || ''"
    class="dynamic-number-input"
    @update:model-value="emitValue"
  />

  <el-select
    v-else-if="fieldType === 'SELECT'"
    :model-value="modelValue ?? ''"
    :placeholder="field.placeholder || '请选择'"
    :clearable="!field.required"
    filterable
    class="dynamic-select"
    @update:model-value="emitValue"
  >
    <el-option v-for="option in options" :key="option.value" :value="option.value" :label="option.label || option.value">
      <span>{{ option.label || option.value }}</span>
      <small v-if="option.description" class="dynamic-option-desc">{{ option.description }}</small>
    </el-option>
  </el-select>

  <el-select
    v-else-if="fieldType === 'MULTI_SELECT'"
    :model-value="arrayValue"
    :placeholder="field.placeholder || '请选择'"
    :multiple="true"
    filterable
    collapse-tags
    collapse-tags-tooltip
    clearable
    class="dynamic-select"
    @update:model-value="emitValue"
  >
    <el-option v-for="option in options" :key="option.value" :value="option.value" :label="option.label || option.value">
      <span>{{ option.label || option.value }}</span>
      <small v-if="option.description" class="dynamic-option-desc">{{ option.description }}</small>
    </el-option>
  </el-select>

  <el-radio-group v-else-if="fieldType === 'RADIO'" :model-value="modelValue" @update:model-value="emitValue">
    <el-radio v-for="option in options" :key="option.value" :value="option.value">
      {{ option.label || option.value }}
    </el-radio>
  </el-radio-group>

  <el-checkbox-group v-else-if="fieldType === 'CHECKBOX_GROUP'" :model-value="arrayValue" @update:model-value="emitValue">
    <el-checkbox v-for="option in options" :key="option.value" :value="option.value">
      {{ option.label || option.value }}
    </el-checkbox>
  </el-checkbox-group>

  <el-switch v-else-if="fieldType === 'SWITCH'" :model-value="modelValue === true" @update:model-value="emitValue" />

  <DataSourceSelector
    v-else-if="fieldType === 'DATASOURCE'"
    :datasource-id="modelValue || null"
    :schema="formValues[field.schemaField || 'schema'] || null"
    :data-source-type="field.dataSourceType || null"
    @selected="handleDataSourceSelected"
  />

  <SchemaSelect
    v-else-if="fieldType === 'SCHEMA'"
    :model-value="modelValue"
    :data-source-id="formValues[field.dataSourceField || 'sourceId'] || null"
    :placeholder="field.placeholder || '请选择 Schema'"
    @update:model-value="emitValue"
  />

  <DataSourceTableSelector
    v-else-if="fieldType === 'TABLE'"
    :key="`table-${formValues[field.dataSourceField || 'sourceId'] || ''}-${formValues[field.schemaField || 'schema'] || ''}`"
    :data-source-id="formValues[field.dataSourceField || 'sourceId'] || null"
    :schema="formValues[field.schemaField || 'schema'] || null"
    :selected-table="field.multiple ? null : modelValue || null"
    :selected-tables="tableSelection"
    :multiple="Boolean(field.multiple)"
    @selected="handleTableSelected"
  />

  <DataSourceTableFieldSelector
    v-else-if="fieldType === 'COLUMN'"
    :key="`column-${formValues[field.dataSourceField || 'sourceId'] || ''}-${formValues[field.schemaField || 'schema'] || ''}-${formValues[field.tableField || 'table'] || ''}`"
    :data-source-id="formValues[field.dataSourceField || 'sourceId'] || null"
    :schema="formValues[field.schemaField || 'schema'] || null"
    :table="formValues[field.tableField || 'table'] || null"
    :selected-fields="columnSelection"
    :selected-field="field.multiple ? null : modelValue || null"
    :multiple="Boolean(field.multiple)"
    @selected="handleColumnSelected"
  />

  <FileField
    v-else-if="fieldType === 'FILE'"
    :model-value="modelValue || ''"
    :placeholder="field.placeholder || '请选择文件'"
    @change="emitValue"
  />

  <component
    :is="customFieldComponent"
    v-else-if="fieldType === 'CUSTOM' && customFieldComponent"
    :model-value="modelValue"
    :field="field"
    :form-values="formValues"
    :component="component"
    @change="payload => emit('change', payload)"
  />

  <el-alert
    v-else-if="fieldType === 'CUSTOM'"
    :title="`未找到自定义字段组件：${field.customComponent || '-'}`"
    type="warning"
    :closable="false"
    show-icon
  />

  <el-input v-else :model-value="modelValue ?? ''" :placeholder="field.placeholder || ''" @update:model-value="emitValue" />
</template>

<script setup lang="ts">
import { computed } from 'vue';

import DataSourceSelector from '@/components/DataSourceSelector.vue';
import DataSourceTableFieldSelector from '@/components/DataSourceTableFieldSelector.vue';
import DataSourceTableSelector from '@/components/DataSourceTableSelector.vue';
import type { IETLComponentField } from '@/shared/model/etl-component.model';

import FileField from './fields/FileField.vue';
import SchemaSelect from './fields/SchemaSelect.vue';

const props = defineProps<{
  field: IETLComponentField;
  modelValue?: any;
  formValues: Record<string, any>;
  component?: any;
}>();

const emit = defineEmits(['change']);

interface FieldChangePayload {
  value: any;
  extra?: Record<string, any>;
}

const customFieldModules = import.meta.glob('./custom/*.vue', { eager: true }) as Record<string, any>;
const customFieldMap: Record<string, any> = {};
for (const [path, module] of Object.entries(customFieldModules)) {
  const filename = path
    .split('/')
    .pop()!
    .replace(/\.vue$/, '');
  customFieldMap[filename] = (module as any).default;
}

const fieldType = computed(() => (props.field.type || 'INPUT').toUpperCase());
const options = computed(() => props.field.options || []);
const arrayValue = computed(() => (Array.isArray(props.modelValue) ? props.modelValue : []));
const numberValue = computed(() => {
  if (props.modelValue === undefined || props.modelValue === null || props.modelValue === '') {
    return null;
  }
  const numberValue = Number(props.modelValue);
  return Number.isFinite(numberValue) ? numberValue : null;
});

const customFieldComponent = computed(() => {
  if (!props.field.customComponent) {
    return null;
  }
  return customFieldMap[props.field.customComponent] || null;
});

const tableSelection = computed(() => {
  if (!props.field.multiple) {
    return [];
  }
  const raw = props.modelValue;
  if (Array.isArray(raw)) {
    return raw.map(item => (typeof item === 'string' ? { table: item } : item)).filter(item => item?.table);
  }
  if (typeof raw === 'string' && raw.trim()) {
    return raw
      .split(',')
      .map(item => item.trim())
      .filter(Boolean)
      .map(table => ({ table }));
  }
  return [];
});

const columnSelection = computed(() => {
  if (!props.field.multiple) {
    return [];
  }
  const raw = props.modelValue;
  if (Array.isArray(raw)) {
    return raw.map(item => (typeof item === 'string' ? { name: item } : item)).filter(item => item?.name);
  }
  if (typeof raw === 'string' && raw.trim()) {
    return raw
      .split(',')
      .map(item => item.trim())
      .filter(Boolean)
      .map(name => ({ name }));
  }
  return [];
});

const emitChange = (payload: FieldChangePayload) => {
  emit('change', payload);
};

const emitValue = (value: any) => {
  emitChange({ value });
};

const handleDataSourceSelected = (selected: { dataSourceId?: string; schema?: string }) => {
  const schemaKey = props.field.schemaField || 'schema';
  emitChange({
    value: selected?.dataSourceId || '',
    extra: { [schemaKey]: selected?.schema || '' },
  });
};

const serializeSelection = (names: string[]): any => {
  const format = (props.field.valueFormat || 'AUTO').toUpperCase();
  return format === 'ARRAY' ? names : names.join(',');
};

const handleTableSelected = (selection: any) => {
  if (props.field.multiple) {
    const names = (Array.isArray(selection) ? selection : []).map((item: any) => item?.table).filter(Boolean);
    emitValue(serializeSelection(names));
    return;
  }
  emitValue(selection?.table || '');
};

const handleColumnSelected = (selection: any) => {
  if (props.field.multiple) {
    const names = (Array.isArray(selection) ? selection : []).map((item: any) => item?.name).filter(Boolean);
    emitValue(serializeSelection(names));
    return;
  }
  emitValue(selection?.name || '');
};
</script>

<style scoped>
.dynamic-select,
.dynamic-number-input {
  width: 100%;
}

.dynamic-option-desc {
  margin-left: 8px;
  color: var(--el-text-color-secondary, #909399);
}
</style>
