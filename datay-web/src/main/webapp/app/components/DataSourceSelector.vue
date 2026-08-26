<template>
  <div class="input-group">
    <!-- 输入框，显示选中信息，点击可打开模态框 -->
    <input type="text" class="form-control" v-model="showName" readonly @click="showModal = true" />
    <!-- 按钮，点击可打开模态框 -->
    <button type="button" class="btn btn-outline-secondary" @click="showModal = true">选择</button>
    <b-modal v-model="showModal" id="dataSourceModal" :title="modalTitle">
      <div class="modal-body">
        <form name="editForm" novalidate>
          <!-- 数据源类型选择（仅在未传入 dataSourceType 时显示） -->
          <div v-if="!props.dataSourceType" class="form-group">
            <label for="dataSourceType" class="form-control-label">数据源类型</label>
            <select class="form-control" v-model="selectedDataSourceType" @change="filterDataSources">
              <option value="">所有类型</option>
              <option v-for="type in dataSourceTypes" :key="type" :value="type">{{ type }}</option>
            </select>
          </div>
          <div class="form-group">
            <label for="model" class="form-control-label">数据源</label>
            <select class="form-control" v-model="selectedDataSource" @change="fetchSchemas">
              <option v-for="source in filteredDataSources" :key="source.id" :value="source.id">{{ source.name }}</option>
            </select>
          </div>
          <!-- Schema 选择 -->
          <div class="form-group">
            <label for="schema" class="form-control-label">Schema</label>
            <select class="form-control" v-model="selectedSchema" :disabled="!selectedDataSource">
              <option v-for="schema in schemas" :key="schema" :value="schema">{{ schema }}</option>
            </select>
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
import { ref, onMounted, computed } from 'vue';
import DataSourceService from '@/entities/data-source/data-source.service';

const props = defineProps<{
  datasourceId?: number | null;
  schema?: string | null;
  dataSourceType?: string | null; // 新增：数据源类型过滤参数
}>();

const emit = defineEmits(['selected']);

const dataSourceService = new DataSourceService();
const showModal = ref(false);
const dataSources = ref<any[]>([]);
const schemas = ref<string[]>([]);
const selectedDataSource = ref<number | null>(props.datasourceId || null);
const showName = ref<string | null>();
const selectedSchema = ref<string | null>(props.schema || null);
const selectedDataSourceType = ref<string | null>(props.dataSourceType || '');
const dataSourceTypes = ref<string[]>([]);

// 计算属性：模态框标题
const modalTitle = computed(() => {
  return props.dataSourceType ? `选择 ${props.dataSourceType} 数据源` : '选择数据源';
});

// 计算属性：过滤后的数据源列表
const filteredDataSources = computed(() => {
  if (!selectedDataSourceType.value) {
    return dataSources.value;
  }
  return dataSources.value.filter(source => source.type === selectedDataSourceType.value);
});

onMounted(async () => {
  await fetchDataSources();
  // 如果传入了 dataSourceType，自动设置选中类型
  if (props.dataSourceType) {
    selectedDataSourceType.value = props.dataSourceType;
  }
});

const fetchDataSources = async () => {
  try {
    const res = await dataSourceService.retrieve();
    dataSources.value = res.data;
    // 提取所有数据源类型
    const types = [...new Set(res.data.map(source => source.type).filter(Boolean))];
    dataSourceTypes.value = types;

    // 如果传入了 datasourceId，获取对应的 schemas
    if (props.datasourceId) {
      await fetchSchemas();
    }
  } catch (error) {
    console.error('获取数据源失败', error);
  }
};

const filterDataSources = () => {
  // 重置选中的数据源和 schema
  selectedDataSource.value = null;
  selectedSchema.value = null;
  schemas.value = [];
  showName.value = null;
};

const fetchSchemas = async () => {
  if (selectedDataSource.value) {
    try {
      const res = await dataSourceService.getSchemas(selectedDataSource.value);
      schemas.value = res.data;
      // 如果传入了 schema，检查是否存在于获取的 schemas 中
      if (props.schema && schemas.value.includes(props.schema)) {
        selectedSchema.value = props.schema;
      }
    } catch (error) {
      schemas.value = [];
      console.error('获取 Schema 失败', error);
    }
    updateShowName();
  } else {
    schemas.value = [];
    showName.value = null;
  }
};

const confirmSelection = () => {
  if (selectedDataSource.value && selectedSchema.value) {
    updateShowName();
    emit('selected', {
      dataSourceId: selectedDataSource.value,
      schema: selectedSchema.value,
    });
  }
  showModal.value = false;
};

const updateShowName = () => {
  const selectDB = dataSources.value.find(item => item.id == selectedDataSource.value);
  if (selectDB) {
    showName.value = selectDB.name + ' - ' + selectedSchema.value;
  }
};

// 根据数据源 ID 获取数据源名称
const getDataSourceName = (id: number) => {
  const source = dataSources.value.find(item => item.id === id);
  return source ? source.name : '';
};
</script>

<style scoped>
.input-group {
  margin-bottom: 1rem;
}

.selected-info {
  margin-top: 10px;
  padding: 10px;
  background-color: #f8f9fa;
  border: 1px solid #dee2e6;
  border-radius: 4px;
}
</style>
