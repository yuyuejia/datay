<template>
  <div class="role-management-container">
    <div class="page-header">
      <h4 class="page-title">角色管理</h4>
      <div class="page-header-actions">
        <button type="button" class="btn btn-secondary mr-2" @click="load">
          刷新
        </button>
        <button type="button" class="btn btn-primary" @click="openCreate">
          <font-awesome-icon icon="plus" /> 新增角色
        </button>
      </div>
    </div>

    <table class="table table-striped">
      <thead>
        <tr>
          <th scope="col">角色名</th>
          <th scope="col">描述</th>
          <th scope="col">类型</th>
          <th scope="col" class="text-right">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="role in roles" :key="role.name">
          <td>
            <code>{{ role.name }}</code>
          </td>
          <td>{{ role.description || "-" }}</td>
          <td>
            <span
              class="badge"
              :class="isBuiltIn(role.name) ? 'badge-secondary' : 'badge-info'"
            >
              {{ isBuiltIn(role.name) ? "内置" : "自定义" }}
            </span>
          </td>
          <td class="text-right">
            <button
              type="button"
              class="btn btn-sm btn-primary mr-2"
              @click="openEdit(role)"
            >
              编辑
            </button>
            <button
              type="button"
              class="btn btn-sm btn-danger"
              :disabled="isBuiltIn(role.name)"
              :title="isBuiltIn(role.name) ? '内置角色不可删除' : ''"
              @click="removeRole(role)"
            >
              删除
            </button>
          </td>
        </tr>
        <tr v-if="roles.length === 0">
          <td colspan="4" class="text-center text-muted">暂无角色</td>
        </tr>
      </tbody>
    </table>

    <div v-if="dialogVisible" class="dialog-overlay">
      <div class="dialog-box">
        <div class="dialog-header">
          {{ editing ? "编辑角色" : "新增角色" }}
        </div>
        <div class="dialog-body">
          <div class="form-group">
            <label>角色名</label>
            <input
              v-model="form.name"
              type="text"
              class="form-control"
              :disabled="editing"
              placeholder="如：REGION_EAST"
            />
            <div v-if="!editing" class="field-hint">
              将自动补全为 <code>ROLE_</code> 前缀并转为大写
            </div>
          </div>
          <div class="form-group">
            <label>描述</label>
            <input
              v-model="form.description"
              type="text"
              class="form-control"
              placeholder="角色用途说明"
            />
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
            {{ saving ? "保存中..." : "保存" }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";

import RoleManagementService from "./role-management.service";
import { useAlertService } from "@/shared/alert/alert.service";
import { type IAuthority } from "@/shared/model/authority.model";

const BUILT_IN_ROLES = ["ROLE_ADMIN", "ROLE_USER"];

const service = new RoleManagementService();
const alertService = useAlertService();

const roles = ref<IAuthority[]>([]);
const dialogVisible = ref(false);
const editing = ref(false);
const saving = ref(false);
const form = ref<IAuthority>({});

const isBuiltIn = (name?: string) =>
  name !== undefined && BUILT_IN_ROLES.includes(name);

const errorMessage = (error: any, fallback: string) => {
  const data = error?.response?.data;
  const errorKey = data?.properties?.message;
  if (errorKey === "error.idexists") {
    return "角色已存在，请更换角色名";
  }
  if (errorKey === "error.builtin") {
    return "内置角色不可删除";
  }
  if (errorKey === "error.inuse") {
    return "该角色已分配给用户，请先解除分配";
  }
  if (errorKey === "error.hasScope") {
    return "该角色存在数据权限规则，请先删除";
  }
  if (errorKey === "error.namenull") {
    return "角色名不能为空";
  }
  if (errorKey === "error.nametoolong") {
    return "角色名过长";
  }
  return data?.message || data?.title || fallback;
};

const load = async () => {
  try {
    const response = await service.retrieve();
    roles.value = response.data ?? [];
  } catch (error: any) {
    alertService.showError(errorMessage(error, "加载角色失败"));
  }
};

const openCreate = () => {
  editing.value = false;
  form.value = { name: "", description: "" };
  dialogVisible.value = true;
};

const openEdit = (role: IAuthority) => {
  editing.value = true;
  form.value = { name: role.name, description: role.description };
  dialogVisible.value = true;
};

const submit = async () => {
  if (saving.value) {
    return;
  }
  if (!editing.value && (!form.value.name || !form.value.name.trim())) {
    alertService.showWarning("请输入角色名");
    return;
  }
  saving.value = true;
  try {
    if (editing.value) {
      await service.update(form.value.name!, {
        description: form.value.description,
      });
    } else {
      await service.create({
        name: form.value.name,
        description: form.value.description,
      });
    }
    alertService.showSuccess("保存成功");
    dialogVisible.value = false;
    await load();
  } catch (error: any) {
    alertService.showError(errorMessage(error, "保存失败"));
  } finally {
    saving.value = false;
  }
};

const removeRole = async (role: IAuthority) => {
  if (isBuiltIn(role.name)) {
    alertService.showWarning("内置角色不可删除");
    return;
  }
  if (!window.confirm(`确认删除角色 ${role.name}？`)) {
    return;
  }
  try {
    await service.remove(role.name!);
    alertService.showSuccess("删除成功");
    await load();
  } catch (error: any) {
    alertService.showError(errorMessage(error, "删除失败"));
  }
};

onMounted(load);
</script>

<style scoped>
.role-management-container {
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

.field-hint {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
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
  max-width: 520px;
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
</style>
