<template>
  <form name="editForm" novalidate>
    <div class="form-group">
      <label for="bootstrapServers">Bootstrap Servers</label>
      <input type="text" class="form-control" id="bootstrapServers" name="bootstrapServers" v-model="formData.bootstrapServers" placeholder="localhost:9092" required />
      <small class="form-text text-muted"> Kafka broker地址，多个用逗号分隔 </small>
    </div>
    <div class="form-group">
      <label for="topic">Topic</label>
      <input type="text" class="form-control" id="topic" name="topic" v-model="formData.topic" placeholder="请输入Kafka Topic名称" required />
    </div>
    <div class="form-group">
      <label for="groupId">消费组ID</label>
      <input type="text" class="form-control" id="groupId" name="groupId" v-model="formData.groupId" placeholder="datay-consumer" />
    </div>
    <div class="form-group">
      <label for="autoOffsetReset">Offset重置策略</label>
      <select class="form-control" v-model="formData.autoOffsetReset">
        <option value="earliest">earliest - 从最早开始消费</option>
        <option value="latest">latest - 从最新开始消费</option>
        <option value="none">none - 必须已有消费记录</option>
      </select>
    </div>
    <div class="form-group">
      <label for="enableAutoCommit">启用自动提交</label>
      <el-switch v-model="formData.enableAutoCommit" />
    </div>
    <div class="form-group">
      <label for="batchSize">批量大小</label>
      <input type="number" class="form-control" id="batchSize" name="batchSize" v-model.number="formData.batchSize" :min="1" :max="10000" />
      <small class="form-text text-muted"> 每次消费的最大消息数 </small>
    </div>
    <div class="form-group">
      <label for="consumerTimeoutMs">消费超时 (ms)</label>
      <input type="number" class="form-control" id="consumerTimeoutMs" name="consumerTimeoutMs" v-model.number="formData.consumerTimeoutMs" :min="1000" />
    </div>
    <div class="form-group">
      <label for="connectionTimeoutMs">连接超时 (ms)</label>
      <input type="number" class="form-control" id="connectionTimeoutMs" name="connectionTimeoutMs" v-model.number="formData.connectionTimeoutMs" :min="1000" />
    </div>
    <div class="form-group">
      <label for="partition">指定分区</label>
      <input type="number" class="form-control" id="partition" name="partition" v-model.number="formData.partition" :min="-1" placeholder="-1表示不指定" />
      <small class="form-text text-muted"> -1表示不指定分区，消费所有分区 </small>
    </div>
    <div class="form-group">
      <label for="startOffset">起始Offset</label>
      <input type="number" class="form-control" id="startOffset" name="startOffset" v-model.number="formData.startOffset" placeholder="留空不指定" />
      <small class="form-text text-muted"> 指定消费的起始Offset，留空则使用autoOffsetReset策略 </small>
    </div>
  </form>
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
