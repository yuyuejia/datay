<template>
  <el-form name="editForm" label-position="top">
    <el-form-item label="关联数据源">
      <DataSourceSelector :datasourceId="formData.sourceId" :schema="formData.schema" @selected="handleDataSourceSelected" />
      <small class="form-text text-muted"> 所有关联表须位于同一数据源；查询另一个数据库中的表，可与上游数据源不同 </small>
    </el-form-item>

    <el-form-item label="关联表配置">
      <div class="lookup-container">
        <div v-for="(item, index) in formData.tables" :key="item._key" class="lookup-item">
          <div class="lookup-item-header">
            <span>关联表 {{ index + 1 }}</span>
            <div class="lookup-item-actions">
              <el-button link size="small" :disabled="index === 0" @click="moveLookup(index, -1)">上移</el-button>
              <el-button link size="small" :disabled="index === formData.tables.length - 1" @click="moveLookup(index, 1)">下移</el-button>
              <el-button type="danger" link size="small" @click="removeLookup(index)">删除</el-button>
            </div>
          </div>

          <div class="lookup-field">
            <div class="lookup-field-label">关联表</div>
            <DataSourceTableSelector
              :key="`table-${item._key}-${formData.sourceId || ''}-${formData.schema || ''}`"
              :dataSourceId="formData.sourceId"
              :schema="formData.schema"
              :selectedTable="item.table || null"
              :selectedTables="[]"
              :multiple="false"
              @selected="selection => handleTableSelected(index, selection)"
            />
          </div>

          <div class="lookup-field">
            <div class="lookup-field-label">关联条件（上游字段 = 关联表字段）</div>
            <div class="mapping-row">
              <div class="field-with-action">
                <el-select v-model="item.localField" placeholder="上游字段" filterable allow-create default-first-option>
                  <el-option
                    v-for="field in upstreamFieldOptionsFor(index)"
                    :key="field.name"
                    :value="field.name"
                    :label="columnLabel(field)"
                  />
                </el-select>
                <el-button size="small" :loading="loadingUpstream" @click="refreshUpstream(true)">调试上游</el-button>
              </div>
              <span class="mapping-arrow">=</span>
              <DataSourceTableFieldSelector
                :key="`lookup-field-${item._key}-${item.table || ''}`"
                :dataSourceId="formData.sourceId"
                :schema="formData.schema"
                :table="item.table"
                :selectedFields="[]"
                :selectedField="item.lookupField || ''"
                :multiple="false"
                @selected="field => handleLookupFieldSelected(index, field)"
              />
            </div>
            <small v-if="index === 0" class="form-text text-muted">{{ upstreamHint }}</small>
            <small v-else class="form-text text-muted"> 可选择上游调试字段、手工输入，或引用前面关联表回填出的字段（前缀 + 字段名） </small>
          </div>

          <div class="lookup-field">
            <div class="lookup-field-label">回填字段</div>
            <DataSourceTableFieldSelector
              :key="`output-${item._key}-${item.table || ''}-${item.selectColumns || ''}`"
              :dataSourceId="formData.sourceId"
              :schema="formData.schema"
              :table="item.table"
              :selectedFields="parseSelectedColumns(item.selectColumns)"
              :selectedField="null"
              :multiple="true"
              @selected="fields => handleOutputFieldsSelected(index, fields)"
            />
            <small class="form-text text-muted"> 选择需要回填的关联表字段；不选择时默认回填全部字段 </small>
          </div>

          <div class="lookup-grid">
            <div class="lookup-field">
              <div class="lookup-field-label">关联类型</div>
              <el-select v-model="item.joinType">
                <el-option value="LEFT" label="LEFT - 保留未匹配记录" />
                <el-option value="INNER" label="INNER - 仅保留匹配记录" />
              </el-select>
            </div>
            <div class="lookup-field">
              <div class="lookup-field-label">回填字段前缀</div>
              <el-input v-model="item.outputPrefix" placeholder="例如 user_" />
              <small class="form-text text-muted"> 留空则直接使用关联表字段名（同名字段会被覆盖） </small>
            </div>
          </div>

          <div class="lookup-field">
            <div class="lookup-field-label">附加过滤条件</div>
            <el-input type="textarea" v-model="item.where" :rows="2" placeholder="例如 status = 1，不需要写 WHERE" />
            <small class="form-text text-muted"> 会以 AND 追加到当前关联表的查询条件中 </small>
          </div>
        </div>

        <el-button type="primary" size="small" @click="addLookup">添加关联表</el-button>
      </div>
      <small class="form-text text-muted"> 按列表顺序依次执行；后面的关联表可以使用前面关联表回填出的字段进行链式 lookup。 </small>
    </el-form-item>

    <el-form-item label="输出表名">
      <el-input v-model="formData.outputTable" placeholder="例如 order_lookup" />
      <small class="form-text text-muted">
        本组件结果集的表名；多个关联/查询组件接入同一个 Join 时请配置不同表名，留空则沿用上游表名
      </small>
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

let lookupKeySeed = 0;
const nextLookupKey = () => ++lookupKeySeed;

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

