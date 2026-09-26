<template>
  <div>
    <h2
      id="page-heading"
      data-cy="AnalysisDashboardHeading"
      class="d-flex align-items-center justify-content-between flex-wrap-nowrap"
    >
      <span id="analysis-dashboard-heading">分析看板</span>
      <div class="d-flex align-items-center">
        <input
          type="text"
          class="form-control mr-2"
          style="width: 260px"
          v-model="search"
          placeholder="按名称 / 编码 / 描述搜索"
        />
        <el-button type="primary" @click="design()">
          <font-awesome-icon
            icon="wand-magic-sparkles"
            class="mr-1"
          ></font-awesome-icon>
          AI 创建看板
        </el-button>
      </div>
    </h2>
    <br />
    <div
      class="alert alert-warning"
      v-if="!isFetching && dashboards && dashboards.length === 0"
    >
      <span>暂无分析看板，点击「AI 创建看板」开始设计</span>
    </div>
    <div v-if="dashboards && dashboards.length > 0">
      <el-table :data="dashboards" style="width: 100%" v-loading="isFetching">
        <el-table-column
          prop="name"
          label="名称"
          min-width="160"
        ></el-table-column>
        <el-table-column prop="code" label="编码" width="180"></el-table-column>
        <el-table-column
          prop="description"
          label="描述"
          min-width="200"
          show-overflow-tooltip
        ></el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="scope">
            <el-tag
              :type="scope.row.status === 'DISABLED' ? 'info' : 'success'"
              size="small"
            >
              {{ statusLabel(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="scope">{{
            formatDateShort(scope.row.createTime) || ""
          }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="340">
          <template #default="scope">
            <div class="btn-group">
              <el-button
                class="btn btn-primary btn-sm"
                @click="openInNewTab(scope.row)"
              >
                <span class="d-none d-md-inline">新页签打开</span>
              </el-button>
              <el-button class="btn btn-info btn-sm" @click="design(scope.row)">
                <span class="d-none d-md-inline">重新设计</span>
              </el-button>
              <el-button
                class="btn btn-warning btn-sm"
                @click="rename(scope.row)"
              >
                <span class="d-none d-md-inline">重命名</span>
              </el-button>
              <el-button
                class="btn btn-danger btn-sm"
                @click="prepareRemove(scope.row)"
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
        <span>确认删除</span>
      </template>
      <div class="modal-body">
        <p>确定要删除看板 {{ removeId }} 吗？</p>
      </div>
      <template #modal-footer>
        <div>
          <button
            type="button"
            class="btn btn-secondary"
            @click="closeDialog()"
          >
            取消
          </button>
          <button
            type="button"
            class="btn btn-primary"
            @click="removeDashboard()"
          >
            删除
          </button>
        </div>
      </template>
    </b-modal>
  </div>
</template>

<script lang="ts" src="./analysis-dashboard.component.ts"></script>
