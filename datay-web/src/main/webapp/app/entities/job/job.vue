<template>
  <div>
    <h2 id="page-heading" data-cy="JobHeading" class="d-flex align-items-center justify-content-between flex-wrap-nowrap">
      <span id="job-heading">Job 列表</span>
      <div class="d-flex align-items-center">
        <input
          type="text"
          class="form-control mr-2"
          style="width: 280px"
          v-model="search"
          placeholder="按名称 / 类型 / 状态搜索"
        />
        <router-link :to="{ name: 'JobCreate' }" custom v-slot="{ navigate }">
          <button @click="navigate" id="jh-create-entity" data-cy="entityCreateButton" class="btn btn-primary jh-create-entity create-job">
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>创建 Job</span>
          </button>
        </router-link>
      </div>
    </h2>
    <br />
    <div class="alert alert-warning" v-if="!isFetching && jobs && jobs.length === 0">
      <span>No Jobs found</span>
    </div>
    <div v-if="jobs && jobs.length > 0">
      <!-- 使用 el-table 组件，添加高度支持滚动条，绑定排序事件 -->
      <el-table :data="jobs" style="width: 100%" @sort-change="handleSortChange">
        <el-table-column prop="id" label="ID" sortable="custom" width="100">
          <template #default="scope">
            <router-link :to="{ name: 'JobView', params: { jobId: scope.row.id } }">{{ scope.row.id }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="jobName" label="名称" sortable="custom" width="150"></el-table-column>
        <el-table-column prop="type" label="类型" sortable="custom" width="100"></el-table-column>
        <el-table-column prop="cron" label="调度表达式" sortable="custom" width="150"></el-table-column>
        <el-table-column prop="jobContext" label="配置" sortable="custom" width="250" show-overflow-tooltip>
          <template #default="scope">
            <el-tooltip :content="scope.row.jobContext" placement="top">
              <span class="single-line-overflow">{{ scope.row.jobContext }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" sortable="custom" width="150"></el-table-column>
        <el-table-column prop="updateTime" label="更新时间" sortable="custom" width="150">
          <template #default="scope">
            {{ formatDateShort(scope.row.updateTime) || '' }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" sortable="custom" width="150">
          <template #default="scope">
            {{ formatDateShort(scope.row.createTime) || '' }}
          </template>
        </el-table-column>
        <!-- <el-table-column prop="project" label="Project" sortable="custom" width="150"></el-table-column> -->
        <!-- <el-table-column prop="tenantId" label="Tenant Id" sortable="custom" width="150"></el-table-column> -->
        <el-table-column label="操作" fixed="right" min-width="220">
          <template #default="scope">
            <div class="btn-group">
              <router-link :to="{ name: 'JobView', params: { jobId: scope.row.id } }" custom v-slot="{ navigate }">
                <el-button @click="navigate" class="btn btn-info btn-sm details" data-cy="entityDetailsButton">
                  <span class="d-none d-md-inline">查看</span>
                </el-button>
              </router-link>
              <el-button @click="handleExecuteOnce(scope.row)" class="btn btn-success btn-sm" data-cy="entityExecuteButton">
                <span class="d-none d-md-inline">执行一次</span>
              </el-button>
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
        <span id="datafusionApp.job.delete.question" data-cy="jobDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p id="jhi-delete-job-heading">你确定要删除 Job {{ removeId }} 吗？</p>
      </div>
      <template #modal-footer>
        <div>
          <button type="button" class="btn btn-secondary" @click="closeDialog()">取消</button>
          <button
            type="button"
            class="btn btn-primary"
            id="jhi-confirm-delete-job"
            data-cy="entityConfirmDeleteButton"
            @click="removeJob()"
          >
            删除
          </button>
        </div>
      </template>
    </b-modal>
    <div v-show="jobs && jobs.length > 0">
      <div class="row justify-content-center">
        <jhi-item-count :page="page" :total="queryCount" :itemsPerPage="itemsPerPage"></jhi-item-count>
      </div>
      <div class="row justify-content-center">
        <b-pagination size="md" :total-rows="totalItems" v-model="page" :per-page="itemsPerPage"></b-pagination>
      </div>
    </div>
  </div>
</template>

<style scoped>
.single-line-overflow {
  display: inline-block;
  width: 90%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>

<script lang="ts" src="./job.component.ts"></script>