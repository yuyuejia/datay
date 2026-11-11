<template>
  <el-form name="editForm" label-position="top">
    <el-form-item label="Broker 地址">
      <el-input id="brokerUrl" name="brokerUrl" v-model="formData.brokerUrl" placeholder="tcp://localhost:1883" required />
      <small class="form-text text-muted"> 支持 tcp://、ssl://、ws://、wss://，例如 ssl://broker.example.com:8883 </small>
    </el-form-item>
    <el-form-item label="ClientId">
      <el-input id="clientId" name="clientId" v-model="formData.clientId" placeholder="留空则自动生成" />
      <small class="form-text text-muted"> 启用持久会话（Clean Session 关闭）时，请配置稳定且唯一的 ClientId </small>
    </el-form-item>
    <el-form-item label="订阅主题">
      <el-input id="topic" name="topic" v-model="formData.topic" placeholder="例如：device/+/telemetry" required />
      <small class="form-text text-muted"> 支持 MQTT 通配符 + 和 # </small>
    </el-form-item>
    <el-form-item label="QoS">
      <el-select v-model="formData.qos">
        <el-option :value="0" label="0 - 最多一次" />
        <el-option :value="1" label="1 - 至少一次" />
        <el-option :value="2" label="2 - 恰好一次" />
      </el-select>
    </el-form-item>
    <el-form-item label="用户名">
      <el-input id="username" name="username" v-model="formData.username" placeholder="可选" />
    </el-form-item>
    <el-form-item label="密码">
      <el-input id="password" name="password" v-model="formData.password" type="password" show-password placeholder="可选" />
    </el-form-item>
    <el-form-item label="Clean Session">
      <el-switch v-model="formData.cleanSession" />
      <small class="form-text text-muted"> 关闭后可保留会话状态，需要稳定的 ClientId </small>
    </el-form-item>
    <el-form-item label="自动重连">
      <el-switch v-model="formData.automaticReconnect" />
    </el-form-item>
    <el-form-item label="KeepAlive 间隔（秒）">
      <el-input-number :controls="false" id="keepAliveInterval" name="keepAliveInterval" v-model="formData.keepAliveInterval" :min="5" />
    </el-form-item>
    <el-form-item label="连接超时（秒）">
      <el-input-number :controls="false" id="connectionTimeout" name="connectionTimeout" v-model="formData.connectionTimeout" :min="1" />
    </el-form-item>
    <el-form-item label="批量大小">
      <el-input-number :controls="false" id="batchSize" name="batchSize" v-model="formData.batchSize" :min="1" />
      <small class="form-text text-muted"> 每批发送给下游的最大消息数 </small>
    </el-form-item>
    <el-form-item label="消费等待超时（ms）">
      <el-input-number :controls="false" id="consumerTimeoutMs" name="consumerTimeoutMs" v-model="formData.consumerTimeoutMs" :min="100" />
    </el-form-item>
    <el-form-item label="消息队列容量">
      <el-input-number :controls="false" id="maxQueueSize" name="maxQueueSize" v-model="formData.maxQueueSize" :min="1" />
      <small class="form-text text-muted"> 回调线程接收消息的缓冲队列大小，队列满时会丢弃并记录告警 </small>
    </el-form-item>
    <el-form-item label="消息体格式">
      <el-select v-model="formData.payloadFormat">
        <el-option value="AUTO" label="AUTO - 自动识别 JSON / 文本" />
        <el-option value="JSON" label="JSON" />
        <el-option value="TEXT" label="TEXT" />
      </el-select>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { reactive } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';

const props = defineProps({
  node: {
    type: Object,
    default: () => ({}),
  },
});

const emits = defineEmits(['save']);

const formData = reactive({
  brokerUrl: props.node?.data?.config?.brokerUrl || 'tcp://localhost:1883',
  clientId: props.node?.data?.config?.clientId || '',
  topic: props.node?.data?.config?.topic || '',
  qos: props.node?.data?.config?.qos ?? 1,
  username: props.node?.data?.config?.username || '',
  password: props.node?.data?.config?.password || '',
  cleanSession: props.node?.data?.config?.cleanSession !== false,
  automaticReconnect: props.node?.data?.config?.automaticReconnect !== false,
  keepAliveInterval: props.node?.data?.config?.keepAliveInterval || 60,
  connectionTimeout: props.node?.data?.config?.connectionTimeout || 10,
  batchSize: props.node?.data?.config?.batchSize || 100,
  consumerTimeoutMs: props.node?.data?.config?.consumerTimeoutMs || 1000,
  maxQueueSize: props.node?.data?.config?.maxQueueSize || 10000,
  payloadFormat: props.node?.data?.config?.payloadFormat || 'AUTO',
});

const saveConfig = async () => {
  if (!formData.brokerUrl.trim()) {
    ElMessage.error('请输入 MQTT Broker 地址');
    return;
  }
  if (!/^(tcp|ssl|ws|wss):\/\//i.test(formData.brokerUrl.trim())) {
    ElMessage.error('Broker 地址必须以 tcp://、ssl://、ws:// 或 wss:// 开头');
    return;
  }
  if (!formData.topic.trim()) {
    ElMessage.error('请输入订阅主题');
    return;
  }
  emits('save', formData);
};

defineExpose({ saveConfig });
</script>

<style scoped>
.form-text {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
</style>
