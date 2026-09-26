<template>
  <el-form name="editForm" label-position="top">
    <el-form-item label="Bootstrap Servers">
      <el-input id="bootstrapServers" name="bootstrapServers" v-model="formData.bootstrapServers" placeholder="localhost:9092" required />
      <small class="form-text text-muted"> Kafka broker地址，多个用逗号分隔 </small>
    </el-form-item>
    <el-form-item label="Topic">
      <el-input id="topic" name="topic" v-model="formData.topic" placeholder="请输入Kafka Topic名称" required />
    </el-form-item>
    <el-form-item label="消费组ID">
      <el-input id="groupId" name="groupId" v-model="formData.groupId" placeholder="datay-consumer" />
    </el-form-item>
    <el-form-item label="Offset重置策略">
      <el-select v-model="formData.autoOffsetReset">
            <el-option value="earliest" label="earliest - 从最早开始消费" />
            <el-option value="latest" label="latest - 从最新开始消费" />
            <el-option value="none" label="none - 必须已有消费记录" />
          </el-select>
    </el-form-item>
    <el-form-item label="启用自动提交">
      <el-switch v-model="formData.enableAutoCommit" />
    </el-form-item>
    <el-form-item label="批量大小">
      <el-input-number :controls="false"  id="batchSize" name="batchSize" v-model="formData.batchSize" ::min="1" ::max="10000" />
      <small class="form-text text-muted"> 每次消费的最大消息数 </small>
    </el-form-item>
    <el-form-item label="消费超时 (ms)">
      <el-input-number :controls="false"  id="consumerTimeoutMs" name="consumerTimeoutMs" v-model="formData.consumerTimeoutMs" ::min="1000" />
    </el-form-item>
    <el-form-item label="连接超时 (ms)">
      <el-input-number :controls="false"  id="connectionTimeoutMs" name="connectionTimeoutMs" v-model="formData.connectionTimeoutMs" ::min="1000" />
    </el-form-item>
    <el-form-item label="指定分区">
      <el-input-number :controls="false"  id="partition" name="partition" v-model="formData.partition" ::min="-1" placeholder="-1表示不指定" />
      <small class="form-text text-muted"> -1表示不指定分区，消费所有分区 </small>
    </el-form-item>
    <el-form-item label="起始Offset">
      <el-input-number :controls="false"  id="startOffset" name="startOffset" v-model="formData.startOffset" placeholder="留空不指定" />
      <small class="form-text text-muted"> 指定消费的起始Offset，留空则使用autoOffsetReset策略 </small>
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
  bootstrapServers: props.node?.data?.config?.bootstrapServers || 'localhost:9092',
  topic: props.node?.data?.config?.topic || '',
  groupId: props.node?.data?.config?.groupId || 'datay-consumer',
  autoOffsetReset: props.node?.data?.config?.autoOffsetReset || 'earliest',
  enableAutoCommit: props.node?.data?.config?.enableAutoCommit !== false,
  batchSize: props.node?.data?.config?.batchSize || 100,
  consumerTimeoutMs: props.node?.data?.config?.consumerTimeoutMs || 30000,
  connectionTimeoutMs: props.node?.data?.config?.connectionTimeoutMs || 5000,
  partition: props.node?.data?.config?.partition ?? -1,
  startOffset: props.node?.data?.config?.startOffset ?? null,
});

const saveConfig = async () => {
  if (!formData.bootstrapServers.trim()) {
    ElMessage.error('请输入Bootstrap Servers');
    return;
  }
  if (!formData.topic.trim()) {
    ElMessage.error('请输入Topic');
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
