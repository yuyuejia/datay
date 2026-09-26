<template>
  <div>
    <!-- 单选模式：带“选择”按钮的输入框 -->
    <el-input v-if="!props.multiple" :model-value="selectedField" placeholder="请选择字段" @input="confirmInput">
      <template #append>
        <el-button @click="showModal = true">选择</el-button>
      </template>
    </el-input>
    <!-- 多选模式：输入框内展示可删除的已选字段 + 右侧“选择字段”按钮 -->
    <div v-else class="field-multi-input" @click="showModal = true">
      <el-tag
        v-for="field in selectedFieldObjects"
        :key="field.name"
        closable
        size="small"
        :disable-transitions="true"
        @close.stop="removeField(field)"
      >
        {{ field.name }}
      </el-tag>
      <span v-if="!selectedFieldObjects.length" class="field-multi-placeholder">请选择字段</span>
      <el-button class="field-multi-input-btn" @click.stop="showModal = true">选择字段</el-button>
    </div>

    <app-modal v-model="showModal" id="fieldSelectorModal" title="选择表字段">
      <div class="modal-body">
        <div v-if="loading" class="text-center">
          <div class="spinner-border" role="status">
            <span class="visually-hidden">加载中...</span>
          </div>
        </div>
        <div v-else-if="fields.length === 0" class="text-center text-muted">暂无字段数据</div>
        <div v-else>
          <div class="mb-3">
            <el-input v-model="searchKeyword" placeholder="搜索字段..." clearable @input="filterFields" />
          </div>
          <div class="field-list" style="max-height: 300px; overflow-y: auto">
            <div v-for="field in filteredFields" :key="field.name" class="field-item mb-2">
              <div class="form-check d-flex align-items-start">
                <el-checkbox
                  v-if="props.multiple"
                  :model-value="isFieldSelected(field)"
                  class="me-2"
                  @change="val => toggleField(field, val)"
                />
                <el-radio
                  v-else
                  :model-value="isFieldSelected(field) ? field.name : null"
                  :value="field.name"
                  class="me-2"
                  @change="() => selectSingleField(field)"
                ><span /></el-radio>
                <div class="d-flex justify-content-between w-100">
                  <span class="field-name">{{ field.name }}</span>
                  <span class="field-type text-muted">{{ field.type }}</span>
                </div>
              </div>
              <div v-if="field.comment" class="field-comment text-muted small ms-3">
                {{ field.comment }}
              </div>
            </div>
          </div>
        </div>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="showModal = false">取消</el-button>
          <el-button type="primary" @click="confirmSelection">确定</el-button>
        </div>
      </template>
    </app-modal>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted, watch } from 'vue';
import DataSourceService from '@/entities/data-source/data-source.service';

const props = defineProps<{
  dataSourceId: number | null;
  schema: string | null;
  table: string | null;
  selectedFields: any[];
  selectedField: string | null;
  multiple: boolean;
}>();

const emit = defineEmits(['selected']);

const dataSourceService = new DataSourceService();
const showModal = ref(false);
const fields = ref<any[]>([]);
const filteredFields = ref<any[]>([]);
const selectedFieldObjects = ref<any>(props.selectedFields || []);
const selectedField = ref<string | null>(props.selectedField);
const loading = ref(false);
const searchKeyword = ref('');

const fetchFields = async () => {
  try {
    if (props.dataSourceId && props.schema && props.table) {
      loading.value = true;
      const res = await dataSourceService.getFields(props.dataSourceId, props.schema, props.table);
      fields.value = res.data;
      filteredFields.value = res.data;

      // 当字段数据加载完成后，设置已选中的字段
      if (props.multiple) {
        selectedFieldObjects.value = props.selectedFields?.filter(selected => fields.value.some(field => field.name === selected.name)) || [];
      } else if (props.selectedField) {
        const field = fields.value.find(f => f.name === props.selectedField);
        if (field) {
          selectedFieldObjects.value = field;
        }
      }
    } else {
      fields.value = [];
      filteredFields.value = [];
      selectedFieldObjects.value = [];
    }
  } catch (error) {
    console.error('获取字段清单失败', error);
    fields.value = [];
    filteredFields.value = [];
  } finally {
    loading.value = false;
  }
};

const filterFields = () => {
  if (!searchKeyword.value.trim()) {
    filteredFields.value = fields.value;
    return;
  }

  const keyword = searchKeyword.value.toLowerCase();
  filteredFields.value = fields.value.filter(
    field =>
      field.name.toLowerCase().includes(keyword) ||
      (field.comment && field.comment.toLowerCase().includes(keyword)) ||
      (field.type && field.type.toLowerCase().includes(keyword)),
  );
};

onMounted(() => {
  fetchFields();
});

// 监听 dataSourceId、schema 和 table 的变化
watch([() => props.dataSourceId, () => props.schema, () => props.table], () => {
  fetchFields();
});

const isFieldSelected = (field: any) => {
  if (props.multiple) {
    return Array.isArray(selectedFieldObjects.value) && selectedFieldObjects.value.some(f => f.name === field.name);
  }
  return selectedFieldObjects.value?.name === field.name;
};

const toggleField = (field: any, checked: boolean) => {
  if (!props.multiple) {
    return;
  }
  const current: any[] = Array.isArray(selectedFieldObjects.value) ? selectedFieldObjects.value : [];
  if (checked) {
    if (!current.some(f => f.name === field.name)) {
      selectedFieldObjects.value = [...current, field];
    }
  } else {
    selectedFieldObjects.value = current.filter(f => f.name !== field.name);
  }
};

const selectSingleField = (field: any) => {
  selectedFieldObjects.value = field;
};

const confirmSelection = () => {
  const selection = selectedFieldObjects.value;

  if (!props.multiple && selection) {
    selectedField.value = selection.name;
  }

  emit('selected', selection);
  showModal.value = false;
};

const confirmInput = (value: string) => {
  if (value) {
    selectedField.value = value;
    emit('selected', { name: value });
  }
};

const removeField = (field: any) => {
  selectedFieldObjects.value = selectedFieldObjects.value.filter(f => f.name !== field.name);
  emit('selected', selectedFieldObjects.value);
};
</script>

<style scoped>
.field-multi-input {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  min-height: 32px;
  padding: 3px 4px 3px 8px;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 4px;
  background: var(--el-fill-color-blank, #fff);
  cursor: pointer;
  transition: border-color 0.2s;
}

.field-multi-input:hover {
  border-color: var(--el-border-color-hover, #c0c4cc);
}

.field-multi-placeholder {
  color: var(--el-text-color-placeholder, #a8abb2);
  font-size: 14px;
  line-height: 24px;
}

.field-multi-input-btn {
  margin: -3px -4px -3px auto;
  align-self: stretch;
  flex: none;
  height: auto;
  border: 0;
  border-left: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 0 3px 3px 0;
  background: var(--el-fill-color-light, #f5f7fa);
  color: var(--el-text-color-regular, #606266);
}

.field-multi-input-btn:hover,
.field-multi-input-btn:focus {
  border-color: var(--el-border-color, #dcdfe6);
  background: var(--el-fill-color, #f0f2f5);
  color: var(--el-color-primary, #409eff);
}

.field-item {
  padding: 8px;
  border: 1px solid #e9ecef;
  border-radius: 4px;
}

.field-item:hover {
  background-color: #f8f9fa;
}

.field-name {
  font-weight: 500;
}

.field-type {
  font-size: 0.875rem;
}

.field-comment {
  margin-top: 4px;
}
</style>
