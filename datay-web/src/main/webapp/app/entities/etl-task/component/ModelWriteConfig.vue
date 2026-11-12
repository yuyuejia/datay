<template>
  <el-form name="editForm" label-position="top">
    <div>
      <!-- 数据模型选择 -->
      <el-form-item label="数据模型">
        <DataModelSelector :model-id="formData.modelId" @selected="handleModelSelected" />
        <el-alert
          v-if="selectedModel && (!selectedModel.dataSourceId || !selectedModel.tableName)"
          class="model-warning"
          title="该数据模型尚未绑定数据源或物理表，请先在「数据模型」中完成物化"
          type="warning"
          :closable="false"
          show-icon
        />
      </el-form-item>

      <!-- 写入策略 -->
      <el-form-item label="写入策略">
        <el-select id="model" name="model" v-model="formData.model">
          <el-option v-for="item in writeModeOptions" :key="item.value" :value="item.value" :label="item.name" />
        </el-select>
        <small class="form-text text-muted">{{ currentWriteModeDescription }}</small>
      </el-form-item>

      <!-- 字段映射 -->
      <el-form-item label="字段映射">
        <div class="mapping-container">
          <div class="mapping-toolbar">
            <el-button size="small" :loading="loadingUpstream" @click="refreshUpstream(true)">获取上游字段</el-button>
            <el-button size="small" :disabled="!targetFields.length" @click="fillSameNameMapping">按同名字段填充</el-button>
            <el-button size="small" text :disabled="!formData.header_map.length" @click="clearMapping">清空</el-button>
            <span class="mapping-status">{{ mappingHint }}</span>
          </div>
          <div v-if="!formData.header_map.length" class="mapping-empty">暂未配置映射，留空表示按同名字段自动映射</div>
          <div v-for="(mapping, index) in formData.header_map" :key="index" class="mapping-row">
            <el-select
              v-model="mapping.source"
              filterable
              allow-create
              default-first-option
              clearable
              :loading="loadingUpstream"
              placeholder="源字段（上游）"
            >
              <el-option v-for="field in sourceFieldOptions" :key="field.name" :value="field.name" :label="fieldLabel(field)" />
            </el-select>
            <span class="mapping-arrow">→</span>
            <el-select
              v-model="mapping.target"
              filterable
              allow-create
              default-first-option
              clearable
              :loading="loadingTargetFields"
              placeholder="目标字段"
            >
              <el-option v-for="field in targetFieldOptions" :key="field.name" :value="field.name" :label="fieldLabel(field)" />
            </el-select>
            <el-button type="danger" link size="small" @click="removeMapping(index)">删除</el-button>
          </div>
          <el-button type="primary" size="small" @click="addMapping">添加映射</el-button>
          <small class="form-text text-muted">
            配置上游字段到模型目标表的字段映射；留空则按字段名自动映射，目标字段可从模型绑定表的字段中选择。
          </small>
        </div>
      </el-form-item>

      <!-- 批处理数 -->
      <el-form-item label="批处理数">
        <el-input-number :controls="false" id="maxRows" name="maxRows" :min="1" v-model="formData.maxRows" controls-position="right" />
      </el-form-item>
    </div>
  </el-form>
</template>

<script setup>
import { ref, reactive, computed, watch, inject, onMounted } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';
import DataModelSelector from '@/components/DataModelSelector.vue';
import DataModelService from '@/entities/data-model/data-model.service';
import DataSourceService from '@/entities/data-source/data-source.service';

const props = defineProps({
  node: {
    type: Object,
    default: () => ({}),
  },
});

const emits = defineEmits(['save']);

const dataModelService = new DataModelService();
const dataSourceService = new DataSourceService();
const designContext = inject('etlTaskDesignContext', null);

const models = ref([]);

const writeModeOptions = [
  { name: '覆盖写入（OVERWRITE）', value: 'overwrite', description: '清空目标表后写入新数据' },
  { name: '追加写入（INSERT）', value: 'append', description: '将数据追加到目标表中' },
  { name: '更新写入（UPSERT）', value: 'update', description: '根据主键更新目标表中的数据' },
  { name: '自动推断（根据属性推断，适合 CDC）', value: 'auto', description: '根据数据流事件类型推断写入策略' },
];

