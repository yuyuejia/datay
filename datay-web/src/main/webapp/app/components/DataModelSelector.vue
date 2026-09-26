<template>
  <div class="input-group">
    <!-- 输入框，显示选中的模型，点击可打开模态框 -->
    <el-input v-model="showName" readonly placeholder="请选择数据模型" @click="showModal = true" />
    <el-button plain @click="showModal = true">选择</el-button>
    <app-modal v-model="showModal" id="dataModelModal" title="选择数据模型">
      <div class="modal-body">
        <el-form name="editForm" label-width="80px">
          <el-form-item label="搜索">
            <el-input v-model="searchQuery" placeholder="按模型名称 / 编码搜索" clearable />
          </el-form-item>
          <el-form-item label="数据模型">
            <el-select v-model="selectedModelId" placeholder="请选择数据模型" style="width: 100%">
              <el-option
                v-for="model in filteredModels"
                :key="model.id"
                :value="model.id"
                :label="model.name + (model.code ? '（' + model.code + '）' : '')"
              />
            </el-select>
          </el-form-item>
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
        </el-form>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="showModal = false">取消</el-button>
          <el-button type="primary" @click="confirmSelection">确认</el-button>
        </div>
      </template>
    </app-modal>
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
