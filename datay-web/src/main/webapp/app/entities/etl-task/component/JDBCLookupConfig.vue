<template>
  <el-form name="editForm" label-position="top">
    <el-form-item label="关联数据源">
      <DataSourceSelector :datasourceId="formData.sourceId" :schema="formData.schema" @selected="handleDataSourceSelected" />
      <small class="form-text text-muted"> 查询另一个数据库中的表，可与上游数据源不同 </small>
    </el-form-item>

    <el-form-item label="关联表">
      <DataSourceTableSelector
        :key="tableSelectorKey"
        :dataSourceId="formData.sourceId"
        :schema="formData.schema"
        :selectedTable="formData.table || null"
        :selectedTables="[]"
        :multiple="false"
        @selected="handleTableSelected"
      />
    </el-form-item>

    <el-form-item label="关联条件">
      <div class="condition-block">
        <div class="condition-title">上游字段 = 关联表字段</div>
        <div class="mapping-row">
          <div class="field-with-action">
            <el-select v-model="formData.localField" placeholder="上游字段" filterable allow-create default-first-option>
              <el-option v-for="field in upstreamFieldOptions" :key="field.name" :value="field.name" :label="columnLabel(field)" />
            </el-select>
            <el-button size="small" :loading="loadingUpstream" @click="refreshUpstream(true)">调试上游</el-button>
          </div>
          <span class="mapping-arrow">=</span>
          <DataSourceTableFieldSelector
            :dataSourceId="formData.sourceId"
            :schema="formData.schema"
            :table="formData.table"
            :selectedFields="[]"
            :selectedField="formData.lookupField || ''"
            :multiple="false"
            @selected="handleLookupFieldSelected"
          />
        </div>
        <small class="form-text text-muted">
          {{ upstreamHint }}
        </small>
      </div>
    </el-form-item>

    <el-form-item label="回填字段">
      <DataSourceTableFieldSelector
        :key="outputFieldsSelectorKey"
        :dataSourceId="formData.sourceId"
        :schema="formData.schema"
        :table="formData.table"
        :selectedFields="selectedOutputFields"
        :selectedField="null"
        :multiple="true"
        @selected="handleOutputFieldsSelected"
      />
      <small class="form-text text-muted"> 选择需要回填的关联表字段；不选择时默认回填全部字段 </small>
    </el-form-item>

    <el-form-item label="关联类型">
      <el-select v-model="formData.joinType">
        <el-option value="LEFT" label="LEFT - 保留未匹配记录" />
        <el-option value="INNER" label="INNER - 仅保留匹配记录" />
      </el-select>
    </el-form-item>

    <el-form-item label="回填字段前缀">
      <el-input v-model="formData.outputPrefix" placeholder="例如 user_" />
      <small class="form-text text-muted">
        回填字段会以「前缀 + 关联表字段名」写入记录；留空则直接使用关联表字段名（同名字段会被覆盖）
      </small>
    </el-form-item>

    <el-form-item label="附加过滤条件">
      <el-input type="textarea" v-model="formData.where" :rows="2" placeholder="例如 status = 1，不需要写 WHERE" />
      <small class="form-text text-muted"> 会以 AND 追加到关联查询条件中 </small>
    </el-form-item>

    <el-form-item label="批量查询大小">
      <el-input-number v-model="formData.batchSize" :min="1" :max="5000" :step="100" controls-position="right" />
      <small class="form-text text-muted"> 单次 IN 查询包含的关联键数量，默认 500 </small>
    </el-form-item>

    <el-form-item label="结果缓存">
      <el-switch v-model="formData.cache" />
      <small class="form-text text-muted"> 开启后相同的关联键在本任务内只查询一次，默认开启 </small>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, inject } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';
import DataSourceSelector from '@/components/DataSourceSelector.vue';
import DataSourceTableSelector from '@/components/DataSourceTableSelector.vue';
import DataSourceTableFieldSelector from '@/components/DataSourceTableFieldSelector.vue';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save']);

const designContext = inject('etlTaskDesignContext', null);

const upstreamTables = ref([]);
const loadingUpstream = ref(false);
const upstreamError = ref('');

const parseSelectedColumns = value => {
  const text = (value || '').trim();
  if (!text) {
    return [];
  }
  return text
    .split(',')
    .map(name => name.trim())
    .filter(Boolean)
    .map(name => ({ name }));
};

const buildForm = () => {
  const config = props.node?.data?.config || {};
  return {
    sourceId: config.sourceId || '',
    schema: config.schema || '',
    table: config.table || '',
    localField: config.localField || '',
    lookupField: config.lookupField || '',
    selectColumns: config.selectColumns || '',
    joinType: config.joinType || 'LEFT',
    outputPrefix: config.outputPrefix || '',
    where: config.where || '',
    batchSize: Number(config.batchSize) > 0 ? Number(config.batchSize) : 500,
    cache: config.cache !== false,
  };
};

const formData = reactive(buildForm());
const selectedOutputFields = ref(parseSelectedColumns(formData.selectColumns));

const resetForm = () => {
  Object.assign(formData, buildForm());
  selectedOutputFields.value = parseSelectedColumns(formData.selectColumns);
};

