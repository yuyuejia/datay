<template>
  <div>
    <div class="page-toolbar" :class="{ 'page-toolbar-embedded': props.embedded }">
      <h2 v-if="!props.embedded" id="page-heading" class="d-flex align-items-center flex-nowrap flex-wrap-nowrap mb-0">
        <span id="data-quality-rule-heading">质量规则</span>
      </h2>
      <div class="d-flex align-items-center">
        <el-input
          v-model="search"
          class="mr-2"
          style="width: 240px"
          placeholder="按名称 / 编码 / 说明搜索"
          clearable
          @keyup.enter="retrieve"
          @clear="retrieve"
        />
        <el-select v-model="level" class="mr-2" style="width: 130px" placeholder="规则级别" clearable @change="retrieve">
          <el-option value="TABLE" label="表级规则" />
          <el-option value="FIELD" label="字段级规则" />
        </el-select>
        <el-select v-model="type" class="mr-2" style="width: 170px" placeholder="规则类型" clearable @change="retrieve">
          <el-option v-for="item in ruleTypes" :key="item.code" :value="item.code" :label="item.label" />
        </el-select>
        <el-button class="mr-2" type="info" @click="retrieve" :disabled="isFetching">
          <font-awesome-icon icon="sync" :spin="isFetching"></font-awesome-icon>
          <span>刷新</span>
        </el-button>
        <router-link :to="{ name: 'DataQualityRuleCreate' }" custom v-slot="{ navigate }">
          <el-button type="primary" @click="navigate">
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>新建规则</span>
          </el-button>
        </router-link>
      </div>
    </div>

    <div class="alert alert-warning" v-if="!isFetching && rules.length === 0">未找到质量规则</div>

    <div v-if="rules.length > 0" class="list-page-scroll">
      <el-table :data="rules" style="width: 100%">
        <el-table-column prop="ruleName" label="规则名称" min-width="180">
          <template #default="scope">
            <div class="rule-name-cell">
              <span>{{ scope.row.ruleName }}</span>
              <el-tag v-if="scope.row.builtin" size="small" type="success" effect="plain">预置</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="ruleCode" label="规则编码" min-width="200" show-overflow-tooltip />
        <el-table-column label="级别" width="100">
          <template #default="scope">
            <span v-if="isCustomSql(scope.row.ruleType)">-</span>
            <el-tag v-else :type="scope.row.ruleLevel === 'TABLE' ? 'warning' : 'primary'" effect="plain">
              {{ scope.row.ruleLevel === 'TABLE' ? '表级' : '字段级' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="规则类型" width="150">
          <template #default="scope">{{ typeLabel(scope.row.ruleType) }}</template>
        </el-table-column>
        <el-table-column label="严重级别" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.severity === 'WARN' ? 'warning' : 'danger'" effect="plain">{{ scope.row.severity || 'ERROR' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip />
        <el-table-column label="启用" width="80">
          <template #default="scope">
            <el-tag :type="scope.row.enabled !== false ? 'success' : 'info'">{{ scope.row.enabled !== false ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="scope">{{ formatDate(scope.row.updateTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="200">
          <template #default="scope">
            <el-button type="primary" size="small" @click="goEdit(scope.row)">{{ scope.row.builtin ? '查看' : '编辑' }}</el-button>
            <el-button type="info" plain size="small" @click="copyRule(scope.row)">复制</el-button>
            <el-button v-if="!scope.row.builtin" type="danger" plain size="small" @click="deleteRule(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="total > size" class="d-flex justify-content-end mt-3">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import DataQualityRuleService, { type IDataQualityRule, type IQualityRuleType } from './data-quality-rule.service';

defineOptions({ name: 'DataQualityRule' });

const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

const router = useRouter();
const service = new DataQualityRuleService();

const rules = ref<IDataQualityRule[]>([]);
const ruleTypes = ref<IQualityRuleType[]>([]);
const search = ref('');
const level = ref('');
const type = ref('');
const page = ref(1);
const size = ref(20);
const total = ref(0);
const isFetching = ref(false);

const typeLabel = (code?: string) => ruleTypes.value.find(item => item.code === code)?.label || code || '-';
const isCustomSql = (code?: string) => code === 'CUSTOM_SQL';

const formatDate = (value?: string) => {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(
    date.getSeconds(),
  )}`;
};

const retrieve = async () => {
  isFetching.value = true;
  try {
    const res = await service.retrieve({
      page: page.value - 1,
      size: size.value,
      sort: ['builtin,desc', 'updateTime,desc'],
      search: search.value || undefined,
      level: level.value || undefined,
      type: type.value || undefined,
    });
    rules.value = res.data || [];
    total.value = Number(res.headers['x-total-count'] || 0);
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.detail || e?.message || '加载质量规则失败');
  } finally {
    isFetching.value = false;
  }
};

const loadTypes = async () => {
  try {
    ruleTypes.value = await service.getTypes();
  } catch {
    ruleTypes.value = [];
  }
};

const onPageChange = (value: number) => {
  page.value = value;
  retrieve();
};

const onSizeChange = (value: number) => {
  size.value = value;
  page.value = 1;
  retrieve();
};

const goEdit = (rule: IDataQualityRule) => {
  router.push({ name: 'DataQualityRuleEdit', params: { id: rule.id } });
};

const copyRule = (rule: IDataQualityRule) => {
  router.push({ name: 'DataQualityRuleCreate', query: { copyFrom: rule.id } });
};

const deleteRule = async (rule: IDataQualityRule) => {
  if (!rule.id) return;
  try {
    await ElMessageBox.confirm(`确定删除质量规则「${rule.ruleName}」吗？`, '删除规则', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await service.delete(rule.id);
    ElMessage.success('删除成功');
    retrieve();
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.detail || e?.message || '删除失败');
  }
};

onMounted(async () => {
  await loadTypes();
  await retrieve();
});
</script>

<style scoped>
.page-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.page-toolbar-embedded {
  justify-content: flex-end;
  margin-bottom: 12px;
}

.rule-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
