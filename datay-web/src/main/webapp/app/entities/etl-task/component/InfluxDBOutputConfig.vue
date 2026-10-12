<template>
  <el-form name="editForm" label-position="top">
    <el-form-item label="目标数据源">
      <DataSourceSelector
        type="target"
        :datasourceId="formData.sourceId"
        :schema="formData.schema"
        dataSourceType="INFLUXDB"
        @selected="handleDataSourceSelected"
      />
      <small class="form-text text-muted"> 选择 InfluxDB 数据源作为输出目标，Schema 即 Bucket（2.x）/ Database（1.x） </small>
    </el-form-item>
    <el-form-item label="目标 measurement（表名）">
      <el-input id="table" name="table" v-model="formData.table" placeholder="例如：cpu，留空则使用上游表名" />
      <small class="form-text text-muted"> 写入的 measurement 名，不存在时自动创建 </small>
    </el-form-item>
    <el-form-item label="写入模式">
      <el-select v-model="formData.model">
        <el-option value="append" label="追加写入 (append)" />
        <el-option value="overwrite" label="覆盖写入 (overwrite)" />
      </el-select>
      <small class="form-text text-muted"> append: 追加写入；overwrite: 首批写入前删除同名 measurement 的数据 </small>
    </el-form-item>
    <el-form-item label="时间戳列">
      <el-input id="timeColumn" name="timeColumn" v-model="formData.timeColumn" placeholder="可选，用作文档时间戳的列名，如 _time" />
      <small class="form-text text-muted"> 留空则由 InfluxDB 使用当前时间；填写后按该列值作为时间戳 </small>
    </el-form-item>
    <el-form-item label="时间戳单位">
      <el-select v-model="formData.timeUnit">
        <el-option value="s" label="秒 (s)" />
        <el-option value="ms" label="毫秒 (ms)" />
        <el-option value="us" label="微秒 (us)" />
        <el-option value="ns" label="纳秒 (ns)" />
      </el-select>
      <small class="form-text text-muted"> 仅当时间戳列为数值时生效；时间戳列为 ISO 时间字符串时自动识别 </small>
    </el-form-item>
    <el-form-item label="Tag 列">
      <el-input id="tagColumns" name="tagColumns" v-model="formData.tagColumns" placeholder="可选，逗号分隔，如 host,region" />
      <small class="form-text text-muted"> 这些列将作为 InfluxDB 的 tag（索引列），其余列作为 field </small>
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
  table: props.node?.data?.config?.table || '',
  model: props.node?.data?.config?.model || 'append',
  timeColumn: props.node?.data?.config?.timeColumn || '',
  timeUnit: props.node?.data?.config?.timeUnit || 's',
  tagColumns: props.node?.data?.config?.tagColumns || '',
});

const handleDataSourceSelected = selectedData => {
  formData.sourceId = selectedData.dataSourceId;
  formData.schema = selectedData.schema;
};

const saveConfig = async () => {
  if (!formData.sourceId) {
    ElMessage.error('请选择目标数据源');
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
