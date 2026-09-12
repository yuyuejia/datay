<template>
  <div class="input-group">
    <!-- 输入框，显示选中的模型，点击可打开模态框 -->
    <input type="text" class="form-control" v-model="showName" readonly @click="showModal = true" />
    <button type="button" class="btn btn-outline-secondary" @click="showModal = true">选择</button>
    <b-modal v-model="showModal" id="dataModelModal" title="选择数据模型">
      <div class="modal-body">
        <form name="editForm" novalidate>
          <div class="form-group">
            <label for="modelSearch" class="form-control-label">搜索</label>
            <input id="modelSearch" v-model="searchQuery" type="text" class="form-control" placeholder="按模型名称 / 编码搜索" />
          </div>
          <div class="form-group">
            <label for="modelSelect" class="form-control-label">数据模型</label>
            <select id="modelSelect" v-model="selectedModelId" class="form-control">
              <option :value="null">请选择数据模型</option>
              <option v-for="model in filteredModels" :key="model.id" :value="model.id">
                {{ model.name }}{{ model.code ? '（' + model.code + '）' : '' }}
              </option>
            </select>
          </div>
          <div v-if="selectedModel" class="model-preview">
            <div class="model-preview-item">
              <span class="model-preview-label">目标数据源</span>
              <span class="model-preview-value">{{ selectedModel.dataSourceId ? selectedModel.dataSourceId : '未绑定' }}</span>
            </div>
            <div class="model-preview-item">
              <span class="model-preview-label">Schema</span>
              <span class="model-preview-value">{{ selectedModel.schemaName || '未绑定' }}</span>
            </div>
            <div class="model-preview-item">
              <span class="model-preview-label">目标表</span>
              <span class="model-preview-value">{{ selectedModel.tableName || '未绑定' }}</span>
            </div>
          </div>
        </form>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="showModal = false">取消</el-button>
          <el-button type="primary" @click="confirmSelection">确认</el-button>
        </div>
      </template>
    </b-modal>
  </div>
</template>

<script lang="ts" setup>
import { ref, computed, onMounted, watch } from 'vue';
import DataModelService from '@/entities/data-model/data-model.service';

const props = defineProps<{
  modelId?: number | null;
}>();

const emit = defineEmits(['selected']);

const dataModelService = new DataModelService();
const showModal = ref(false);
const models = ref<any[]>([]);
const searchQuery = ref('');
const selectedModelId = ref<number | null>(props.modelId || null);

const showName = ref<string | null>(null);

const filteredModels = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase();
  if (!keyword) {
    return models.value;
  }
  return models.value.filter(
    model =>
      String(model.name || '')
        .toLowerCase()
        .includes(keyword) ||
      String(model.code || '')
        .toLowerCase()
        .includes(keyword),
  );
});

const selectedModel = computed(() => models.value.find(model => String(model.id) === String(selectedModelId.value)) || null);

const refreshShowName = () => {
  const model = models.value.find(item => String(item.id) === String(props.modelId));
  showName.value = model ? `${model.name}${model.code ? '（' + model.code + '）' : ''}` : null;
};

const loadModels = async () => {
  try {
    const res = await dataModelService.retrieve();
    models.value = res.data || res || [];
    refreshShowName();
  } catch (error) {
    console.error('获取数据模型失败', error);
  }
};

watch(
  () => props.modelId,
  () => {
    selectedModelId.value = props.modelId || null;
    refreshShowName();
  },
);

onMounted(loadModels);

const confirmSelection = () => {
  const model = selectedModel.value;
  if (!model) {
    return;
  }
  showName.value = `${model.name}${model.code ? '（' + model.code + '）' : ''}`;
  showModal.value = false;
  emit('selected', model);
};
</script>

<style scoped>
.model-preview {
  padding: 12px;
  background-color: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

.model-preview-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  line-height: 2;
}

.model-preview-label {
  width: 90px;
  color: #909399;
}

.model-preview-value {
  flex: 1;
  color: #303133;
  word-break: break-all;
}
</style>
