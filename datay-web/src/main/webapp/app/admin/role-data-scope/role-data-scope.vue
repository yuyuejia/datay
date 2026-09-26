<template>
  <div class="role-data-scope-container">
    <h2 id="page-heading">
      <span>数据权限（角色维度成员范围）</span>
      <div class="d-flex align-items-center">
        <el-button type="primary" :disabled="!selectedRole" @click="openCreate">
          <font-awesome-icon icon="plus" /> 新增范围
        </el-button>
      </div>
    </h2>

    <div class="toolbar">
      <span class="toolbar-label">角色</span>
      <el-select v-model="selectedRole" placeholder="请选择角色" class="role-select" @change="loadScopes">
        <el-option v-for="authority in authorities" :key="authority.name" :value="authority.name" :label="authority.name" />
      </el-select>
      <el-button type="info" @click="loadScopes">刷新</el-button>
    </div>

    <el-alert
      v-if="selectedRole === adminRole"
      class="mb-3"
      type="warning"
      :closable="false"
      show-icon
      title="ROLE_ADMIN 默认不受数据范围限制，配置的规则不会对管理员生效。"
    />

    <el-table :data="scopes" v-loading="loading" style="width: 100%">
      <el-table-column label="维度" min-width="180">
        <template #default="{ row }">{{ dimensionName(row.dimensionModelId) }}</template>
      </el-table-column>
      <el-table-column label="成员范围" min-width="260">
        <template #default="{ row }">{{ summarize(row) }}</template>
      </el-table-column>
      <el-table-column label="启用" width="100" align="center">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled" @change="toggleEnabled(row)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button type="danger" size="small" @click="removeScope(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <div class="text-center text-muted py-4">{{ selectedRole ? '暂无数据范围，该角色不受限制' : '请先选择角色' }}</div>
      </template>
    </el-table>

    <app-modal v-model="dialogVisible" :title="form.id ? '编辑数据范围' : '新增数据范围'" size="lg">
      <el-form label-position="top">
        <el-form-item label="角色">
          <el-input :model-value="form.roleName || ''" disabled />
        </el-form-item>

        <el-form-item label="维度">
          <el-select v-model="form.dimensionModelId" placeholder="请选择维度模型" style="width: 100%" @change="onDimensionChange">
            <el-option v-for="dimension in dimensions" :key="dimension.id" :value="dimension.id" :label="dimension.name || dimension.code" />
          </el-select>
        </el-form-item>

        <el-form-item label="成员条件">
          <div v-for="(condition, index) in conditions" :key="index" class="condition-row">
            <el-select v-model="condition.dimensionFieldName" placeholder="字段" class="condition-field">
              <el-option v-for="field in fields" :key="field.fieldName" :value="field.fieldName" :label="field.fieldName" />
            </el-select>
            <el-select v-model="condition.operator" class="condition-operator">
              <el-option v-for="operator in operators" :key="operator.value" :value="operator.value" :label="operator.label" />
            </el-select>
            <div class="value-cell">
              <DimensionValueSelect
                :dimension-model-id="form.dimensionModelId"
                :field-name="condition.dimensionFieldName"
                :model-value="conditionValue(condition)"
                :multiple="isMulti(condition.operator)"
                :disabled="isUnary(condition.operator)"
                :placeholder="valuePlaceholder(condition.operator)"
                @update:model-value="(val) => setConditionValue(condition, val)"
              />
            </div>
            <div v-if="condition.operator === 'BETWEEN'" class="value-cell">
              <DimensionValueSelect
                :dimension-model-id="form.dimensionModelId"
                :field-name="condition.dimensionFieldName"
                :model-value="condition.valueEnd ?? ''"
                placeholder="结束值"
                @update:model-value="(val) => setConditionValueEnd(condition, val)"
              />
            </div>
            <el-button type="danger" plain size="small" :disabled="conditions.length <= 1" @click="removeCondition(index)">删除</el-button>
          </div>
          <el-button type="primary" plain size="small" @click="addCondition">+ 添加 OR 条件</el-button>
          <div class="condition-hint">同一维度内多个条件为 OR 关系；不同维度之间为 AND 关系。</div>
        </el-form-item>
      </el-form>
      <template #modal-footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="saving" @click="submit">保存</el-button>
      </template>
    </app-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";

