<template>
  <div>
    <h2 id="page-heading" class="d-flex align-items-center justify-content-between flex-nowrap flex-wrap-nowrap">
      <span id="data-compare-heading">数据比对</span>
      <div class="d-flex align-items-center">
        <el-input
          v-model="search"
          class="mr-2"
          style="width: 240px"
          placeholder="按任务名称 / 描述搜索"
          clearable
          @keyup.enter="retrieveTasks"
          @clear="retrieveTasks"
        />
        <el-input v-model="instanceQueryId" class="mr-2" style="width: 260px" placeholder="按执行实例 ID 查询结果" clearable />
        <el-button class="mr-2" @click="queryByInstanceId" :disabled="!instanceQueryId.trim()">查询实例</el-button>
        <el-button type="info" class="mr-2" @click="retrieveTasks" :disabled="isFetching">
          <font-awesome-icon icon="sync" :spin="isFetching"></font-awesome-icon>
          <span>刷新</span>
        </el-button>
        <router-link :to="{ name: 'DataCompareCreate' }" custom v-slot="{ navigate }">
          <el-button type="primary" @click="navigate" id="jh-create-entity" data-cy="entityCreateButton">
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>创建比对任务</span>
          </el-button>
        </router-link>
      </div>
    </h2>

    <div class="alert alert-warning" v-if="!isFetching && tasks.length === 0">
      <span>未找到数据比对任务</span>
    </div>

    <div v-if="tasks.length > 0" class="list-page-scroll">
      <el-table :data="tasks" style="width: 100%">
        <el-table-column prop="taskName" label="任务名称" min-width="200"></el-table-column>
        <el-table-column label="比对配置" min-width="320">
          <template #default="scope">
            <div class="compare-config">
              <div v-for="(table, index) in (scope.row.tables || []).slice(0, 2)" :key="index">
                <span class="config-label">表{{ index + 1 }}</span>
                {{ table.leftTable }} → {{ table.rightTable }}
              </div>
              <div v-if="(scope.row.tables || []).length > 2" class="config-keys">另有 {{ scope.row.tables.length - 2 }} 张表</div>
              <div v-if="!(scope.row.tables || []).length" class="config-keys">未配置比对表</div>
              <div class="config-keys">
                数据源：{{ dataSourceLabel(scope.row.leftDataSourceId) }} → {{ dataSourceLabel(scope.row.rightDataSourceId) }}
                <el-tag v-if="scope.row.compareDetail === false" size="small" type="info" effect="plain">仅记录数</el-tag>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="最近状态" width="120">
          <template #default="scope">
            <span :class="'status-badge status-' + ((scope.row.lastStatus || scope.row.status || '').toLowerCase() || '')">
              {{ statusText(scope.row.lastStatus || scope.row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="差异行数" width="120">
          <template #default="scope">
            <el-tag v-if="scope.row.lastResult?.diffRowCount != null" :type="scope.row.lastResult.diffRowCount > 0 ? 'danger' : 'success'">
              {{ scope.row.lastResult.diffRowCount }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="最近执行" width="170">
          <template #default="scope">{{ formatDate(scope.row.lastRunTime || scope.row.lastResult?.createTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="300">
          <template #default="scope">
            <el-button type="primary" size="small" @click="goEdit(scope.row)">编辑</el-button>
            <el-button
              type="success"
              size="small"
              :loading="executingId === scope.row.id"
              :disabled="isTaskRunning(scope.row)"
              @click="executeTask(scope.row)"
            >
              立即执行
            </el-button>
            <el-button type="info" plain size="small" @click="openResults(scope.row)">结果</el-button>
            <el-button type="danger" plain size="small" @click="deleteTask(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="total > size" class="d-flex justify-content-end mt-3">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <el-dialog v-model="resultsDialogVisible" :title="resultsDialogTitle" width="86%" destroy-on-close>
      <div v-if="activeResult" class="result-summary">
        <div class="result-toolbar">
          <div class="result-heading">
            <span class="result-title">比对汇总</span>
            <el-tag v-if="isLatestResult" type="success" effect="plain">最近一次</el-tag>
            <el-tag v-else type="info" effect="plain">历史执行</el-tag>
          </div>
          <div class="result-actions">
            <el-button
              type="primary"
              :disabled="!(selectedTable?.resultPath || activeResult.resultPath)"
              @click="downloadResult(activeResult, selectedTable)"
            >
              下载差异文件
            </el-button>
            <el-button :disabled="!activeResult.jobInstanceId" @click="downloadSummary(activeResult, selectedTable)"
              >下载汇总文件</el-button
            >
            <el-button :disabled="!activeResult.jobId || !activeResult.instanceCode" @click="openLog(activeResult)">查看日志</el-button>
            <el-button @click="historyVisible = !historyVisible">{{ historyVisible ? '收起历史实例' : '历史任务实例' }}</el-button>
          </div>
        </div>

        <div class="result-meta">
          <span class="meta-item">
            <span class="meta-label">执行实例</span>
            <span class="instance-id">{{ activeResult.jobInstanceId }}</span>
            <el-button link type="primary" size="small" @click="copyText(activeResult.jobInstanceId)">复制</el-button>
          </span>
          <span class="meta-item">
            <span class="meta-label">状态</span>
            <span :class="'status-badge status-' + ((activeResult.status || '').toLowerCase() || '')">
              {{ statusText(activeResult.status) }}
            </span>
          </span>
          <span class="meta-item">
            <span class="meta-label">执行时间</span>
            <span>{{ formatDate(activeResult.startTime || activeResult.createTime) || '-' }}</span>
          </span>
          <span class="meta-item">
            <span class="meta-label">执行区间</span>
            <span>{{ formatDate(activeResult.startTime) || '-' }} ~ {{ formatDate(activeResult.endTime) || '-' }}</span>
          </span>
        </div>

        <div class="compare-summary-grid">
          <div class="summary-card">
            <div class="summary-label">左侧行数</div>
            <div class="summary-value">{{ activeResult.leftRowCount ?? '-' }}</div>
          </div>
          <div class="summary-card">
            <div class="summary-label">右侧行数</div>
            <div class="summary-value">{{ activeResult.rightRowCount ?? '-' }}</div>
          </div>
          <template v-if="activeResult.compareDetail !== false">
            <div class="summary-card summary-card-success">
              <div class="summary-label">一致行数</div>
              <div class="summary-value">{{ activeResult.sameRowCount ?? '-' }}</div>
            </div>
            <div class="summary-card" :class="(activeResult.diffRowCount || 0) > 0 ? 'summary-card-danger' : 'summary-card-success'">
              <div class="summary-label">差异行数</div>
              <div class="summary-value">{{ activeResult.diffRowCount ?? '-' }}</div>
            </div>
            <div class="summary-card summary-card-warning">
              <div class="summary-label">仅左侧</div>
              <div class="summary-value">{{ activeResult.onlyLeftCount ?? '-' }}</div>
            </div>
            <div class="summary-card summary-card-warning">
              <div class="summary-label">仅右侧</div>
              <div class="summary-value">{{ activeResult.onlyRightCount ?? '-' }}</div>
            </div>
            <div class="summary-card summary-card-warning">
              <div class="summary-label">值差异</div>
              <div class="summary-value">{{ activeResult.valueDiffCount ?? '-' }}</div>
            </div>
          </template>
          <template v-else>
            <div class="summary-card" :class="(activeResult.diffRowCount || 0) > 0 ? 'summary-card-danger' : 'summary-card-success'">
              <div class="summary-label">记录数差异</div>
              <div class="summary-value">{{ activeResult.diffRowCount ?? '-' }}</div>
            </div>
          </template>
        </div>

        <el-alert v-if="activeResult.jobMessage" type="error" :closable="false" :title="activeResult.jobMessage" class="mt-3" />

        <div v-if="(activeResult.tables || []).length > 1" class="table-result-section">
          <div class="detail-section-title">
            <span>表比对结果</span>
            <span class="text-muted">共 {{ activeResult.tables?.length }} 张表，点击行查看该表明细</span>
          </div>
          <el-table
            :data="activeResult.tables"
            style="width: 100%"
            highlight-current-row
            :current-row-key="selectedTable?.index"
            row-key="index"
            @row-click="row => selectTable(row.index)"
          >
            <el-table-column label="左侧表" min-width="150" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.leftTable || '-' }}</template>
            </el-table-column>
            <el-table-column label="右侧表" min-width="150" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.rightTable || '-' }}</template>
            </el-table-column>
            <el-table-column label="左/右行数" width="120">
              <template #default="scope">{{ scope.row.leftRowCount ?? '-' }} / {{ scope.row.rightRowCount ?? '-' }}</template>
            </el-table-column>
            <el-table-column v-if="activeResult.compareDetail !== false" label="一致" width="80">
              <template #default="scope">{{ scope.row.sameRowCount ?? '-' }}</template>
            </el-table-column>
            <el-table-column :label="activeResult.compareDetail === false ? '记录数差异' : '差异'" width="100">
              <template #default="scope">
                <el-tag :type="(scope.row.diffRowCount || 0) > 0 ? 'danger' : 'success'">{{ scope.row.diffRowCount ?? '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="activeResult.compareDetail !== false" label="仅左/仅右" width="100">
              <template #default="scope">{{ scope.row.onlyLeftCount ?? '-' }} / {{ scope.row.onlyRightCount ?? '-' }}</template>
            </el-table-column>
            <el-table-column v-if="activeResult.compareDetail !== false" label="值差异" width="80">
              <template #default="scope">{{ scope.row.valueDiffCount ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" :width="activeResult.compareDetail === false ? 100 : 170" fixed="right">
              <template #default="scope">
                <template v-if="activeResult.compareDetail !== false">
                  <el-button link type="primary" @click.stop="selectTable(scope.row.index)">查看明细</el-button>
                  <el-button link type="primary" @click.stop="downloadResult(activeResult, scope.row)">下载差异</el-button>
                </template>
                <el-button link type="primary" @click.stop="downloadSummary(activeResult, scope.row)">下载汇总</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="activeResult.compareDetail !== false" class="detail-section">
          <div class="detail-section-title">
            <span>
              差异明细（前 100 行）
              <span v-if="selectedTable" class="detail-table-name">
                {{ selectedTable.leftTable || '-' }} → {{ selectedTable.rightTable || '-' }}
              </span>
            </span>
            <span v-if="detailTruncated" class="text-muted">仅展示前 100 行，完整结果请下载文件</span>
          </div>
          <div v-if="detailLoading" class="detail-loading">正在加载差异明细...</div>
          <el-table v-else-if="detailColumns.length" :data="detailRows" style="width: 100%" max-height="420">
            <el-table-column
              v-for="column in detailColumns"
              :key="column"
              :prop="column"
              :label="detailColumnLabel(column)"
              min-width="150"
              show-overflow-tooltip
            >
              <template #default="scope">
                {{ detailCellValue(column, scope.row[column]) }}
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="detail-empty">{{ detailHint }}</div>
        </div>
        <div v-else class="detail-section">
          <div class="detail-empty">本次仅比对记录数，未生成差异明细</div>
        </div>

        <div v-if="historyVisible" class="history-section">
          <div class="detail-section-title">
            <span>历史任务实例</span>
            <span class="text-muted">选择要查看的历史执行实例</span>
          </div>
          <el-table :data="results" style="width: 100%" @row-click="selectHistoryResult" highlight-current-row>
            <el-table-column label="执行实例 ID" min-width="290">
              <template #default="scope">
                <span class="instance-id">{{ scope.row.jobInstanceId }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="scope">
                <span :class="'status-badge status-' + ((scope.row.status || '').toLowerCase() || '')">
                  {{ statusText(scope.row.status) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="左/右行数" width="130">
              <template #default="scope">{{ scope.row.leftRowCount ?? '-' }} / {{ scope.row.rightRowCount ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="差异" width="90">
              <template #default="scope">
                <el-tag :type="(scope.row.diffRowCount || 0) > 0 ? 'danger' : 'success'">{{ scope.row.diffRowCount ?? '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="开始时间" width="170">
              <template #default="scope">{{ formatDate(scope.row.startTime || scope.row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="scope">
                <el-button link type="primary" @click.stop="selectHistoryResult(scope.row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="logDialogVisible" title="任务日志" width="80%" destroy-on-close>
      <pre class="log-content">{{ logContent || '暂无日志' }}</pre>
      <template #footer>
        <el-button @click="refreshLog">刷新</el-button>
        <el-button type="primary" @click="logDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" src="./data-compare.component.ts"></script>

<style scoped>
.compare-config {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 13px;
}
.config-label {
  display: inline-block;
  width: 20px;
  color: #909399;
}
.config-keys {
  color: #909399;
}
.result-summary {
  margin-bottom: 16px;
}
.result-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.result-heading {
  display: flex;
  align-items: center;
  gap: 8px;
}
.result-title {
  font-size: 15px;
  font-weight: 600;
}
.result-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.result-meta {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
  padding: 10px 14px;
  margin-bottom: 12px;
  color: #606266;
  font-size: 13px;
  background: #f7f8fa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}
.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.meta-label {
  color: #909399;
}
.compare-summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(118px, 1fr));
  gap: 10px;
  margin-bottom: 4px;
}
.summary-card {
  padding: 14px 16px;
  background: #f7f8fa;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease;
}
.summary-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgb(0 0 0 / 6%);
}
.summary-label {
  margin-bottom: 6px;
  color: #909399;
  font-size: 12px;
}
.summary-value {
  color: #303133;
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
}
.summary-card-success .summary-value {
  color: #00a870;
}
.summary-card-danger .summary-value {
  color: #d54941;
}
.summary-card-warning .summary-value {
  color: #d97706;
}
.table-result-section,
.detail-section,
.history-section {
  margin-top: 18px;
}
.detail-table-name {
  margin-left: 8px;
  color: #409eff;
  font-weight: 400;
}
.detail-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
}
.detail-loading,
.detail-empty {
  padding: 24px;
  text-align: center;
  color: #909399;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
}
.instance-id {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  word-break: break-all;
}
.log-content {
  max-height: 60vh;
  overflow: auto;
  background: #0b1020;
  color: #e5e7eb;
  padding: 12px;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
}
</style>
