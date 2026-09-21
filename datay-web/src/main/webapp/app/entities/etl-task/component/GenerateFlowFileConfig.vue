<template>
  <form name="editForm" novalidate>
    <div>
      <!-- 数据格式选择 -->
      <div class="form-group">
        <label for="dataFormat">数据格式</label>
        <select class="form-control" v-model="formData.dataFormat" @change="handleDataFormatChange">
          <option v-for="item in formatOptions" :key="item.value" :value="item.value">
            {{ item.name }}
          </option>
        </select>
      </div>

      <!-- 输入数据 -->
      <div class="form-group">
        <label for="inputData">输入数据</label>
        <textarea
          class="form-control"
          id="inputData"
          name="inputData"
          v-model="formData.inputData"
          :rows="8"
          :maxlength="5000"
          placeholder="请输入要生成的数据内容"
        ></textarea>
      </div>

      <!-- CSV格式相关配置 -->
      <div v-if="formData.dataFormat === 'CSV'" class="form-group">
        <label for="delimiter">分隔符</label>
        <input
          type="text"
          class="form-control"
          id="delimiter"
          name="delimiter"
          v-model="formData.delimiter"
          placeholder="请输入分隔符，默认为逗号"
        />
      </div>

      <div v-if="formData.dataFormat === 'CSV'" class="form-group">
        <el-checkbox v-model="formData.hasHeader">包含表头 <span class="help-text">第一行作为表头处理</span></el-checkbox>
      </div>

      <!-- JSON对象格式相关配置 -->
      <div v-if="formData.dataFormat === 'JSON_OBJECT'" class="form-group">
        <label for="fieldMapping">字段映射</label>
        <textarea
          class="form-control"
          id="fieldMapping"
          name="fieldMapping"
          v-model="formData.fieldMapping"
          :rows="4"
          :maxlength="5000"
          placeholder="请输入字段映射，用逗号分隔，如：name,age,city"
        ></textarea>
        <span class="help-text">用于JSON对象格式的字段映射，与数据中的字段对应</span>
      </div>

      <!-- 循环输出配置 -->
      <div class="form-group">
        <label for="loopCount">循环输出次数</label>
        <input
          type="number"
          class="form-control"
          id="loopCount"
          name="loopCount"
          v-model="formData.loopCount"
          placeholder="请输入循环输出次数，默认为1"
        />
        <span class="help-text">设置为1表示单次输出，大于1表示循环输出</span>
      </div>

      <!-- <div v-if="formData.loopCount > 1" class="form-group">
        <label for="waitTime">每次输出等待时间 (毫秒)</label>
        <el-input-number
          v-model="formData.waitTime"
          :min="100"
          :max="60000"
          :step="100"
          style="width: 100%"
        />
        <span class="help-text">每次输出后的等待时间，用于控制输出频率</span>
      </div> -->
    </div>
  </form>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue';
import { defineProps, defineEmits } from 'vue';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save']);

// 写入策略选项
const formatOptions = [
  { name: '文本格式 (TEXT)', value: 'TEXT' },
  { name: 'CSV格式', value: 'CSV' },
  { name: 'JSON数组格式', value: 'JSON_ARRAY' },
  { name: 'JSON对象格式', value: 'JSON_OBJECT' },
];