const tableSelectorKey = computed(() => `${formData.sourceId || ''}-${formData.schema || ''}-${formData.table || ''}`);
const outputFieldsSelectorKey = computed(() => `${tableSelectorKey.value}-${formData.selectColumns || ''}`);

const upstreamHint = computed(() => {
  if (upstreamError.value) {
    return upstreamError.value;
  }
  if (upstreamFieldOptions.value.length) {
    return '上游字段来自调试结果，可直接选择，也支持手工输入字段名';
  }
  return '可点击「调试上游」获取上游字段，或直接手工输入字段名';
});

const upstreamFieldOptions = computed(() => {
  const fields = [];
  const seen = new Set();
  for (const table of upstreamTables.value) {
    const columnTypes = table.columnTypes || {};
    for (const name of table.columns || []) {
      if (!name || seen.has(name)) {
        continue;
      }
      seen.add(name);
      fields.push({ name, type: columnTypes[name] || '' });
    }
  }
  const current = (formData.localField || '').trim();
  if (current && !seen.has(current)) {
    fields.unshift({ name: current, type: '' });
  }
  return fields;
});

const columnLabel = field => (field.type ? `${field.name} (${field.type})` : field.name);

const refreshUpstream = async (force = false) => {
  if (!designContext || typeof designContext.debugUpstreamTables !== 'function') {
    upstreamError.value = '当前环境无法调试上游组件';
    return;
  }
  loadingUpstream.value = true;
  upstreamError.value = '';
  try {
    const res = await designContext.debugUpstreamTables(props.node?.id, force);
    upstreamTables.value = res.tables || [];
    upstreamError.value = res.error || '';
  } catch (error) {
    upstreamError.value = error?.message || '获取上游字段失败';
  } finally {
    loadingUpstream.value = false;
  }
};

onMounted(() => {
  if (designContext && typeof designContext.debugUpstreamTables === 'function') {
    refreshUpstream();
  }
});

watch(
  () => props.node?.id,
  () => {
    resetForm();
    upstreamTables.value = [];
    upstreamError.value = '';
    if (designContext && typeof designContext.debugUpstreamTables === 'function') {
      refreshUpstream();
    }
  },
);

const handleDataSourceSelected = selectedData => {
  const dataSourceId = selectedData?.dataSourceId || '';
  const schema = selectedData?.schema || '';
  if (dataSourceId !== formData.sourceId || schema !== formData.schema) {
    formData.table = '';
    formData.lookupField = '';
    formData.selectColumns = '';
    selectedOutputFields.value = [];
  }
  formData.sourceId = dataSourceId;
  formData.schema = schema;
};

const handleTableSelected = selection => {
  const table = selection?.table || '';
  if (table !== formData.table) {
    formData.lookupField = '';
    formData.selectColumns = '';
    selectedOutputFields.value = [];
  }
  formData.table = table;
};

const handleLookupFieldSelected = field => {
  if (field?.name) {
    formData.lookupField = field.name;
  }
};

const handleOutputFieldsSelected = selection => {
  const fields = Array.isArray(selection) ? selection : [];
  selectedOutputFields.value = fields;
  formData.selectColumns = fields
    .map(field => field.name)
    .filter(Boolean)
    .join(',');
};

const saveConfig = async () => {
  if (!formData.sourceId) {
    ElMessage.error('请选择关联数据源');
    return;
  }
  if (!(formData.table || '').trim()) {
    ElMessage.error('请选择关联表');
    return;
  }
  if (!(formData.localField || '').trim()) {
    ElMessage.error('请选择或输入上游关联字段');
    return;
  }
  if (!(formData.lookupField || '').trim()) {
    ElMessage.error('请选择关联表字段');
    return;
  }

  emits('save', {
    sourceId: formData.sourceId,
    schema: formData.schema,
    table: formData.table.trim(),
    localField: formData.localField.trim(),
    lookupField: formData.lookupField.trim(),
    selectColumns: (formData.selectColumns || '').trim(),
    joinType: formData.joinType || 'LEFT',
    outputPrefix: (formData.outputPrefix || '').trim(),
    where: (formData.where || '').trim(),
    batchSize: Number(formData.batchSize) > 0 ? Number(formData.batchSize) : 500,
    cache: formData.cache !== false,
  });
};

defineExpose({ saveConfig });
</script>

<style scoped>
.condition-block {
  width: 100%;
  padding: 10px 12px;
  border-radius: 6px;
  background-color: #f7f9fc;
}

.condition-title {
  margin-bottom: 8px;
  color: #909399;
  font-size: 12px;
}

.mapping-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mapping-row > :deep(.el-select),
.mapping-row > :deep(.el-input) {
  flex: 1 1 0;
  min-width: 0;
}

.field-with-action {
  display: flex;
  flex: 1 1 0;
  gap: 8px;
  min-width: 0;
}

.field-with-action :deep(.el-select) {
  flex: 1 1 0;
  min-width: 0;
}

.mapping-arrow {
  flex: none;
  color: #909399;
  font-weight: bold;
}

.form-text {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}

textarea {
  resize: vertical;
}
</style>
