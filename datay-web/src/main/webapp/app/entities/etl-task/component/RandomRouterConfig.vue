<template>
  <el-form name="editForm" label-position="top">
    <el-form-item label="随机路由说明">
      <div class="form-control-static">
        该组件将FlowFile随机路由到下游连接中的一个，无需额外配置。
      </div>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { defineProps, defineEmits } from 'vue';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save']);

const formData = reactive({});

const saveConfig = async () => {
  emits('save', formData);
};

defineExpose({ saveConfig });
</script>

<style scoped>
.form-group {
  margin-bottom: 20px;
}
.form-control-static {
  padding: 8px 12px;
  background-color: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
}
.form-actions {
  margin-top: 30px;
  text-align: right;
}
</style>