import RoleDataScopeService from "./role-data-scope.service";
import DimensionValueSelect from "@/components/DimensionValueSelect.vue";
import { useAlertService } from "@/shared/alert/alert.service";
import { type IRoleDataScope, type IRoleScopeCondition, type IRoleScopeFilterConfig } from "@/shared/model/role-data-scope.model";

const adminRole = "ROLE_ADMIN";

const operators = [
  { value: "EQ", label: "等于", unary: false },
  { value: "NE", label: "不等于", unary: false },
  { value: "IN", label: "属于(多值)", unary: false },
  { value: "NOT_IN", label: "不属于(多值)", unary: false },
  { value: "LIKE", label: "模糊匹配", unary: false },
  { value: "GT", label: "大于", unary: false },
  { value: "GE", label: "大于等于", unary: false },
  { value: "LT", label: "小于", unary: false },
  { value: "LE", label: "小于等于", unary: false },
  { value: "BETWEEN", label: "区间", unary: false },
  { value: "IS_NULL", label: "为空", unary: true },
  { value: "IS_NOT_NULL", label: "不为空", unary: true },
];

const service = new RoleDataScopeService();
const alertService = useAlertService();

const authorities = ref<any[]>([]);
const dimensions = ref<any[]>([]);
const fields = ref<any[]>([]);
const scopes = ref<IRoleDataScope[]>([]);
const selectedRole = ref<string>("");
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const form = ref<IRoleDataScope>({});
const conditions = ref<IRoleScopeCondition[]>([]);
const errorMessage = (error: any, fallback: string) => error?.response?.data?.message || error?.response?.data?.detail || fallback;

const dimensionName = (id?: number) => {
  const dimension = dimensions.value.find((item) => item.id === id);
  return dimension ? dimension.name || dimension.code : id ? `#${id}` : "";
};

const isUnary = (operator?: string) => operators.find((item) => item.value === operator)?.unary === true;

const valuePlaceholder = (operator?: string) => {
  if (isUnary(operator)) {
    return "";
  }
  if (operator === "IN" || operator === "NOT_IN") {
    return "请选择（可多选）";
  }
  return "请选择或输入";
};

const summarize = (row: IRoleDataScope) => {
  const config = parseConfig(row.filterConfig);
  if (!config.conditions || config.conditions.length === 0) {
    return "-";
  }
  return config.conditions
    .map((condition) => {
      const label = operators.find((item) => item.value === condition.operator)?.label ?? condition.operator;
      if (isUnary(condition.operator)) {
        return `${condition.dimensionFieldName} ${label}`;
      }
      if (condition.operator === "BETWEEN") {
        return `${condition.dimensionFieldName} ${label} [${condition.value}, ${condition.valueEnd}]`;
      }
      return `${condition.dimensionFieldName} ${label} ${condition.value}`;
    })
    .join(" OR ");
};

const parseConfig = (filterConfig?: string): IRoleScopeFilterConfig => {
  if (!filterConfig) {
    return { conditions: [] };
  }
  try {
    return JSON.parse(filterConfig) as IRoleScopeFilterConfig;
  } catch {
    return { conditions: [] };
  }
};

const loadAuthorities = async () => {
  const response = await service.retrieveAuthorities();
  authorities.value = response.data ?? [];
};

const loadDimensions = async () => {
  const response = await service.retrieveDimensions();
  dimensions.value = response.data ?? [];
};

const loadScopes = async () => {
  if (!selectedRole.value) {
    scopes.value = [];
    return;
  }
  loading.value = true;
  try {
    const response = await service.retrieve(selectedRole.value);
    scopes.value = response.data ?? [];
  } catch (error: any) {
    alertService.showError(errorMessage(error, "加载数据范围失败"));
  } finally {
    loading.value = false;
  }
};

const loadFields = async (dimensionModelId?: number) => {
  if (!dimensionModelId) {
    fields.value = [];
    return;
  }
  try {
    const response = await service.retrieveDimensionFields(dimensionModelId);
    fields.value = response.data ?? [];
  } catch (error: any) {
    fields.value = [];
    alertService.showError(errorMessage(error, "加载维度字段失败"));
  }
};

const isMulti = (operator?: string) => operator === "IN" || operator === "NOT_IN";