const normalizeHeaderMap = raw => {
  if (typeof raw === 'string') {
    if (!raw.trim()) {
      return [];
    }
    try {
      return normalizeHeaderMap(JSON.parse(raw));
    } catch {
      return [];
    }
  }
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw
    .filter(item => item && typeof item === 'object')
    .map(item => ({
      source: String(item.source ?? item.inColumns ?? ''),
      target: String(item.target ?? item.outColumns ?? ''),
    }));
};

const formData = reactive({
  modelId: props.node?.data?.config?.modelId ?? null,
  model: props.node?.data?.config?.model || 'append',
  updateColumn: props.node?.data?.config?.updateColumn || '',
  maxRows: props.node?.data?.config?.maxRows || 1000,
  header_map: normalizeHeaderMap(props.node?.data?.config?.header_map),
});

const selectedModel = computed(() => models.value.find(item => String(item.id) === String(formData.modelId)) || null);

const currentWriteModeDescription = computed(() => {
  const matched = writeModeOptions.find(item => item.value === formData.model);
  return matched ? matched.description : '';
});

// 目标表字段：来自模型绑定的物理表，用于目标字段下拉选择
const targetFields = ref([]);
const loadingTargetFields = ref(false);

const loadTargetFields = async () => {
  targetFields.value = [];
  const model = selectedModel.value;
  if (!model?.dataSourceId || !model?.schemaName || !model?.tableName) {
    return;
  }
  loadingTargetFields.value = true;
  try {
    const res = await dataSourceService.getFields(model.dataSourceId, model.schemaName, model.tableName);
    targetFields.value = Array.isArray(res?.data) ? res.data : [];
  } catch (error) {
    console.error('获取目标表字段失败', error);
  } finally {
    loadingTargetFields.value = false;
  }
};

watch(() => [selectedModel.value?.dataSourceId, selectedModel.value?.schemaName, selectedModel.value?.tableName], loadTargetFields, {
  immediate: true,
});

// 上游字段：通过设计器提供的调试能力获取
const upstreamTables = ref([]);
const loadingUpstream = ref(false);
const upstreamError = ref('');

const refreshUpstream = async (force = false) => {
  if (!designContext || typeof designContext.debugUpstreamTables !== 'function') {
    upstreamError.value = '当前环境无法获取上游字段，请手工输入';
    return;
  }
  loadingUpstream.value = true;
  upstreamError.value = '';
  try {
    const res = await designContext.debugUpstreamTables(props.node?.id, force);
    upstreamTables.value = res?.tables || [];
    upstreamError.value = res?.error || '';
  } catch (error) {
    upstreamError.value = error?.message || '获取上游字段失败';
  } finally {
    loadingUpstream.value = false;
  }
};

const sourceFieldOptions = computed(() => {
  const fields = [];
  const seen = new Set();
  upstreamTables.value.forEach(table => {
    const columnTypes = table.columnTypes || {};
    (table.columns || []).forEach(name => {
      if (!name || seen.has(name)) {
        return;
      }
      seen.add(name);
      fields.push({ name, type: columnTypes[name] || '' });
    });
  });
  formData.header_map.forEach(mapping => {
    const name = (mapping.source || '').trim();
    if (name && !seen.has(name)) {
      seen.add(name);
      fields.push({ name, type: '' });
    }
  });
  return fields;
});

const targetFieldOptions = computed(() => {
  const fields = [];
  const seen = new Set();
  targetFields.value.forEach(field => {
    if (!field?.name || seen.has(field.name)) {
      return;
    }
    seen.add(field.name);
    fields.push({ name: field.name, type: field.type || '' });
  });
  formData.header_map.forEach(mapping => {
    const name = (mapping.target || '').trim();
    if (name && !seen.has(name)) {
      seen.add(name);
      fields.push({ name, type: '' });
    }
  });
  return fields;
});

