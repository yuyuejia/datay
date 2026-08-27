<template>
  <div>
    <h2 id="page-heading" data-cy="DataSourceHeading" class="d-flex align-items-center justify-content-between flex-wrap-nowrap">
      <span id="data-source-heading">数据源列表</span>
      <div class="d-flex align-items-center">
        <input
          type="text"
          class="form-control mr-2"
          style="width: 280px"
          v-model="search"
          placeholder="按名称 / IP / 端口 / 地址搜索"
        />
        <router-link :to="{ name: 'DataSourceCreate' }" custom v-slot="{ navigate }">
          <button
            @click="navigate"
            id="jh-create-entity"
            data-cy="entityCreateButton"
            class="btn btn-primary jh-create-entity create-data-source"
          >
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>创建数据源</span>
          </button>
        </router-link>
      </div>
    </h2>
    <br />
    <div class="alert alert-warning" v-if="!isFetching && dataSources && dataSources.length === 0">
      <span>未找到数据源</span>
    </div>
    <div v-if="dataSources && dataSources.length > 0">
      <!-- 使用 el-table 组件，添加高度支持滚动条，绑定排序事件 -->
      <el-table :data="dataSources" style="width: 100%" @sort-change="handleSortChange">
        <el-table-column prop="id" label="ID" sortable="custom" width="100">
          <template #default="scope">
            <router-link :to="{ name: 'DataSourceView', params: { dataSourceId: scope.row.id } }">{{ scope.row.id }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" sortable="custom" width="150"></el-table-column>
        <el-table-column prop="description" label="描述" sortable="custom" width="150"></el-table-column>
        <el-table-column prop="type" label="类型" sortable="custom" width="100"></el-table-column>
        <el-table-column prop="url" label="地址" sortable="custom" width="350"></el-table-column>
        <el-table-column prop="hostname" label="IP/主机" sortable="custom" width="150"></el-table-column>
        <el-table-column prop="port" label="端口" sortable="custom" width="100"></el-table-column>
        <el-table-column prop="schemaName" label="数据库名" sortable="custom" width="150"></el-table-column>
        <el-table-column prop="username" label="用户名" sortable="custom" width="150"></el-table-column>
        <!-- <el-table-column prop="password" label="密码" sortable="custom" width="150"></el-table-column> -->
        <!-- <el-table-column prop="updateTime" label="更新时间" sortable="custom" width="150">
          <template #default="scope">
            {{ formatDateShort(scope.row.updateTime) || '' }}
          </template>
        </el-table-column> -->
        <el-table-column prop="createTime" label="创建时间" sortable="custom" width="150">
          <template #default="scope">
            {{ formatDateShort(scope.row.createTime) || '' }}
          </template>
        </el-table-column>
        <!-- <el-table-column prop="tenantId" label="租户ID" sortable="custom" width="150"></el-table-column> -->
        <el-table-column label="操作" fixed="right" min-width="200">
          <template #default="scope">
            <div class="btn-group">
              <router-link :to="{ name: 'DataSourceView', params: { dataSourceId: scope.row.id } }" custom v-slot="{ navigate }">
                <el-button @click="navigate" class="btn btn-info btn-sm details" data-cy="entityDetailsButton">
                  <span class="d-none d-md-inline">查看</span>
                </el-button>
              </router-link>
              <router-link :to="{ name: 'DataSourceEdit', params: { dataSourceId: scope.row.id } }" custom v-slot="{ navigate }">
                <el-button @click="navigate" class="btn btn-primary btn-sm edit" data-cy="entityEditButton">
                  <span class="d-none d-md-inline">编辑</span>
                </el-button>
              </router-link>
              <el-button
                @click="prepareRemove(scope.row)"
                variant="danger"
                class="btn btn-danger btn-sm"
                data-cy="entityDeleteButton"
                v-b-modal.removeEntity
              >
                <span class="d-none d-md-inline">删除</span>
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <b-modal ref="removeEntity" id="removeEntity">
      <template #modal-title>
        <span id="datafusionApp.dataSource.delete.question" data-cy="dataSourceDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p id="jhi-delete-dataSource-heading">确定要删除数据源 {{ removeId }} 吗？</p>
      </div>
      <template #modal-footer>
        <div>
          <button type="button" class="btn btn-secondary" @click="closeDialog()">取消</button>
          <button
            type="button"
            class="btn btn-primary"
            id="jhi-confirm-delete-dataSource"
            data-cy="entityConfirmDeleteButton"
            @click="removeDataSource()"
          >
            删除
          </button>
        </div>
      </template>
    </b-modal>
    <div v-show="dataSources && dataSources.length > 0">
      <div class="row justify-content-center">
        <jhi-item-count :page="page" :total="queryCount" :itemsPerPage="itemsPerPage"></jhi-item-count>
      </div>
      <div class="row justify-content-center">
        <b-pagination size="md" :total-rows="totalItems" v-model="page" :per-page="itemsPerPage"></b-pagination>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./data-source.component.ts"></script>
