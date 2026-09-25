<template>
  <div class="role-data-scope-container">
    <div class="page-header">
      <h4 class="page-title">数据权限（角色维度成员范围）</h4>
      <div class="page-header-actions">
        <button
          type="button"
          class="btn btn-primary"
          :disabled="!selectedRole"
          @click="openCreate"
        >
          <font-awesome-icon icon="plus" /> 新增范围
        </button>
      </div>
    </div>

    <div class="toolbar">
      <label class="toolbar-label">角色</label>
      <select
        v-model="selectedRole"
        class="form-control role-select"
        @change="loadScopes"
      >
        <option value="" disabled>请选择角色</option>
        <option
          v-for="authority in authorities"
          :key="authority.name"
          :value="authority.name"
        >
          {{ authority.name }}
        </option>
      </select>
      <button type="button" class="btn btn-secondary" @click="loadScopes">
        刷新
      </button>
    </div>

    <div v-if="selectedRole === adminRole" class="alert alert-warning">
      ROLE_ADMIN 默认不受数据范围限制，配置的规则不会对管理员生效。
    </div>

    <table class="table table-striped">
      <thead>
        <tr>
          <th scope="col">维度</th>
          <th scope="col">成员范围</th>
          <th scope="col" class="text-center">启用</th>
          <th scope="col">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in scopes" :key="row.id">
          <td>{{ dimensionName(row.dimensionModelId) }}</td>
          <td>{{ summarize(row) }}</td>
          <td class="text-center">
            <input
              type="checkbox"
              :checked="row.enabled"
              @change="toggleEnabled(row)"
            />
          </td>
          <td>
            <button
              type="button"
              class="btn btn-sm btn-primary mr-2"
              @click="openEdit(row)"
            >
              编辑
            </button>
            <button
              type="button"
              class="btn btn-sm btn-danger"
              @click="removeScope(row)"
            >
              删除
            </button>
          </td>
        </tr>
        <tr v-if="scopes.length === 0">
          <td colspan="4" class="text-center text-muted">
            {{ selectedRole ? "暂无数据范围，该角色不受限制" : "请先选择角色" }}
          </td>
        </tr>
      </tbody>
    </table>

    <div v-if="dialogVisible" class="dialog-overlay">
      <div class="dialog-box">
        <div class="dialog-header">
          {{ form.id ? "编辑数据范围" : "新增数据范围" }}
        </div>
        <div class="dialog-body">
          <div class="form-group">
            <label>角色</label>
            <div class="form-value">{{ form.roleName }}</div>
          </div>

          <div class="form-group">
            <label>维度</label>
            <select
              v-model="form.dimensionModelId"
              class="form-control"
              @change="onDimensionChange"
            >
              <option value="" disabled>请选择维度模型</option>
              <option
                v-for="dimension in dimensions"
                :key="dimension.id"
                :value="dimension.id"
              >
                {{ dimension.name || dimension.code }}
              </option>
            </select>
          </div>

          <div class="form-group">
            <label>成员条件</label>
            <div
              v-for="(condition, index) in conditions"
              :key="index"
              class="condition-row"
            >
              <select
                v-model="condition.dimensionFieldName"
                class="form-control condition-field"
              >
                <option value="" disabled>字段</option>
                <option
                  v-for="field in fields"
                  :key="field.fieldName"
                  :value="field.fieldName"
                >
                  {{ field.fieldName }}
                </option>
              </select>
              <select
                v-model="condition.operator"
                class="form-control condition-operator"
              >
                <option
                  v-for="operator in operators"
                  :key="operator.value"
                  :value="operator.value"
                >
                  {{ operator.label }}
                </option>
              </select>
              <div class="value-cell">
                <DimensionValueSelect
                  :dimension-model-id="form.dimensionModelId"
                  :field-name="condition.dimensionFieldName"
                  :model-value="conditionValue(condition)"
                  :multiple="isMulti(condition.operator)"
                  :disabled="isUnary(condition.operator)"
                  :placeholder="valuePlaceholder(condition.operator)"
                  @update:model-value="
                    (val) => setConditionValue(condition, val)
                  "
                />
              </div>
              <div v-if="condition.operator === 'BETWEEN'" class="value-cell">
                <DimensionValueSelect
                  :dimension-model-id="form.dimensionModelId"
                  :field-name="condition.dimensionFieldName"
                  :model-value="condition.valueEnd ?? ''"
                  placeholder="结束值"
                  @update:model-value="
                    (val) => setConditionValueEnd(condition, val)
                  "
                />
              </div>
              <button
                type="button"
                class="btn btn-sm btn-outline-danger"
                :disabled="conditions.length <= 1"
                @click="removeCondition(index)"
              >
                删除
              </button>
            </div>
            <button
              type="button"
              class="btn btn-sm btn-outline-primary"
              @click="addCondition"
            >
              + 添加 OR 条件
            </button>
            <div class="condition-hint">
              同一维度内多个条件为 OR 关系；不同维度之间为 AND 关系。
            </div>
          </div>
        </div>
        <div class="dialog-footer">
          <button
            type="button"
            class="btn btn-secondary mr-2"
            @click="dialogVisible = false"
          >
            取消
          </button>
          <button
            type="button"
            class="btn btn-primary"
            :disabled="saving"
            @click="submit"
          >
            保存
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";

