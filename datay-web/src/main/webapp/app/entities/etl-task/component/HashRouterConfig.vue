<template>
  <el-form name="editForm" label-position="top">
    <el-form-item label="哈希属性名">
      <el-input id="hashKey" name="hashKey" v-model="formData.hashKey" placeholder="请输入用于路由的FlowFile属性名" />
      <small class="form-text text-muted"> 根据该属性值进行哈希路由，将数据分发到不同下游 </small>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save']);

const formData = reactive({
  hashKey: props.node?.data?.config?.hashKey || '',
});

const saveConfig = async () => {
  if (!formData.hashKey.trim()) {
    ElMessage.error('请输入哈希属性名');
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
.form-control {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
}
.form-control:focus {
  border-color: #409eff;
  outline: none;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}
.form-text {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
.form-actions {
  margin-top: 30px;
  text-align: right;
}
</style>