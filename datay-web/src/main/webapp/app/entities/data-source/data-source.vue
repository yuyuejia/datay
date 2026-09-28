<template>
  <div>
    <h2 id="page-heading" data-cy="DataSourceHeading" class="d-flex align-items-center justify-content-between flex-wrap-nowrap">
      <span id="data-source-heading">数据源列表</span>
      <div class="d-flex align-items-center">
        <el-input class="mr-2" style="width: 280px" v-model="search" placeholder="按名称 / IP / 端口 / 地址搜索" clearable/>
        <el-button type="warning" @click="openDefaultDialog" id="jh-set-default-warehouse" data-cy="setDefaultWarehouseButton" class="create-data-source mr-2">
          <font-awesome-icon icon="database"></font-awesome-icon>
          <span>设置默认数仓</span>
        </el-button>
        <el-button type="primary" @click="openCreateModal" id="jh-create-entity" data-cy="entityCreateButton" class="jh-create-entity create-data-source">
          <font-awesome-icon icon="plus"></font-awesome-icon>
          <span>创建数据源</span>
        </el-button>
      </div>
    </h2>
    <br />
    <div class="alert alert-warning" v-if="!isFetching && dataSources && dataSources.length === 0">
      <span>未找到数据源</span>
    </div>
    <div v-if="dataSources && dataSources.length > 0">
      <el-table :data="dataSources" style="width: 100%" @sort-change="handleSortChange">
        <el-table-column prop="name" label="名称" sortable="custom" width="150"></el-table-column>
        <el-table-column prop="description" label="描述" sortable="custom" width="260" show-overflow-tooltip></el-table-column>
        <el-table-column prop="type" label="类型" sortable="custom" width="140">
          <template #default="scope">
            <span class="type-cell">
              <img v-if="getTypeImage(scope.row.type)" :src="getTypeImage(scope.row.type)" class="type-icon" :alt="scope.row.type" />
              <font-awesome-icon v-else icon="database" class="type-fa-icon" />
              <span>{{ scope.row.type }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="url" label="地址" sortable="custom" width="350" show-overflow-tooltip></el-table-column>
        <el-table-column prop="schemaName" label="数据库名" sortable="custom" width="150"></el-table-column>
        <el-table-column prop="createTime" label="创建时间" sortable="custom" width="150">
          <template #default="scope">
            {{ formatDateShort(scope.row.createTime) || '' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="260">
          <template #default="scope">
            <div class="btn-group">
              <el-button type="primary" size="small" @click="openEditModal(scope.row)" class="edit" data-cy="entityEditButton">
                <span class="d-none d-md-inline">编辑</span>
              </el-button>
              <router-link :to="{ name: 'DataSourceQuery', params: { dataSourceId: scope.row.id } }" custom v-slot="{ navigate }">
                <el-button type="success" size="small" @click="navigate" class="query" data-cy="entityQueryButton">
                  <span class="d-none d-md-inline">数据查询</span>
                </el-button>
              </router-link>
              <el-button size="small" @click="prepareRemove(scope.row)" type="danger" data-cy="entityDeleteButton">
                <span class="d-none d-md-inline">删除</span>
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <app-modal ref="removeEntity" id="removeEntity">
      <template #modal-title>
        <span id="datafusionApp.dataSource.delete.question" data-cy="dataSourceDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p id="jhi-delete-dataSource-heading">确定要删除数据源 {{ removeId }} 吗？</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeDialog()">取消</el-button>
          <el-button type="primary" id="jhi-confirm-delete-dataSource" data-cy="entityConfirmDeleteButton" @click="removeDataSource()">
            删除
          </el-button>
        </div>
      </template>
    </app-modal>

    <app-modal ref="defaultWarehouseModal" id="defaultWarehouseModal">
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
          <el-button @click="closeDefaultDialog()">取消</el-button>
          <el-button type="primary" id="jhi-confirm-default-warehouse" data-cy="confirmDefaultWarehouseButton" @click="saveDefaultWarehouse()">
            保存
          </el-button>
        </div>
      </template>
    </app-modal>

    <data-source-modal
      v-model:show="modalShow"
      :mode="modalMode"
      :data-source-id="modalDataSourceId"
      @saved="onModalSaved"
    ></data-source-modal>

    <div v-show="dataSources && dataSources.length > 0">
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
        />
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./data-source.component.ts"></script>

<style scoped>
.type-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.type-cell > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.type-icon {
  width: 18px;
  height: 18px;
  object-fit: contain;
  flex-shrink: 0;
}

.type-fa-icon {
  color: #409eff;
  font-size: 14px;
  flex-shrink: 0;
}
</style>