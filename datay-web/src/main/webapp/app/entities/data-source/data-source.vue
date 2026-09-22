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
        <button
          @click="openDefaultDialog"
          id="jh-set-default-warehouse"
          data-cy="setDefaultWarehouseButton"
          class="btn btn-warning create-data-source mr-2"
        >
          <font-awesome-icon icon="database"></font-awesome-icon>
          <span>设置默认数仓</span>
        </button>
        <button
          @click="openCreateModal"
          id="jh-create-entity"
          data-cy="entityCreateButton"
          class="btn btn-primary jh-create-entity create-data-source"
        >
          <font-awesome-icon icon="plus"></font-awesome-icon>
          <span>创建数据源</span>
        </button>
      </div>
    </h2>
    <br />
    <div class="alert alert-warning" v-if="!isFetching && dataSources && dataSources.length === 0">
      <span>未找到数据源</span>
    </div>
    <div v-if="dataSources && dataSources.length > 0">
      <el-table :data="dataSources" style="width: 100%" @sort-change="handleSortChange">
        <el-table-column prop="name" label="名称" sortable="custom" width="150"></el-table-column>
        <el-table-column label="默认数仓" width="100">
          <template #default="scope">
            <el-tag v-if="isDefaultWarehouse(scope.row)" type="success" size="small">默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" sortable="custom" width="150"></el-table-column>
        <el-table-column prop="type" label="类型" sortable="custom" width="100"></el-table-column>
        <el-table-column prop="url" label="地址" sortable="custom" width="350"></el-table-column>
        <el-table-column prop="schemaName" label="数据库名" sortable="custom" width="150"></el-table-column>
        <el-table-column prop="username" label="用户名" sortable="custom" width="150"></el-table-column>
        <el-table-column prop="createTime" label="创建时间" sortable="custom" width="150">
          <template #default="scope">
            {{ formatDateShort(scope.row.createTime) || '' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="260">
          <template #default="scope">
            <div class="btn-group">
              <el-button @click="openEditModal(scope.row)" class="btn btn-primary btn-sm edit" data-cy="entityEditButton">
                <span class="d-none d-md-inline">编辑</span>
              </el-button>
              <router-link :to="{ name: 'DataSourceQuery', params: { dataSourceId: scope.row.id } }" custom v-slot="{ navigate }">
                <el-button @click="navigate" class="btn btn-success btn-sm query" data-cy="entityQueryButton">
                  <span class="d-none d-md-inline">数据查询</span>
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

    <b-modal ref="defaultWarehouseModal" id="defaultWarehouseModal">
      <template #modal-title>
        <span id="datafusionApp.dataSource.defaultWarehouse.title">设置默认数仓</span>
      </template>
      <div class="modal-body">
        <p>选择该租户的默认数仓，数据模型物化时将默认使用该数据源。</p>
        <select class="form-control" v-model="selectedDefaultId" style="width: 320px">
          <option :value="null" disabled>请选择数据源</option>
          <option v-for="ds in defaultWarehouseOptions" :key="ds.id" :value="ds.id">{{ ds.name }} ({{ ds.type }})</option>
        </select>
      </div>
      <template #modal-footer>
        <div>
          <button type="button" class="btn btn-secondary" @click="closeDefaultDialog()">取消</button>
          <button
            type="button"
            class="btn btn-primary"
            id="jhi-confirm-default-warehouse"
            data-cy="confirmDefaultWarehouseButton"
            @click="saveDefaultWarehouse()"
          >
            保存
          </button>
        </div>
      </template>
    </b-modal>

    <data-source-modal
      v-model:show="modalShow"
      :mode="modalMode"
      :data-source-id="modalDataSourceId"
      @saved="onModalSaved"
    ></data-source-modal>

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