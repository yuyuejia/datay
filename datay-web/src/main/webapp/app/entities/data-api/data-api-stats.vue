<template>
  <div class="data-api-stats-container">
    <div class="page-header">
      <div class="header-left">
        <el-button link @click="backToList">
          <font-awesome-icon icon="arrow-left" class="mr-1" />
          <span>返回</span>
        </el-button>
        <div class="title-block">
          <h3>{{ apiTitle }}</h3>
          <span class="api-code" v-if="apiCode">{{ apiCode }}</span>
        </div>
      </div>
      <div class="header-actions">
        <el-button-group>
          <el-button @click="applyQuickRange(7)">近 7 天</el-button>
          <el-button @click="applyQuickRange(30)">近 30 天</el-button>
        </el-button-group>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          @change="handleRangeChange"
        />
        <el-button type="info" :loading="statsLoading || logsLoading" @click="refresh">
          <font-awesome-icon icon="sync" /> <span>刷新</span>
        </el-button>
      </div>
    </div>

    <div class="kpi-grid" v-loading="statsLoading">
      <div class="kpi-card">
        <div class="kpi-label">总调用次数</div>
        <div class="kpi-value">{{ stats?.totalCount ?? 0 }}</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">成功率</div>
        <div class="kpi-value success">{{ (stats?.successRate ?? 0).toFixed(2) }}%</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">平均耗时</div>
        <div class="kpi-value">{{ (stats?.avgDurationMs ?? 0).toFixed(1) }} ms</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">失败次数</div>
        <div class="kpi-value danger">{{ stats?.failureCount ?? 0 }}</div>
      </div>
    </div>

    <div class="chart-card" v-loading="statsLoading">
      <div class="card-title">调用趋势</div>
      <div class="chart-wrapper">
        <EChart :option="chartOption" />
      </div>
    </div>

    <div class="log-card">
      <div class="card-header">
        <div class="card-title">调用日志</div>
        <div class="card-actions">
          <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px" @change="handleStatusChange">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
          <el-button type="danger" plain @click="clearLogs">清空日志</el-button>
        </div>
      </div>
      <div class="alert alert-warning" v-if="!logsLoading && logs.length === 0">当前时间范围内暂无调用日志</div>
      <el-table v-else :data="logs" v-loading="logsLoading" style="width: 100%">
        <el-table-column label="调用时间" width="180">
          <template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="调用方" min-width="150" show-overflow-tooltip>
          <template #default="scope">
            <div>{{ scope.row.callerLogin || '未知用户' }}</div>
            <div class="text-muted small">{{ scope.row.clientIp || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="110" align="center">
          <template #default="scope">
            <el-tag size="small" type="info">{{ sourceLabel(scope.row.requestSource) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="请求方式" width="90" align="center">
          <template #default="scope">{{ scope.row.requestMethod || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.status)" size="small">{{ statusLabel(scope.row.status) }}</el-tag>
            <span class="http-status">{{ scope.row.httpStatus || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100" align="right">
          <template #default="scope">{{ formatDuration(scope.row.durationMs) }}</template>
        </el-table-column>
        <el-table-column label="返回行数" width="100" align="right">
          <template #default="scope">{{ scope.row.resultRows ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="错误信息" min-width="200" show-overflow-tooltip>
          <template #default="scope">
            <span class="error-text">{{ scope.row.errorMessage || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openDetail(scope.row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="list-pagination" v-show="totalItems > 0">
        <jhi-item-count :page="page" :total="totalItems" :items-per-page="itemsPerPage"></jhi-item-count>
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

    <app-modal v-model="detailVisible" title="调用详情" size="lg" ok-only ok-title="关闭">
      <div class="modal-body" v-if="currentLog">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="调用时间" :span="2">{{ formatDateTime(currentLog.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="服务编码">{{ currentLog.apiCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="服务名称">{{ currentLog.apiName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="调用来源">{{ sourceLabel(currentLog.requestSource) }}</el-descriptions-item>
          <el-descriptions-item label="请求方式">{{ currentLog.requestMethod || '-' }}</el-descriptions-item>
          <el-descriptions-item label="调用方">{{ currentLog.callerLogin || '-' }}</el-descriptions-item>
          <el-descriptions-item label="用户 ID">{{ currentLog.callerUserId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户端 IP">{{ currentLog.clientIp || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(currentLog.status)" size="small">{{ statusLabel(currentLog.status) }}</el-tag>
            <span class="ml-2">{{ currentLog.httpStatus || '' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ formatDuration(currentLog.durationMs) }}</el-descriptions-item>
          <el-descriptions-item label="返回行数">{{ currentLog.resultRows ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="请求路径" :span="2">{{ currentLog.requestPath || '-' }}</el-descriptions-item>
          <el-descriptions-item label="User-Agent" :span="2">{{ currentLog.userAgent || '-' }}</el-descriptions-item>
          <el-descriptions-item label="请求参数" :span="2">
            <pre class="detail-pre">{{ formatParams(currentLog.requestParams) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item label="错误信息" :span="2">
            <pre class="detail-pre error-pre">{{ currentLog.errorMessage || '-' }}</pre>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #modal-footer>
        <el-button type="primary" @click="closeDetail">关闭</el-button>
      </template>
    </app-modal>
  </div>
</template>

<script lang="ts" src="./data-api-stats.component.ts"></script>

<style scoped>
.data-api-stats-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: 24px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.title-block h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.api-code {
  font-size: 12px;
  color: #909399;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.kpi-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 18px 20px;
}

.kpi-label {
  color: #909399;
  font-size: 13px;
  margin-bottom: 8px;
}

.kpi-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.kpi-value.success {
  color: #67c23a;
}

.kpi-value.danger {
  color: #f56c6c;
}

.chart-card,
.log-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px 20px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.card-header .card-title {
  margin-bottom: 0;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chart-wrapper {
  height: 320px;
}

.small {
  font-size: 12px;
}

.http-status {
  margin-left: 6px;
  color: #909399;
  font-size: 12px;
}

.error-text {
  color: #f56c6c;
}

.detail-pre {
  margin: 0;
  max-height: 240px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
}

.error-pre {
  color: #f56c6c;
}

.ml-2 {
  margin-left: 8px;
}

@media (max-width: 900px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