const mappingHint = computed(() => {
  if (loadingTargetFields.value) {
    return '正在获取模型目标表字段...';
  }
  if (loadingUpstream.value) {
    return '正在获取上游字段...';
  }
  if (upstreamError.value) {
    return upstreamError.value;
  }
  if (upstreamTables.value.length) {
    return `已获取 ${sourceFieldOptions.value.length} 个上游字段`;
  }
  return '可点击「获取上游字段」加载上游字段，也可直接手工输入';
});

const fieldLabel = field => (field.type ? `${field.name} (${field.type})` : field.name);

const addMapping = () => {
  formData.header_map.push({ source: '', target: '' });
};

const removeMapping = index => {
  formData.header_map.splice(index, 1);
};

const clearMapping = () => {
  formData.header_map.splice(0, formData.header_map.length);
};

const fillSameNameMapping = () => {
  const existingTargets = new Set(formData.header_map.map(mapping => (mapping.target || '').trim()).filter(Boolean));
  const sourceNames = new Set(sourceFieldOptions.value.map(field => field.name));
  let added = 0;
  targetFields.value.forEach(field => {
    if (!field?.name || existingTargets.has(field.name)) {
      return;
    }
    if (sourceNames.size > 0 && !sourceNames.has(field.name)) {
      return;
    }
    formData.header_map.push({ source: field.name, target: field.name });
    existingTargets.add(field.name);
    added += 1;
  });
  if (added === 0) {
    ElMessage.info('没有可自动填充的同名字段');
  } else {
    ElMessage.success(`已填充 ${added} 个同名字段映射`);
  }
};

const loadModels = async () => {
  try {
    const res = await dataModelService.retrieve();
    models.value = res.data || res || [];
  } catch (error) {
    console.error('获取数据模型失败', error);
  }
};

onMounted(() => {
  loadModels();
});

const handleModelSelected = model => {
  formData.modelId = model?.id ?? null;
};

const saveConfig = () => {
  if (!formData.modelId) {
    ElMessage.error('请选择数据模型');
    return;
  }
  const model = selectedModel.value;
  if (!model) {
    ElMessage.error('数据模型不存在，请刷新后重试');
    return;
  }
  if (!model.dataSourceId) {
    ElMessage.error('该数据模型未绑定数据源，请先在「数据模型」中完成物化');
    return;
  }
  if (!model.tableName) {
    ElMessage.error('该数据模型未绑定物理表，请先在「数据模型」中完成物化');
    return;
  }
  const headerMap = [];
  const targets = new Set();
  for (const mapping of formData.header_map) {
    const source = (mapping.source || '').trim();
    const target = (mapping.target || '').trim();
    if (!source && !target) {
      continue;
    }
    if (!source || !target) {
      ElMessage.error('字段映射需同时填写源字段和目标字段');
      return;
    }
    if (targets.has(target)) {
      ElMessage.error(`目标字段「${target}」存在重复映射`);
      return;
    }
    targets.add(target);
    headerMap.push({ source, target });
  }
  emits('save', {
    modelId: model.id,
    model: formData.model,
    updateColumn: formData.updateColumn,
    maxRows: formData.maxRows,
    header_map: headerMap,
  });
};

defineExpose({ saveConfig });
</script>

<style scoped>
.form-actions {
  margin-top: 30px;
  text-align: right;
}

.form-group {
  margin-bottom: 20px;
}

.model-warning {
  width: 100%;
  margin-top: 8px;
}

.mapping-container {
  width: 100%;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  background-color: #fafcff;
}

.mapping-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.mapping-status {
  color: #909399;
  font-size: 12px;
  margin-left: auto;
}

.mapping-empty {
  color: #909399;
  font-size: 13px;
  padding: 4px 0 12px;
}

.mapping-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.mapping-row > :deep(.el-select) {
  flex: 1 1 0;
  width: auto;
  min-width: 0;
}

.mapping-arrow {
  color: #909399;
  font-weight: bold;
}
</style>
