<template>
  <form name="editForm" novalidate>
    <div>
      <!-- 日志级别选择 -->
      <!-- <div class="form-group">
        <label for="logLevel">日志级别</label>
        <el-select
          v-model="formData.logLevel"
          placeholder="请选择日志级别"
          style="width: 100%"
        >
          <el-option label="DEBUG" value="DEBUG" />
          <el-option label="INFO" value="INFO" />
          <el-option label="WARN" value="WARN" />
          <el-option label="ERROR" value="ERROR" />
        </el-select>
        <span class="help-text">设置日志记录的详细程度</span>
      </div> -->

      <!-- 记录选项 -->
      <!-- <div class="form-group">
        <label>记录选项</label>
        <div class="checkbox-group">
          <el-checkbox v-model="formData.logDataContent">记录数据内容</el-checkbox>
          <span class="help-text">是否记录FlowFile的数据内容</span>
        </div>
        
        <div class="checkbox-group">
          <el-checkbox v-model="formData.logAttributes">记录属性信息</el-checkbox>
          <span class="help-text">是否记录FlowFile的属性信息</span>
        </div>
        
        <div class="checkbox-group">
          <el-checkbox v-model="formData.logDataFormat">记录数据格式</el-checkbox>
          <span class="help-text">是否记录数据格式信息</span>
        </div>
        
        <div class="checkbox-group">
          <el-checkbox v-model="formData.logTimestamp">记录时间戳</el-checkbox>
          <span class="help-text">是否记录时间戳信息</span>
        </div>
      </div> -->

      <!-- 最大数据记录长度 -->
      <div class="form-group">
        <label for="maxDataLength">最大数据记录长度</label>
        <input type="number" class="form-control" id="maxDataLength" name="maxDataLength" v-model="formData.maxDataLength" />
        <span class="help-text">设置记录数据内容的最大长度，避免日志过大</span>
      </div>
    </div>
  </form>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { defineProps, defineEmits } from 'vue';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save']);

// 配置项定义
const configItems = [
  {
    key: 'logLevel',
    label: '日志级别',
    defaultValue: 'INFO',
    required: true,
    description: '设置日志记录的详细程度',
    show: true,
    controlType: 'SELECT',
    parameterType: 'STRING',
    options: [
      { label: 'DEBUG', value: 'DEBUG' },
      { label: 'INFO', value: 'INFO' },
      { label: 'WARN', value: 'WARN' },
      { label: 'ERROR', value: 'ERROR' },
    ],
  },
  {
    key: 'logDataContent',
    label: '记录数据内容',
    defaultValue: true,
    required: false,
    description: '是否记录FlowFile的数据内容',
    show: true,
    controlType: 'CHECKBOX',
    parameterType: 'BOOLEAN',
  },
  {
    key: 'logAttributes',
    label: '记录属性信息',
    defaultValue: true,
    required: false,
    description: '是否记录FlowFile的属性信息',
    show: true,
    controlType: 'CHECKBOX',
    parameterType: 'BOOLEAN',
  },
  {
    key: 'logDataFormat',
    label: '记录数据格式',
    defaultValue: true,
    required: false,
    description: '是否记录数据格式信息',
    show: true,
    controlType: 'CHECKBOX',
    parameterType: 'BOOLEAN',
  },
  {
    key: 'logTimestamp',
    label: '记录时间戳',
    defaultValue: true,
    required: false,
    description: '是否记录时间戳信息',
    show: true,
    controlType: 'CHECKBOX',
    parameterType: 'BOOLEAN',
  },
  {
    key: 'maxDataLength',
    label: '最大数据记录长度',
    defaultValue: 5000,
    required: false,
    description: '设置记录数据内容的最大长度',
    show: true,
    minValue: 100,
    maxValue: 10000,
    controlType: 'INPUTNUMBER',
    parameterType: 'INTEGER',
  },
];

const formData = reactive({
  logLevel: props.node?.data?.config?.logLevel || 'INFO',
  logDataContent: props.node?.data?.config?.logDataContent !== undefined ? props.node?.data?.config?.logDataContent : true,
  logAttributes: props.node?.data?.config?.logAttributes !== undefined ? props.node?.data?.config?.logAttributes : true,
  logDataFormat: props.node?.data?.config?.logDataFormat !== undefined ? props.node?.data?.config?.logDataFormat : true,
  logTimestamp: props.node?.data?.config?.logTimestamp !== undefined ? props.node?.data?.config?.logTimestamp : true,
  maxDataLength: props.node?.data?.config?.maxDataLength || 5000,
});

const saveConfig = async () => {
  // 验证必填项
  if (!formData.logLevel) {
    ElMessage.error('请选择日志级别');
    return;
  }

  // 验证数值范围
  if (formData.maxDataLength < 100 || formData.maxDataLength > 10000) {
    ElMessage.error('最大数据记录长度必须在100-10000之间');
    return;
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

.checkbox-group {
  margin-bottom: 10px;
  display: flex;
  align-items: center;
}

.preview-container {
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 15px;
  background-color: #f9f9f9;
}

.preview-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #eee;
}

.preview-item:last-child {
  border-bottom: none;
  margin-bottom: 0;
}

.preview-label {
  font-weight: bold;
  color: #333;
}

.preview-value {
  color: #666;
}

.info-container {
  border: 1px solid #e6f7ff;
  border-radius: 4px;
  padding: 15px;
  background-color: #f0f8ff;
}

.info-container p {
  margin: 0 0 10px 0;
  color: #1890ff;
  font-weight: bold;
}

.info-container ul {
  margin: 0;
  padding-left: 20px;
}

.info-container li {
  margin-bottom: 5px;
  color: #666;
  line-height: 1.5;
}

.form-actions {
  margin-top: 30px;
  text-align: right;
}
</style>
