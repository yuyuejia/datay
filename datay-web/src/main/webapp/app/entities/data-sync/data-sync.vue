<template>
  <div>
    <h2 id="page-heading" data-cy="DataSyncHeading">
      <span id="data-sync-heading">数据同步</span>
      <div class="d-flex justify-content-end">
        <button class="btn btn-info mr-2" @click="handleSyncList" :disabled="isFetching">
          <font-awesome-icon icon="sync" :spin="isFetching"></font-awesome-icon> <span>刷新列表</span>
        </button>
        <router-link :to="{ name: 'DataSyncCreate' }" custom v-slot="{ navigate }">
          <button
            @click="navigate"
            id="jh-create-entity"
            data-cy="entityCreateButton"
            class="btn btn-primary jh-create-entity create-data-sync"
          >
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>创建新数据同步</span>
          </button>
        </router-link>
      </div>
    </h2>
    <br />
    <div class="alert alert-warning" v-if="!isFetching && dataSyncs && dataSyncs.length === 0">
      <span>未找到数据同步</span>
    </div>
    <div v-if="dataSyncs && dataSyncs.length > 0">
      <!-- 使用 el-table 组件，添加高度支持滚动条，绑定排序事件 -->
      <el-table :data="dataSyncs" style="width: 100%" @sort-change="handleSortChange">
        <el-table-column prop="id" label="ID" sortable="custom" width="100">
          <template #default="scope">
            <router-link :to="{ name: 'DataSyncView', params: { dataSyncId: scope.row.id } }">{{ scope.row.id }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="jobName" label="任务名称" sortable="custom" width="150"></el-table-column>
        <el-table-column prop="jobDesc" label="任务描述" sortable="custom" width="150"></el-table-column>
        <el-table-column prop="type" label="类型" sortable="custom" width="150"></el-table-column>
        <!-- <el-table-column prop="source" label="Source" sortable="custom" width="150"></el-table-column>
        <el-table-column prop="target" label="Target" sortable="custom" width="150"></el-table-column> -->
        <el-table-column prop="cron" label="定时表达式" sortable="custom" width="150"></el-table-column>
        <el-table-column prop="status" label="状态" sortable="custom" width="150"></el-table-column>
        <!-- <el-table-column prop="lastStatus" label="Last Status" sortable="custom" width="150"></el-table-column> -->
        <el-table-column prop="createTime" label="创建时间" sortable="custom" width="150">
          <template #default="scope">
            {{ formatDateShort(scope.row.createTime) || '' }}
          </template>
        </el-table-column>
        <el-table-column label="" fixed="right" min-width="260">
          <template #default="scope">
            <div class="btn-group">
              <el-button
                @click="executeDataSync(scope.row)"
                class="btn btn-success btn-sm"
                data-cy="entityExecuteButton"
              >
                <span class="d-none d-md-inline">立即执行</span>
              </el-button>
              <router-link :to="{ name: 'DataSyncView', params: { dataSyncId: scope.row.id } }" custom v-slot="{ navigate }">
                <el-button @click="navigate" class="btn btn-info btn-sm details" data-cy="entityDetailsButton">
                  <span class="d-none d-md-inline">查看</span>
                </el-button>
              </router-link>
              <router-link :to="{ name: 'DataSyncEdit', params: { dataSyncId: scope.row.id } }" custom v-slot="{ navigate }">
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
        <span id="datafusionApp.dataSync.delete.question" data-cy="dataSyncDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p id="jhi-delete-dataSync-heading">你确定要删除数据同步 {{ removeId }} 吗？</p>
      </div>
      <template #modal-footer>
        <div>
          <button type="button" class="btn btn-secondary" @click="closeDialog()">取消</button>
          <button
            type="button"
            class="btn btn-primary"
            id="jhi-confirm-delete-dataSync"
            data-cy="entityConfirmDeleteButton"
            @click="removeDataSync()"
          >
            删除
          </button>
        </div>
      </template>
    </b-modal>
    <div v-show="dataSyncs && dataSyncs.length > 0">
      <div class="row justify-content-center">
        <jhi-item-count :page="page" :total="queryCount" :itemsPerPage="itemsPerPage"></jhi-item-count>
      </div>
      <div class="row justify-content-center">
        <b-pagination size="md" :total-rows="totalItems" v-model="page" :per-page="itemsPerPage"></b-pagination>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./data-sync.component.ts"></script>