<template>
  <el-form name="editForm" label-position="top">
    <el-form-item label="数据源">
      <DataSourceSelector
        type="source"
        :datasourceId="formData.sourceId"
        :schema="formData.schema"
        dataSourceType="INFLUXDB"
        @selected="handleDataSourceSelected"
      />
      <small class="form-text text-muted"> 选择 InfluxDB 数据源，Schema 即 Bucket（2.x）/ Database（1.x） </small>
    </el-form-item>
    <el-form-item label="measurement（表名）">
      <el-input id="measurement" name="measurement" v-model="formData.measurement" placeholder="例如：cpu；填写自定义查询语句时可留空" />
      <small class="form-text text-muted"> 要读取的 measurement（表）名，默认按此构建查询 </small>
    </el-form-item>
    <el-form-item label="自定义查询语句">
      <el-input
        id="query"
        name="query"
        type="textarea"
        :rows="4"
        v-model="formData.query"
        placeholder='可选。2.x 填写 Flux，例如：from(bucket:"mybucket") |> range(start:-1h)；1.x 填写 InfluxQL'
      />
      <small class="form-text text-muted"> 留空则按 Schema + measurement + 起止时间自动生成查询语句 </small>
    </el-form-item>
    <el-form-item label="起始时间 (start)">
      <el-input id="start" name="start" v-model="formData.start" placeholder="默认 -30d" />
      <small class="form-text text-muted"> Flux 相对时间如 -30d/-1h，或绝对时间如 2024-01-01T00:00:00Z </small>
    </el-form-item>
    <el-form-item label="结束时间 (stop)">
      <el-input id="stop" name="stop" v-model="formData.stop" placeholder="可选，默认到当前时间" />
    </el-form-item>
    <el-form-item label="批大小 (batchSize)">
      <el-input id="batchSize" name="batchSize" v-model="formData.batchSize" placeholder="默认 1000" />
    </el-form-item>
  </el-form>
</template>

<script setup>
import { reactive } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';
import DataSourceSelector from '@/components/DataSourceSelector.vue';

const props = defineProps({ node: Object });
const emits = defineEmits(['save']);

const formData = reactive({
  sourceId: props.node?.data?.config?.sourceId,
  schema: props.node?.data?.config?.schema || '',
  measurement: props.node?.data?.config?.measurement || '',
  query: props.node?.data?.config?.query || '',
  start: props.node?.data?.config?.start || '-30d',
  stop: props.node?.data?.config?.stop || '',
  batchSize: props.node?.data?.config?.batchSize || '1000',
});

const handleDataSourceSelected = selectedData => {
  formData.sourceId = selectedData.dataSourceId;
  formData.schema = selectedData.schema;
};

const saveConfig = async () => {
  if (!formData.sourceId) {
    ElMessage.error('请选择数据源');
    return;
  }
  if (!formData.query && !formData.measurement) {
    ElMessage.error('请填写 measurement 或自定义查询语句');
    return;
  }
  emits('save', {
    sourceId: formData.sourceId,
    schema: formData.schema,
    measurement: formData.measurement,
    query: formData.query,
    start: formData.start,
    stop: formData.stop,
    batchSize: formData.batchSize,
  });
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
