<template>
  <div>
    <h2 id="page-heading" data-cy="userManagementPageHeading">
      <span id="user-management-page-heading">用户</span>

      <div class="d-flex align-items-center">
        <el-button type="info" class="mr-2" @click="handleSyncList" :disabled="isLoading">
          <font-awesome-icon icon="sync" :spin="isLoading"></font-awesome-icon> <span>刷新列表</span>
        </el-button>
        <router-link custom v-slot="{ navigate }" :to="{ name: 'JhiUserCreate' }">
          <el-button type="primary" @click="navigate" class="jh-create-entity">
            <font-awesome-icon icon="plus"></font-awesome-icon> <span>创建新用户</span>
          </el-button>
        </router-link>
      </div>
    </h2>
    <div v-if="users">
      <el-table :data="users" style="width: 100%" @sort-change="handleSortChange">
        <el-table-column prop="id" label="ID" sortable="custom" width="90">
          <template #default="{ row }">
            <router-link :to="{ name: 'JhiUserView', params: { userId: row.login } }">{{ row.id }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="login" label="登录" sortable="custom" min-width="130" />
        <el-table-column prop="email" label="邮箱" sortable="custom" min-width="200" />
        <el-table-column label="激活状态" width="130">
          <template #default="{ row }">
            <el-button v-if="!row.activated" type="danger" size="small" @click="setActive(row, true)">失效</el-button>
            <el-button v-else type="success" size="small" :disabled="username === row.login" @click="setActive(row, false)">
              已激活
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="authority in row.authorities" :key="authority" size="small" class="mr-1">{{ authority }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdDate" label="创建时间" sortable="custom" width="170">
          <template #default="{ row }">{{ formatDate(row.createdDate) }}</template>
        </el-table-column>
        <el-table-column prop="lastModifiedBy" label="最近修改人" sortable="custom" min-width="140" />
        <el-table-column prop="lastModifiedDate" label="最近修改时间" sortable="custom" width="170">
          <template #default="{ row }">{{ formatDate(row.lastModifiedDate) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="230">
          <template #default="{ row }">
            <div class="btn-group">
              <router-link :to="{ name: 'JhiUserView', params: { userId: row.login } }" custom v-slot="{ navigate }">
                <el-button type="info" size="small" @click="navigate" class="details">
                  <font-awesome-icon icon="eye"></font-awesome-icon>
                  <span class="d-none d-md-inline">查看</span>
                </el-button>
              </router-link>
              <router-link :to="{ name: 'JhiUserEdit', params: { userId: row.login } }" custom v-slot="{ navigate }">
                <el-button type="primary" size="small" @click="navigate" class="edit">
                  <font-awesome-icon icon="pencil-alt"></font-awesome-icon>
                  <span class="d-none d-md-inline">编辑</span>
                </el-button>
              </router-link>
              <el-button size="small" @click="prepareRemove(row)" type="danger" class="delete" :disabled="username === row.login">
                <font-awesome-icon icon="times"></font-awesome-icon>
                <span class="d-none d-md-inline">删除</span>
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <app-modal ref="removeUser" id="removeUser" title="确认删除" @ok="deleteUser()">
        <div class="modal-body">
          <p id="jhi-delete-user-heading">你确定要删除用户 {{ removeId }} ?</p>
        </div>
        <template #modal-footer>
          <div>
            <el-button @click="closeDialog()">取消</el-button>
            <el-button type="primary" id="confirm-delete-user" @click="deleteUser()">删除</el-button>
          </div>
        </template>
      </app-modal>
    </div>
    <div v-show="users && users.length > 0">
      <div class="list-pagination">
        <jhi-item-count :page="page" :total="queryCount" :items-per-page="itemsPerPage"></jhi-item-count>
        <el-pagination
          background
          layout="sizes, prev, pager, next, jumper"
          :total="totalItems"
          :page-sizes="[10, 20, 50, 100]"
          :pager-count="7"
          v-model:current-page="page"
          v-model:page-size="itemsPerPage"
          @current-change="loadPage"
          @size-change="handleSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./user-management.component.ts"></script>