const normalizeLookup = source => ({
  _key: nextLookupKey(),
  table: source?.table || '',
  localField: source?.localField || '',
  lookupField: source?.lookupField || '',
  selectColumns: source?.selectColumns || '',
  joinType: source?.joinType || 'LEFT',
  outputPrefix: source?.outputPrefix || '',
  where: source?.where || '',
});

const buildTables = config => {
  if (Array.isArray(config.tables) && config.tables.length) {
    return config.tables.map(item => normalizeLookup(item));
  }
  if (config.table) {
    return [normalizeLookup(config)];
  }
  return [normalizeLookup({})];
};

const buildForm = () => {
  const config = props.node?.data?.config || {};
  return {
    sourceId: config.sourceId || '',
    schema: config.schema || '',
    outputTable: config.outputTable || '',
    batchSize: Number(config.batchSize) > 0 ? Number(config.batchSize) : 500,
    cache: config.cache !== false,
    tables: buildTables(config),
  };
};

const formData = reactive(buildForm());

const resetForm = () => {
  Object.assign(formData, buildForm());
};

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
  return fields;
});

// 当前关联表可用的上游字段：上游调试字段 + 前面关联表回填出的字段
const upstreamFieldOptionsFor = index => {
  const fields = [];
  const seen = new Set();
  const push = field => {
    if (!field.name || seen.has(field.name)) {
      return;
    }
    seen.add(field.name);
    fields.push(field);
  };
  upstreamFieldOptions.value.forEach(push);
  formData.tables.slice(0, index).forEach(previous => {
    const prefix = previous.outputPrefix || '';
    parseSelectedColumns(previous.selectColumns).forEach(column => {
      push({ name: `${prefix}${column.name}`, type: '' });
    });
  });
  const current = (formData.tables[index]?.localField || '').trim();
  if (current && !seen.has(current)) {
    fields.unshift({ name: current, type: '' });
  }
  return fields;
};

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
    formData.tables.forEach(item => {
      item.table = '';
      item.lookupField = '';
      item.selectColumns = '';
    });
  }
  formData.sourceId = dataSourceId;
  formData.schema = schema;
};

const handleTableSelected = (index, selection) => {
  const item = formData.tables[index];
  if (!item) {
    return;
  }
  const table = selection?.table || '';
  if (table !== item.table) {
    item.lookupField = '';
    item.selectColumns = '';
  }
  item.table = table;
};

const handleLookupFieldSelected = (index, field) => {
  const item = formData.tables[index];
  if (item && field?.name) {
    item.lookupField = field.name;
  }
};

const handleOutputFieldsSelected = (index, selection) => {
  const item = formData.tables[index];
  if (!item) {
    return;
  }
  const fields = Array.isArray(selection) ? selection : [];
  item.selectColumns = fields
    .map(field => field.name)
    .filter(Boolean)
    .join(',');
};

const addLookup = () => {
  formData.tables.push(normalizeLookup({}));
};

const removeLookup = index => {
  formData.tables.splice(index, 1);
};

const moveLookup = (index, offset) => {
  const target = index + offset;
  if (target < 0 || target >= formData.tables.length) {
    return;
  }
  const [item] = formData.tables.splice(index, 1);
  formData.tables.splice(target, 0, item);
};

const saveConfig = async () => {
  if (!formData.sourceId) {
    ElMessage.error('请选择关联数据源');
    return;
  }
  if (!formData.tables.length) {
    ElMessage.error('请至少添加一个关联表');
    return;
  }

  const savedTables = [];
  for (let i = 0; i < formData.tables.length; i++) {
    const item = formData.tables[i];
    const table = (item.table || '').trim();
    const localField = (item.localField || '').trim();
    const lookupField = (item.lookupField || '').trim();
    if (!table) {
      ElMessage.error(`请选择第 ${i + 1} 个关联表`);
      return;
    }
    if (!localField) {
      ElMessage.error(`请选择或输入第 ${i + 1} 个关联表的上游关联字段`);
      return;
    }
    if (!lookupField) {
      ElMessage.error(`请选择第 ${i + 1} 个关联表的关联字段`);
      return;
    }
    savedTables.push({
      table,
      localField,
      lookupField,
      selectColumns: (item.selectColumns || '').trim(),
      joinType: item.joinType || 'LEFT',
      outputPrefix: (item.outputPrefix || '').trim(),
      where: (item.where || '').trim(),
    });
  }

  emits('save', {
    sourceId: formData.sourceId,
    schema: formData.schema,
    outputTable: (formData.outputTable || '').trim(),
    batchSize: Number(formData.batchSize) > 0 ? Number(formData.batchSize) : 500,
    cache: formData.cache !== false,
    tables: savedTables,
  });
};

defineExpose({ saveConfig });
</script>

<style scoped>
.lookup-container {
  width: 100%;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  background-color: #fafcff;
}

.lookup-item {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 12px;
  background-color: #fff;
  box-shadow: 0 1px 2px rgb(0 0 0 / 4%);
}

.lookup-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f2f5;
  color: #303133;
  font-size: 13px;
  font-weight: 600;
}

.lookup-item-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.lookup-field {
  margin-bottom: 10px;
}

.lookup-field:last-child {
  margin-bottom: 0;
}

.lookup-field-label {
  margin-bottom: 4px;
  color: #606266;
  font-size: 12px;
}

.lookup-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
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
