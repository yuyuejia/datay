<template>
  <div class="role-management-container">
    <h2 id="page-heading">
      <span>角色管理</span>
      <div class="d-flex align-items-center">
        <el-button type="info" class="mr-2" @click="load">刷新</el-button>
        <el-button type="primary" @click="openCreate">
          <font-awesome-icon icon="plus" /> 新增角色
        </el-button>
      </div>
    </h2>

    <el-table :data="roles" style="width: 100%">
      <el-table-column label="角色名" min-width="200">
        <template #default="{ row }">
          <code>{{ row.name }}</code>
        </template>
      </el-table-column>
      <el-table-column label="描述" min-width="220">
        <template #default="{ row }">{{ row.description || '-' }}</template>
      </el-table-column>
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag :type="isBuiltIn(row.name) ? 'info' : 'primary'" size="small">{{ isBuiltIn(row.name) ? '内置' : '自定义' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button
            type="danger"
            size="small"
            :disabled="isBuiltIn(row.name)"
            :title="isBuiltIn(row.name) ? '内置角色不可删除' : ''"
            @click="removeRole(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <div class="text-center text-muted py-4">暂无角色</div>
      </template>
    </el-table>

    <app-modal v-model="dialogVisible" :title="editing ? '编辑角色' : '新增角色'" size="md">
      <el-form label-position="top">
        <el-form-item label="角色名">
          <el-input v-model="form.name" :disabled="editing" placeholder="如：REGION_EAST" />
          <div v-if="!editing" class="field-hint">将自动补全为 <code>ROLE_</code> 前缀并转为大写</div>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="角色用途说明" />
        </el-form-item>
      </el-form>
      <template #modal-footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="saving" @click="submit">{{ saving ? '保存中...' : '保存' }}</el-button>
      </template>
    </app-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";

import RoleManagementService from "./role-management.service";
import { useAlertService } from "@/shared/alert/alert.service";
import { type IAuthority } from "@/shared/model/authority.model";

const BUILT_IN_ROLES = ["ROLE_ADMIN", "ROLE_TENANT_ADMIN", "ROLE_USER"];

const service = new RoleManagementService();
const alertService = useAlertService();

const roles = ref<IAuthority[]>([]);
const dialogVisible = ref(false);
const editing = ref(false);
const saving = ref(false);
const form = ref<IAuthority>({});

const isBuiltIn = (name?: string) => name !== undefined && BUILT_IN_ROLES.includes(name);

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
  padding: 8px 0;
}

.field-hint {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}
</style>
