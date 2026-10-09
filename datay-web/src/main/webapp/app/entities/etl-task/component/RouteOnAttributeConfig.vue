<template>
  <el-form name="editForm" label-position="top">
    <el-form-item label="匹配模式">
      <el-radio-group v-model="formData.matchMode">
        <el-radio value="attribute">按属性名</el-radio>
        <el-radio value="value">按属性值</el-radio>
        <el-radio value="data">按数据项</el-radio>
      </el-radio-group>
      <small class="form-text text-muted">
        按属性名：FlowFile 存在与连线标签同名的属性时命中；按属性值：指定属性的值等于连线标签时命中；按数据项：逐条记录的字段值等于连线标签时命中。
      </small>
    </el-form-item>

    <el-form-item v-if="formData.matchMode === 'value'" label="属性名">
      <el-input id="attributeName" name="attributeName" v-model="formData.attributeName" placeholder="请输入用于取值的 FlowFile 属性名" />
      <small class="form-text text-muted">取该属性的值，与下游连线标签进行等值匹配。</small>
    </el-form-item>

    <el-form-item v-if="formData.matchMode === 'data'" label="匹配字段名">
      <el-input id="dataField" name="dataField" v-model="formData.dataField" placeholder="请输入记录中用于匹配的字段名，如 type" />
      <small class="form-text text-muted">逐条读取该字段的值，与下游连线标签匹配，支持一个 FlowFile 拆分到多条连线。</small>
    </el-form-item>

    <el-form-item label="兜底连线标签">
      <el-input id="defaultLabel" name="defaultLabel" v-model="formData.defaultLabel" placeholder="未匹配时兜底路由到的连线标签，默认 other" />
      <small class="form-text text-muted">默认 other，所有未匹配的数据都会走该标签对应的连线；该连线不存在时丢弃。</small>
    </el-form-item>

    <el-form-item label="使用说明">
      <div class="form-control-static">
        在下游连线上双击设置标签，本组件会按标签把 FlowFile（或记录）分流到对应连线，未匹配的走兜底标签连线。
      </div>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { reactive } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';

const props = defineProps({
  node: { type: Object, default: () => ({}) },
});

const emits = defineEmits(['save']);

const formData = reactive({
  matchMode: props.node?.data?.config?.matchMode || 'attribute',
  attributeName: props.node?.data?.config?.attributeName || '',
  dataField: props.node?.data?.config?.dataField || '',
  defaultLabel: props.node?.data?.config?.defaultLabel || 'other',
});

const saveConfig = async () => {
  if (formData.matchMode === 'value' && !formData.attributeName.trim()) {
    ElMessage.error('按属性值匹配时，请输入属性名');
    return;
  }
  if (formData.matchMode === 'data' && !formData.dataField.trim()) {
    ElMessage.error('按数据项匹配时，请输入匹配字段名');
    return;
  }
  emits('save', {
    matchMode: formData.matchMode,
    attributeName: formData.matchMode === 'value' ? formData.attributeName.trim() : '',
    dataField: formData.matchMode === 'data' ? formData.dataField.trim() : '',
    defaultLabel: (formData.defaultLabel || 'other').trim(),
  });
};

defineExpose({ saveConfig });
</script>

<style scoped>
.form-control-static {
  padding: 8px 12px;
  background-color: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
}
.form-text {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
</style>
