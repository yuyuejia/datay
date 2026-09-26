<template>
  <div class="input-group">
    <!-- 输入框（带“选择”按钮），点击可打开模态框 -->
    <el-input v-model="showName" readonly placeholder="请选择数据源" @click="showModal = true">
      <template #append>
        <el-button @click="showModal = true">选择</el-button>
      </template>
    </el-input>
    <app-modal v-model="showModal" id="dataSourceModal" :title="modalTitle">
      <div class="modal-body">
        <el-form name="editForm" label-width="90px">
          <!-- 数据源类型选择（仅在未传入 dataSourceType 时显示） -->
          <el-form-item v-if="!props.dataSourceType" label="数据源类型">
            <el-select v-model="selectedDataSourceType" placeholder="所有类型" style="width: 100%" @change="filterDataSources">
              <el-option v-for="type in dataSourceTypes" :key="type" :value="type" :label="type" />
            </el-select>
          </el-form-item>
          <el-form-item label="数据源">
            <el-select v-model="selectedDataSource" placeholder="请选择数据源" style="width: 100%" @change="fetchSchemas">
              <el-option v-for="source in filteredDataSources" :key="source.id" :value="source.id" :label="source.name" />
            </el-select>
          </el-form-item>
          <!-- Schema 选择 -->
          <el-form-item label="Schema">
            <el-select v-model="selectedSchema" placeholder="请选择 Schema" style="width: 100%" :disabled="!selectedDataSource">
              <el-option v-for="schema in schemas" :key="schema" :value="schema" :label="schema" />
            </el-select>
          </el-form-item>
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
