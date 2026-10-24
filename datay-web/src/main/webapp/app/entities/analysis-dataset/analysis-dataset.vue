<template>
  <div>
    <h2 id="page-heading" data-cy="AnalysisDatasetHeading" class="d-flex align-items-center justify-content-between flex-wrap-nowrap">
      <span id="analysis-dataset-heading">分析数据集</span>
      <div class="d-flex align-items-center">
        <el-input v-model="search" class="mr-2" style="width: 260px" placeholder="按名称 / 编码 / 描述搜索" clearable />
        <el-button type="primary" @click="create()">
          <font-awesome-icon icon="plus" class="mr-1"></font-awesome-icon>
          新建数据集
        </el-button>
      </div>
    </h2>
    <div class="alert alert-warning" v-if="!isFetching && datasets.length === 0">
      <span>暂无数据集。数据集可被多个看板复用，修改后所有引用看板自动生效。</span>
    </div>
    <div v-if="datasets.length > 0" class="list-page-scroll">
      <el-table :data="datasets" style="width: 100%" v-loading="isFetching">
        <el-table-column prop="name" label="名称" min-width="160">
          <template #default="scope">
            <div class="dataset-name-cell">
              <span>{{ scope.row.name }}</span>
              <el-tag v-if="scope.row.status === 'DISABLED'" type="info" size="small">停用</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="编码" width="180">
          <template #default="scope">
            <a class="dataset-code" title="点击复制编码" @click="copyCode(scope.row)">{{ scope.row.code }}</a>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="scope">
            <el-tag :type="scope.row.type === 'SQL' ? 'warning' : 'primary'" size="small">
              {{ typeLabel(scope.row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column prop="updateTime" label="更新时间" width="170">
          <template #default="scope">{{ formatDateShort(scope.row.updateTime) || formatDateShort(scope.row.createTime) || '' }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="280">
          <template #default="scope">
            <div class="btn-group">
              <el-button type="primary" size="small" @click="openPreview(scope.row)">预览</el-button>
              <el-button type="info" size="small" @click="edit(scope.row)">编辑</el-button>
              <el-button type="danger" size="small" @click="prepareRemove(scope.row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <app-modal ref="removeEntity" id="removeEntity">
      <template #modal-title>
        <span>确认删除</span>
      </template>
      <div class="modal-body">
        <p>确定要删除数据集 {{ removeId }} 吗？被看板引用的数据集无法删除。</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeDialog()">取消</el-button>
          <el-button type="primary" @click="remove()">删除</el-button>
        </div>
      </template>
    </app-modal>

    <app-modal v-model="previewVisible" size="lg" :title="`数据预览 · ${previewDataset?.name || ''}`">
      <el-alert v-if="previewError" type="error" :title="previewError" :closable="false" class="mb-2" />
      <div v-loading="previewLoading" class="dataset-preview-table">
        <el-table v-if="previewColumns.length" :data="previewRows" border stripe height="420">
          <el-table-column
            v-for="column in previewColumns"
            :key="column"
            :prop="column"
            :label="previewColumnLabel(column)"
            min-width="130"
            show-overflow-tooltip
          />
        </el-table>
        <el-empty v-else-if="!previewLoading && !previewError" description="查询成功，无数据返回" />
      </div>
      <template #modal-footer>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </app-modal>
  </div>
</template>

<script lang="ts" src="./analysis-dataset.component.ts"></script>

<style scoped>
.dataset-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dataset-code {
  color: #409eff;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  cursor: pointer;
}

.dataset-preview-table {
  min-height: 180px;
}
</style>