import RoleDataScopeService from "./role-data-scope.service";
import DimensionValueSelect from "@/components/DimensionValueSelect.vue";
import { useAlertService } from "@/shared/alert/alert.service";
import {
  type IRoleDataScope,
  type IRoleScopeCondition,
  type IRoleScopeFilterConfig,
} from "@/shared/model/role-data-scope.model";

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
const errorMessage = (error: any, fallback: string) =>
  error?.response?.data?.message || error?.response?.data?.detail || fallback;

const dimensionName = (id?: number) => {
  const dimension = dimensions.value.find((item) => item.id === id);
  return dimension ? dimension.name || dimension.code : id ? `#${id}` : "";
};

const isUnary = (operator?: string) =>
  operators.find((item) => item.value === operator)?.unary === true;

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
      const label =
        operators.find((item) => item.value === condition.operator)?.label ??
        condition.operator;
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

const isMulti = (operator?: string) =>
  operator === "IN" || operator === "NOT_IN";

const parseList = (value?: string) =>
  value
    ? value
        .split(",")
        .map((item) => item.trim())
        .filter((item) => item.length > 0)
    : [];

const conditionValue = (condition: IRoleScopeCondition) =>
  isMulti(condition.operator)
    ? parseList(condition.value)
    : (condition.value ?? "");

const setConditionValue = (
  condition: IRoleScopeCondition,
  value: string | string[],
) => {
  if (isMulti(condition.operator)) {
    condition.value = Array.isArray(value)
      ? value.join(",")
      : String(value ?? "");
  } else {
    condition.value = Array.isArray(value)
      ? (value[0] ?? "")
      : String(value ?? "");
  }
};

const setConditionValueEnd = (
  condition: IRoleScopeCondition,
  value: string | string[],
) => {
  condition.valueEnd = Array.isArray(value)
    ? (value[0] ?? "")
    : String(value ?? "");
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
    config.conditions && config.conditions.length > 0
      ? config.conditions.map((condition) => ({ ...condition }))
      : [newCondition()];
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
  const validConditions = conditions.value.filter(
    (condition) => condition.dimensionFieldName && condition.operator,
  );
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
  padding: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.page-title {
  margin: 0;
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
}

.role-select {
  width: 280px;
}

.dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 60px 16px;
  background: rgba(0, 0, 0, 0.5);
}

.dialog-box {
  width: 100%;
  max-width: 680px;
  background: #fff;
  border-radius: 4px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
}

.dialog-header {
  padding: 12px 16px;
  border-bottom: 1px solid #e9ecef;
  font-weight: 600;
}

.dialog-body {
  max-height: 60vh;
  overflow-y: auto;
  padding: 16px;
}

.dialog-footer {
  padding: 12px 16px;
  border-top: 1px solid #e9ecef;
  text-align: right;
}

.form-group {
  margin-bottom: 12px;
}

.form-group > label {
  display: block;
  margin-bottom: 4px;
  font-weight: 500;
}

.form-value {
  padding-top: 6px;
}

.condition-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.condition-field {
  width: 150px;
}

.condition-operator {
  width: 120px;
}

.value-cell {
  width: 160px;
  flex: none;
}

.condition-hint {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}
</style>
