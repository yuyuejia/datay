<template>
  <div>
    <h2>
      <span id="tenant-management-page-heading">租户管理</span>

      <div class="d-flex justify-content-end">
        <button class="btn btn-info mr-2" @click="handleSyncList" :disabled="isLoading">
          <font-awesome-icon icon="sync" :spin="isLoading"></font-awesome-icon> <span>刷新</span>
        </button>
        <router-link custom v-slot="{ navigate }" :to="{ name: 'JhiTenantCreate' }">
          <button @click="navigate" class="btn btn-primary jh-create-entity">
            <font-awesome-icon icon="plus"></font-awesome-icon> <span>创建租户</span>
          </button>
        </router-link>
      </div>
    </h2>
    <div class="table-responsive" v-if="tenants">
      <table class="table table-striped" aria-describedby="Tenants">
        <thead>
          <tr>
            <th scope="col" @click="changeOrder('id')">
              <span>ID</span> <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'id'"></jhi-sort-indicator>
            </th>
            <th scope="col" @click="changeOrder('code')">
              <span>租户编码</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'code'"></jhi-sort-indicator>
            </th>
            <th scope="col" @click="changeOrder('name')">
              <span>租户名称</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'name'"></jhi-sort-indicator>
            </th>
            <th scope="col"><span>描述</span></th>
            <th scope="col"><span>状态</span></th>
            <th scope="col" @click="changeOrder('createdDate')">
              <span>创建时间</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'createdDate'"></jhi-sort-indicator>
            </th>
            <th scope="col" @click="changeOrder('lastModifiedBy')">
              <span>最近修改人</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'lastModifiedBy'"></jhi-sort-indicator>
            </th>
            <th scope="col" @click="changeOrder('lastModifiedDate')">
              <span>最近修改时间</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'lastModifiedDate'"></jhi-sort-indicator>
            </th>
            <th scope="col"></th>
          </tr>
        </thead>
        <tbody v-if="tenants">
          <tr v-for="tenant in tenants" :key="tenant.id" :id="tenant.code">
            <td>
              <router-link :to="{ name: 'JhiTenantView', params: { tenantId: tenant.id } }">{{ tenant.id }}</router-link>
            </td>
            <td>{{ tenant.code }}</td>
            <td>{{ tenant.name }}</td>
            <td>{{ tenant.description }}</td>
            <td>
              <span class="badge" :class="tenant.enabled ? 'badge-success' : 'badge-secondary'">
                {{ tenant.enabled ? '启用' : '禁用' }}
              </span>
            </td>
            <td>{{ formatDate(tenant.createdDate) }}</td>
            <td>{{ tenant.lastModifiedBy }}</td>
            <td>{{ formatDate(tenant.lastModifiedDate) }}</td>
            <td class="text-right">
              <div class="btn-group">
                <router-link :to="{ name: 'JhiTenantView', params: { tenantId: tenant.id } }" custom v-slot="{ navigate }">
                  <button @click="navigate" class="btn btn-info btn-sm">
                    <font-awesome-icon icon="eye"></font-awesome-icon>
                    <span class="d-none d-md-inline">查看</span>
                  </button>
                </router-link>
                <router-link :to="{ name: 'JhiTenantEdit', params: { tenantId: tenant.id } }" custom v-slot="{ navigate }">
                  <button @click="navigate" class="btn btn-primary btn-sm">
                    <font-awesome-icon icon="pencil-alt"></font-awesome-icon>
                    <span class="d-none d-md-inline">编辑</span>
                  </button>
                </router-link>
                <b-button @click="prepareRemove(tenant)" variant="danger" class="btn btn-sm" :disabled="tenant.code === 'system'">
                  <font-awesome-icon icon="times"></font-awesome-icon>
                  <span class="d-none d-md-inline">删除</span>
                </b-button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <b-modal ref="removeTenant" id="removeTenant" title="确认删除" @ok="deleteTenant()">
        <div class="modal-body">
          <p id="jhi-delete-tenant-heading">你确定要删除租户 {{ removeId }} ?</p>
        </div>
        <template #modal-footer>
          <div>
            <button type="button" class="btn btn-secondary" @click="closeDialog()">取消</button>
            <button type="button" class="btn btn-primary" id="confirm-delete-tenant" @click="deleteTenant()">删除</button>
          </div>
        </template>
      </b-modal>
    </div>
  </div>
</template>

<script lang="ts" src="./tenant-management.component.ts"></script>