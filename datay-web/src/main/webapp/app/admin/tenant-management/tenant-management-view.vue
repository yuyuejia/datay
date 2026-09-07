<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <h2>
        <span>租户详情</span>
        <div class="float-right">
          <router-link :to="{ name: 'JhiTenantEdit', params: { tenantId: tenant?.id } }" custom v-slot="{ navigate }">
            <button v-if="tenant?.code !== 'system'" @click="navigate" class="btn btn-primary btn-sm mr-2">
              <font-awesome-icon icon="pencil-alt"></font-awesome-icon> 编辑
            </button>
          </router-link>
          <button @click="previousState" class="btn btn-secondary btn-sm">
            <font-awesome-icon icon="arrow-left"></font-awesome-icon> 返回
          </button>
        </div>
      </h2>
      <div v-if="tenant">
        <div class="card mb-4">
          <div class="card-header"><strong>基本信息</strong></div>
          <div class="card-body">
            <dl class="row">
              <dt class="col-sm-3">ID</dt>
              <dd class="col-sm-9">{{ tenant.id }}</dd>

              <dt class="col-sm-3">租户编码</dt>
              <dd class="col-sm-9">{{ tenant.code }}</dd>

              <dt class="col-sm-3">租户名称</dt>
              <dd class="col-sm-9">{{ tenant.name }}</dd>

              <dt class="col-sm-3">描述</dt>
              <dd class="col-sm-9">{{ tenant.description || '-' }}</dd>

              <dt class="col-sm-3">状态</dt>
              <dd class="col-sm-9">
                <span class="badge" :class="tenant.enabled ? 'badge-success' : 'badge-secondary'">
                  {{ tenant.enabled ? '启用' : '禁用' }}
                </span>
              </dd>

              <dt class="col-sm-3">创建时间</dt>
              <dd class="col-sm-9">{{ formatDate(tenant.createdDate) }}</dd>

              <dt class="col-sm-3">最近修改人</dt>
              <dd class="col-sm-9">{{ tenant.lastModifiedBy }}</dd>

              <dt class="col-sm-3">最近修改时间</dt>
              <dd class="col-sm-9">{{ formatDate(tenant.lastModifiedDate) }}</dd>
            </dl>
          </div>
        </div>

        <div class="card">
          <div class="card-header d-flex justify-content-between align-items-center">
            <strong>租户用户 ({{ users.length }})</strong>
            <button class="btn btn-success btn-sm" @click="showAddUserModal">
              <font-awesome-icon icon="plus"></font-awesome-icon> 添加用户
            </button>
          </div>
          <div class="card-body">
            <table class="table table-striped" v-if="users.length > 0">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>登录名</th>
                  <th>邮箱</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="user in users" :key="user.id">
                  <td>{{ user.id }}</td>
                  <td>{{ user.login }}</td>
                  <td>{{ user.email }}</td>
                  <td>
                    <button class="btn btn-danger btn-sm" @click="removeUser(user)">
                      <font-awesome-icon icon="times"></font-awesome-icon> 移除
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-else class="text-center text-muted py-4">
              暂无用户
            </div>
          </div>
        </div>
      </div>
    </div>

    <b-modal v-model="addUserModal" title="添加用户到租户">
      <div class="form-group">
        <input
          type="text"
          class="form-control"
          v-model="searchQuery"
          placeholder="输入登录名或邮箱搜索..."
          @input="debouncedSearch"
        />
      </div>
      <div v-if="searchResults.length > 0" style="max-height: 300px; overflow-y: auto;">
        <div
          v-for="user in searchResults"
          :key="user.id"
          class="d-flex justify-content-between align-items-center p-2 border-bottom"
        >
          <div>
            <strong>{{ user.login }}</strong>
            <small class="text-muted ml-2">{{ user.email }}</small>
          </div>
          <button class="btn btn-sm btn-success" @click="addUser(user)" :disabled="isUserInTenant(user.id)">
            <font-awesome-icon icon="plus"></font-awesome-icon> {{ isUserInTenant(user.id) ? '已在租户中' : '添加' }}
          </button>
        </div>
      </div>
      <div v-else-if="searched" class="text-center text-muted py-3">
        未找到用户
      </div>
      <template #modal-footer>
        <button type="button" class="btn btn-secondary" @click="addUserModal = false">关闭</button>
      </template>
    </b-modal>
  </div>
</template>

<script lang="ts" src="./tenant-management-view.component.ts"></script>