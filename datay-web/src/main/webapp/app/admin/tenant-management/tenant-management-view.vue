<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <h2 id="page-heading">
        <span>租户详情</span>
        <div class="d-flex align-items-center">
          <router-link :to="{ name: 'JhiTenantEdit', params: { tenantId: tenant?.id } }" custom v-slot="{ navigate }">
            <el-button type="primary" size="small" class="mr-2" v-if="tenant?.code !== 'system'" @click="navigate">
              <font-awesome-icon icon="pencil-alt"></font-awesome-icon> 编辑
            </el-button>
          </router-link>
          <el-button size="small" @click="previousState">
            <font-awesome-icon icon="arrow-left"></font-awesome-icon> 返回
          </el-button>
        </div>
      </h2>
      <div v-if="tenant">
        <el-card shadow="never" class="mb-4">
          <template #header><strong>基本信息</strong></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="ID">{{ tenant.id }}</el-descriptions-item>
            <el-descriptions-item label="租户编码">{{ tenant.code }}</el-descriptions-item>
            <el-descriptions-item label="租户名称">{{ tenant.name }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="tenant.enabled ? 'success' : 'info'" size="small">{{ tenant.enabled ? '启用' : '禁用' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">{{ tenant.description || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDate(tenant.createdDate) }}</el-descriptions-item>
            <el-descriptions-item label="最近修改人">{{ tenant.lastModifiedBy }}</el-descriptions-item>
            <el-descriptions-item label="最近修改时间" :span="2">{{ formatDate(tenant.lastModifiedDate) }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card shadow="never">
          <template #header>
            <div class="d-flex justify-content-between align-items-center">
              <strong>租户用户 ({{ users.length }})</strong>
              <el-button type="success" size="small" @click="showAddUserModal">
                <font-awesome-icon icon="plus"></font-awesome-icon> 添加用户
              </el-button>
            </div>
          </template>
          <el-table :data="users" style="width: 100%">
            <el-table-column prop="id" label="ID" width="90" />
            <el-table-column prop="login" label="登录名" min-width="150" />
            <el-table-column prop="email" label="邮箱" min-width="200" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button type="danger" size="small" @click="removeUser(row)">
                  <font-awesome-icon icon="times"></font-awesome-icon> 移除
                </el-button>
              </template>
            </el-table-column>
            <template #empty>
              <div class="text-center text-muted py-4">暂无用户</div>
            </template>
          </el-table>
        </el-card>
      </div>
    </div>

    <app-modal v-model="addUserModal" title="添加用户到租户">
      <el-input v-model="searchQuery" placeholder="输入登录名或邮箱搜索..." clearable class="mb-3" @input="debouncedSearch">
        <template #prefix>
          <font-awesome-icon icon="search" />
        </template>
      </el-input>
      <div v-if="searchResults.length > 0" style="max-height: 300px; overflow-y: auto">
        <div
          v-for="user in searchResults"
          :key="user.id"
          class="d-flex justify-content-between align-items-center p-2 border-bottom"
        >
          <div>
            <strong>{{ user.login }}</strong>
            <small class="text-muted ml-2">{{ user.email }}</small>
          </div>
          <el-button type="success" size="small" @click="addUser(user)" :disabled="isUserInTenant(user.id)">
            <font-awesome-icon icon="plus"></font-awesome-icon> {{ isUserInTenant(user.id) ? '已在租户中' : '添加' }}
          </el-button>
        </div>
      </div>
      <div v-else-if="searched" class="text-center text-muted py-3">未找到用户</div>
      <template #modal-footer>
        <el-button @click="addUserModal = false">关闭</el-button>
      </template>
    </app-modal>
  </div>
</template>

<script lang="ts" src="./tenant-management-view.component.ts"></script>
