<template>
  <form name="editForm" novalidate>
    <div>
      <!-- 数据模型选择 -->
      <div class="form-group">
        <label for="modelId">数据模型</label>
        <DataModelSelector :modelId="formData.modelId" @selected="handleModelSelected" />
        <small class="form-text text-muted"> 选择数据模型后，任务定义将基于模型绑定的数据源与表信息生成 StreamJdbcOutput </small>
      </div>

      <!-- 模型绑定的目标信息（只读展示） -->
      <div v-if="selectedModel" class="model-summary">
        <div class="model-summary-item">
          <span class="model-summary-label">目标数据源</span>
          <span class="model-summary-value">{{ selectedModel.dataSourceId ? dataSourceName : '未绑定' }}</span>
        </div>
        <div class="model-summary-item">
          <span class="model-summary-label">Schema</span>
          <span class="model-summary-value">{{ selectedModel.schemaName || '未绑定' }}</span>
        </div>
        <div class="model-summary-item">
          <span class="model-summary-label">目标表</span>
          <span class="model-summary-value">{{ selectedModel.tableName || '未绑定' }}</span>
        </div>
        <el-alert
          v-if="!selectedModel.dataSourceId || !selectedModel.tableName"
          title="该数据模型尚未绑定数据源或物理表，请先在「数据模型」中完成物化"
          type="warning"
          :closable="false"
          show-icon
        />
      </div>

      <!-- 写入策略 -->
      <div class="form-group">
        <label for="model">写入策略</label>
        <select class="form-control" id="model" name="model" v-model="formData.model">
          <option v-for="item in writeModeOptions" :key="item.value" :value="item.value">{{ item.name }}</option>
        </select>
        <small class="form-text text-muted">{{ currentWriteModeDescription }}</small>
      </div>

      <!-- 更新字段 -->
      <div v-if="formData.model === 'update'" class="form-group">
        <label for="updateColumn">更新字段</label>
        <input
          type="text"
          class="form-control"
          id="updateColumn"
          name="updateColumn"
          v-model="formData.updateColumn"
          placeholder="多个字段用逗号分隔"
        />
      </div>

      <!-- 批处理数 -->
      <div class="form-group">
        <label for="maxRows">批处理数</label>
        <input class="form-control" id="maxRows" name="maxRows" type="number" min="1" v-model.number="formData.maxRows" />
      </div>
    </div>

    <div class="form-actions">
      <el-button type="primary" @click="saveConfig">保存</el-button>
      <el-button @click="cancelConfig">取消</el-button>
    </div>
  </form>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';
import DataModelSelector from '@/components/DataModelSelector.vue';
import DataModelService from '@/entities/data-model/data-model.service';
import DataSourceService from '@/entities/data-source/data-source.service';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save', 'cancel']);

const dataModelService = new DataModelService();
const dataSourceService = new DataSourceService();

const models = ref([]);
const dataSources = ref([]);

const writeModeOptions = [
  { name: '覆盖写入（OVERWRITE）', value: 'overwrite', description: '清空目标表后写入新数据' },
  { name: '追加写入（INSERT）', value: 'append', description: '将数据追加到目标表中' },
  { name: '更新写入（UPDATE）', value: 'update', description: '根据指定字段更新目标表中的数据' },
  { name: '自动推断', value: 'auto', description: '根据数据流事件类型推断写入策略' },
];

const formData = reactive({
  modelId: props.node?.data?.config?.modelId ?? null,
  model: props.node?.data?.config?.model || 'append',
  updateColumn: props.node?.data?.config?.updateColumn || '',
  maxRows: props.node?.data?.config?.maxRows || 1000,
});

const selectedModel = computed(() => models.value.find(item => String(item.id) === String(formData.modelId)) || null);

const dataSourceName = computed(() => {
  if (!selectedModel.value || !selectedModel.value.dataSourceId) {
    return '';
  }
  const source = dataSources.value.find(item => String(item.id) === String(selectedModel.value.dataSourceId));
  return source ? `${source.name}（ID: ${source.id}）` : `ID: ${selectedModel.value.dataSourceId}`;
});

const currentWriteModeDescription = computed(() => {
  const matched = writeModeOptions.find(item => item.value === formData.model);
  return matched ? matched.description : '';
});

const loadModels = async () => {
  try {
    const res = await dataModelService.retrieve();
    models.value = res.data || res || [];
  } catch (error) {
    console.error('获取数据模型失败', error);
  }
};

const loadDataSources = async () => {
  try {
    const res = await dataSourceService.retrieve({ page: 0, size: 1000 });
    dataSources.value = res.data || [];
  } catch (error) {
    console.error('获取数据源失败', error);
  }
};

onMounted(() => {
  loadModels();
  loadDataSources();
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
  emits('save', {
    modelId: model.id,
    model: formData.model,
    updateColumn: formData.updateColumn,
    maxRows: formData.maxRows,
  });
};

const cancelConfig = () => {
  emits('cancel');
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

.model-summary {
  padding: 12px;
  margin-bottom: 20px;
  background-color: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

.model-summary-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  line-height: 2;
}

.model-summary-label {
  width: 90px;
  color: #909399;
}

.model-summary-value {
  flex: 1;
  color: #303133;
  word-break: break-all;
}
</style>