const parseList = (value?: string) =>
  value
    ? value
        .split(",")
        .map((item) => item.trim())
        .filter((item) => item.length > 0)
    : [];

const conditionValue = (condition: IRoleScopeCondition) =>
  isMulti(condition.operator) ? parseList(condition.value) : (condition.value ?? "");

const setConditionValue = (condition: IRoleScopeCondition, value: string | string[]) => {
  if (isMulti(condition.operator)) {
    condition.value = Array.isArray(value) ? value.join(",") : String(value ?? "");
  } else {
    condition.value = Array.isArray(value) ? (value[0] ?? "") : String(value ?? "");
  }
};

const setConditionValueEnd = (condition: IRoleScopeCondition, value: string | string[]) => {
  condition.valueEnd = Array.isArray(value) ? (value[0] ?? "") : String(value ?? "");
};

const newCondition = (): IRoleScopeCondition => ({
  dimensionFieldName: "",
  operator: "EQ",
  value: "",
});

const openCreate = () => {
  form.value = {
    roleName: selectedRole.value,
    enabled: true,
    dimensionModelId: dimensions.value[0]?.id ?? "",
  };
  conditions.value = [newCondition()];
  fields.value = [];
  dialogVisible.value = true;
  loadFields(form.value.dimensionModelId as number);
};

const openEdit = (row: IRoleDataScope) => {
  form.value = { ...row };
  const config = parseConfig(row.filterConfig);
  conditions.value =
    config.conditions && config.conditions.length > 0 ? config.conditions.map((condition) => ({ ...condition })) : [newCondition()];
  fields.value = [];
  dialogVisible.value = true;
  loadFields(row.dimensionModelId);
};

const onDimensionChange = () => {
  conditions.value = [newCondition()];
  loadFields(form.value.dimensionModelId as number);
};

const addCondition = () => {
  conditions.value.push(newCondition());
};

const removeCondition = (index: number) => {
  conditions.value.splice(index, 1);
};

const submit = async () => {
  if (!form.value.dimensionModelId) {
    alertService.showWarning("请选择维度模型");
    return;
  }
  const validConditions = conditions.value.filter((condition) => condition.dimensionFieldName && condition.operator);
  if (validConditions.length === 0) {
    alertService.showWarning("请至少配置一条有效的成员条件");
    return;
  }
  saving.value = true;
  try {
    const payload: IRoleDataScope = {
      ...form.value,
      dimensionModelId: Number(form.value.dimensionModelId),
      filterConfig: JSON.stringify({
        conditions: validConditions.map((condition) => ({
          type: "DIMENSION",
          dimensionFieldName: condition.dimensionFieldName,
          operator: condition.operator,
          value: condition.value,
          valueEnd: condition.valueEnd,
        })),
      }),
    };
    if (payload.id) {
      await service.update(payload);
    } else {
      await service.create(payload);
    }
    alertService.showSuccess("保存成功");
    dialogVisible.value = false;
    await loadScopes();
  } catch (error: any) {
    alertService.showError(errorMessage(error, "保存失败"));
  } finally {
    saving.value = false;
  }
};

const toggleEnabled = async (row: IRoleDataScope) => {
  try {
    await service.update({ ...row, enabled: !row.enabled });
    await loadScopes();
  } catch (error: any) {
    alertService.showError(errorMessage(error, "操作失败"));
  }
};

const removeScope = async (row: IRoleDataScope) => {
  if (!window.confirm("确认删除该数据范围规则？")) {
    return;
  }
  try {
    await service.remove(row.id!);
    alertService.showSuccess("删除成功");
    await loadScopes();
  } catch (error: any) {
    alertService.showError(errorMessage(error, "删除失败"));
  }
};

onMounted(async () => {
  try {
    await Promise.all([loadAuthorities(), loadDimensions()]);
  } catch (error: any) {
    alertService.showError(errorMessage(error, "初始化数据加载失败"));
  }
});
</script>

<style scoped>
.role-data-scope-container {
  padding: 8px 0;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.toolbar-label {
  margin: 0;
  white-space: nowrap;
  color: #606266;
}

.role-select {
  width: 280px;
}

.condition-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  width: 100%;
}

.condition-field {
  width: 160px;
}

.condition-operator {
  width: 130px;
}

.value-cell {
  flex: 1;
  min-width: 160px;
}

.condition-hint {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}
</style>
