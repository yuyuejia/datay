<template>
  <div>
    <h2 id="page-heading">
      <span id="tenant-management-page-heading">租户管理</span>

      <div class="d-flex align-items-center">
        <el-button type="info" class="mr-2" @click="handleSyncList" :disabled="isLoading">
          <font-awesome-icon icon="sync" :spin="isLoading"></font-awesome-icon> <span>刷新</span>
        </el-button>
        <router-link custom v-slot="{ navigate }" :to="{ name: 'JhiTenantCreate' }">
          <el-button type="primary" @click="navigate" class="jh-create-entity">
            <font-awesome-icon icon="plus"></font-awesome-icon> <span>创建租户</span>
          </el-button>
        </router-link>
      </div>
    </h2>
    <div v-if="tenants">
      <el-table :data="tenants" style="width: 100%">
        <el-table-column prop="id" label="ID" sortable width="90">
          <template #default="{ row }">
            <router-link :to="{ name: 'JhiTenantView', params: { tenantId: row.id } }">{{ row.id }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="租户编码" sortable min-width="140" />
        <el-table-column prop="name" label="租户名称" sortable min-width="150" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdDate" label="创建时间" sortable width="170">
          <template #default="{ row }">{{ formatDate(row.createdDate) }}</template>
        </el-table-column>
        <el-table-column prop="lastModifiedBy" label="最近修改人" sortable min-width="140" />
        <el-table-column prop="lastModifiedDate" label="最近修改时间" sortable width="170">
          <template #default="{ row }">{{ formatDate(row.lastModifiedDate) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="220">
          <template #default="{ row }">
            <div class="btn-group">
              <router-link :to="{ name: 'JhiTenantView', params: { tenantId: row.id } }" custom v-slot="{ navigate }">
                <el-button type="info" size="small" @click="navigate">
                  <font-awesome-icon icon="eye"></font-awesome-icon>
                  <span class="d-none d-md-inline">查看</span>
                </el-button>
              </router-link>
              <router-link :to="{ name: 'JhiTenantEdit', params: { tenantId: row.id } }" custom v-slot="{ navigate }">
                <el-button type="primary" size="small" @click="navigate">
                  <font-awesome-icon icon="pencil-alt"></font-awesome-icon>
                  <span class="d-none d-md-inline">编辑</span>
                </el-button>
              </router-link>
              <el-button size="small" @click="prepareRemove(row)" type="danger" :disabled="row.code === 'system'">
                <font-awesome-icon icon="times"></font-awesome-icon>
                <span class="d-none d-md-inline">删除</span>
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <app-modal ref="removeTenant" id="removeTenant" title="确认删除" @ok="deleteTenant()">
        <div class="modal-body">
          <p id="jhi-delete-tenant-heading">你确定要删除租户 {{ removeId }} ?</p>
        </div>
        <template #modal-footer>
          <div>
            <el-button @click="closeDialog()">取消</el-button>
            <el-button type="primary" id="confirm-delete-tenant" @click="deleteTenant()">删除</el-button>
          </div>
        </template>
      </app-modal>
    </div>
  </div>
</template>

<script lang="ts" src="./tenant-management.component.ts"></script>