// 配置项定义
const configItems = [
  {
    key: 'dataFormat',
    label: '数据格式',
    defaultValue: 'TEXT',
    required: true,
    description: '选择生成数据的格式类型',
    show: true,
    controlType: 'SELECT',
    parameterType: 'STRING',
    options: [
      { label: '文本格式 (TEXT)', value: 'TEXT' },
      { label: 'CSV格式', value: 'CSV' },
      { label: 'JSON数组格式', value: 'JSON_ARRAY' },
      { label: 'JSON对象格式', value: 'JSON_OBJECT' },
    ],
  },
  {
    key: 'inputData',
    label: '输入数据',
    defaultValue: '',
    required: true,
    description: '输入要生成的数据内容',
    show: true,
    maxLength: 5000,
    rows: 6,
    controlType: 'TEXTAREA',
    parameterType: 'STRING',
  },
  {
    key: 'delimiter',
    label: '分隔符',
    defaultValue: ',',
    required: false,
    description: 'CSV格式的分隔符',
    show: false,
    controlType: 'INPUT',
    parameterType: 'STRING',
    condition: { field: 'dataFormat', value: 'CSV' },
  },
  {
    key: 'hasHeader',
    label: '包含表头',
    defaultValue: false,
    required: false,
    description: 'CSV格式是否包含表头',
    show: false,
    controlType: 'CHECKBOX',
    parameterType: 'BOOLEAN',
    condition: { field: 'dataFormat', value: 'CSV' },
  },
  {
    key: 'fieldMapping',
    label: '字段映射',
    defaultValue: '',
    required: false,
    description: 'JSON对象格式的字段映射',
    show: false,
    controlType: 'INPUT',
    parameterType: 'STRING',
    condition: { field: 'dataFormat', value: 'JSON_OBJECT' },
  },
  {
    key: 'loopCount',
    label: '循环输出次数',
    defaultValue: 1,
    required: false,
    description: '循环输出次数，1表示单次输出',
    show: true,
    minValue: 1,
    maxValue: 1000,
    controlType: 'INPUTNUMBER',
    parameterType: 'INTEGER',
  },
  {
    key: 'waitTime',
    label: '等待时间',
    defaultValue: 1000,
    required: false,
    description: '每次输出后的等待时间(毫秒)',
    show: false,
    minValue: 100,
    maxValue: 60000,
    controlType: 'INPUTNUMBER',
    parameterType: 'INTEGER',
    condition: { field: 'loopCount', operator: '>', value: 1 },
  },
];

const formData = reactive({
  dataFormat: props.node?.data?.config?.dataFormat || 'TEXT',
  inputData: props.node?.data?.config?.inputData || '',
  delimiter: props.node?.data?.config?.delimiter || ',',
  hasHeader: props.node?.data?.config?.hasHeader || false,
  fieldMapping: props.node?.data?.config?.fieldMapping || '',
  loopCount: props.node?.data?.config?.loopCount || 1,
  waitTime: props.node?.data?.config?.waitTime || 1000,
});

// 数据预览
const previewData = computed(() => {
  if (!formData.inputData) return '暂无数据';

  try {
    switch (formData.dataFormat) {
      case 'JSON_ARRAY':
        return JSON.stringify(JSON.parse(formData.inputData), null, 2);
      case 'JSON_OBJECT':
        return JSON.stringify(JSON.parse(formData.inputData), null, 2);
      default:
        return formData.inputData;
    }
  } catch (e) {
    return formData.inputData;
  }
});

// 监听数据格式变化
const handleDataFormatChange = value => {
  // 切换格式时重置相关配置
  if (value !== 'CSV') {
    formData.delimiter = ',';
    formData.hasHeader = false;
  }
  if (value !== 'JSON_OBJECT') {
    formData.fieldMapping = '';
  }
};

// 监听循环次数变化
watch(
  () => formData.loopCount,
  newVal => {
    if (newVal <= 1) {
      formData.waitTime = 1000;
    }
  },
);

const saveConfig = async () => {
  // 验证必填项
  if (!formData.inputData.trim()) {
    ElMessage.error('请输入数据内容');
    return;
  }

  if (!formData.dataFormat) {
    ElMessage.error('请选择数据格式');
    return;
  }

  // 验证JSON格式
  if (formData.dataFormat === 'JSON_ARRAY' || formData.dataFormat === 'JSON_OBJECT') {
    try {
      JSON.parse(formData.inputData);
    } catch (e) {
      ElMessage.error('JSON格式不正确，请检查输入数据');
      return;
    }
  }

  emits('save', formData);
};

defineExpose({ saveConfig });
</script>

<style scoped>
.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 5px;
  font-weight: bold;
}

.help-text {
  font-size: 12px;
  color: #666;
  margin-left: 10px;
}

.preview-container {
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 10px;
  background-color: #f9f9f9;
  max-height: 200px;
  overflow-y: auto;
}

.preview-container pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-size: 12px;
}

.form-actions {
  margin-top: 30px;
  text-align: right;
}
</style>