<template>
  <div class="data-api-container">
    <h2 id="page-heading" data-cy="DataApiHeading" class="d-flex align-items-center justify-content-between flex-wrap-nowrap">
      <span id="data-api-heading">数据服务列表</span>
      <div class="d-flex align-items-center">
        <el-input class="mr-2" style="width: 280px" v-model="search" placeholder="按名称 / 编码 / 表 / 描述搜索" clearable />
        <router-link :to="createRouteLocation" custom v-slot="{ navigate }">
          <el-button
            type="primary"
            @click="navigate"
            id="jh-create-entity"
            data-cy="entityCreateButton"
            class="jh-create-entity create-data-api"
          >
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>新建数据服务</span>
          </el-button>
        </router-link>
      </div>
    </h2>
    <div class="data-api-layout">
      <div class="tree-panel">
        <div class="tree-header">
          <h5>API 服务目录</h5>
          <div class="tree-actions">
            <el-button type="primary" size="small" @click="showAddDirectoryDialog(null)">
              <font-awesome-icon icon="plus" /> 目录
            </el-button>
          </div>
        </div>
        <div class="tree-body" v-loading="directoryLoading">
          <div class="tree-shortcut" :class="{ active: !selectedDirectoryId }" @click="selectAllServices">
            <font-awesome-icon icon="list" class="tree-icon" />
            <span>全部服务</span>
          </div>
          <el-tree
            ref="directoryTreeRef"
            :data="directoryTree"
            :props="treeProps"
            node-key="id"
            default-expand-all
            highlight-current
            :expand-on-click-node="true"
            @node-click="handleDirectoryClick"
          >
            <template #default="{ node, data }">
              <span class="custom-tree-node">
                <span>
                  <font-awesome-icon icon="folder" class="tree-icon folder-icon" />
                  <span>{{ node.label }}</span>
                </span>
                <span class="tree-node-actions">
                  <el-button link type="primary" size="small" title="新增子目录" @click.stop="showAddDirectoryDialog(data.id)">
                    <font-awesome-icon icon="plus" />
                  </el-button>
                  <router-link :to="{ name: 'DataApiCreate', query: { directoryId: data.id } }" custom v-slot="{ navigate }">
                    <el-button link type="primary" size="small" title="在该目录下新建服务" @click.stop="navigate">
                      <font-awesome-icon icon="file-circle-plus" />
                    </el-button>
                  </router-link>
                  <el-button link type="primary" size="small" title="重命名" @click.stop="showEditDirectoryDialog(data.data)">
                    <font-awesome-icon icon="pen" />
                  </el-button>
                  <el-button
                    link
                    type="primary"
                    size="small"
                    title="上移"
                    :disabled="!canMoveDirectory(data.data, -1)"
                    @click.stop="moveDirectory(data.data, -1)"
                  >
                    <font-awesome-icon icon="arrow-up" />
                  </el-button>
                  <el-button
                    link
                    type="primary"
                    size="small"
                    title="下移"
                    :disabled="!canMoveDirectory(data.data, 1)"
                    @click.stop="moveDirectory(data.data, 1)"
                  >
                    <font-awesome-icon icon="arrow-down" />
                  </el-button>
                  <el-button link type="danger" size="small" title="删除目录" @click.stop="deleteDirectory(data.data)">
                    <font-awesome-icon icon="trash" />
                  </el-button>
                </span>
              </span>
            </template>
          </el-tree>
        </div>
      </div>
      <div class="list-panel">
        <div class="alert alert-warning" v-if="!isFetching && dataApis && dataApis.length === 0">
          <span>未找到数据服务</span>
        </div>
        <div v-if="dataApis && dataApis.length > 0" class="list-page-scroll">
          <el-table :data="dataApis" style="width: 100%" @sort-change="handleSortChange">
            <el-table-column prop="name" label="名称" sortable="custom" min-width="150"></el-table-column>
            <el-table-column prop="code" label="编码" sortable="custom" width="140"></el-table-column>
            <el-table-column label="来源" width="100" align="center">
              <template #default="scope">
                <el-tag v-if="scope.row.sourceType === 'SQL'" type="warning" size="small">自定义 SQL</el-tag>
                <el-tag v-else-if="scope.row.sourceType === 'API'" type="primary" size="small">已有 API</el-tag>
                <el-tag v-else-if="scope.row.sourceType === 'DATASET'" type="primary" size="small">分析数据集</el-tag>
                <el-tag v-else type="success" size="small">数据表</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="内容" sortable="custom" min-width="180">
              <template #default="scope">{{ tableDisplay(scope.row) }}</template>
            </el-table-column>
            <el-table-column label="数据源" width="140">
              <template #default="scope">
                {{ scope.row.sourceType === 'API' ? '-' : dataSourceNames[scope.row.dataSourceId] || scope.row.dataSourceId }}
              </template>
            </el-table-column>
            <el-table-column label="所属目录" min-width="180" show-overflow-tooltip>
              <template #default="scope">
                {{ directoryPath(scope.row) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="scope">
                <el-tag :type="scope.row.status === 'ENABLED' ? 'success' : 'info'" size="small">
                  {{ statusLabel(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" sortable="custom" width="150">
              <template #default="scope">{{ formatDateShort(scope.row.createTime) || '' }}</template>
            </el-table-column>
            <el-table-column label="操作" fixed="right" width="330">
              <template #default="scope">
                <div class="btn-group">
                  <router-link :to="{ name: 'DataApiEdit', params: { dataApiId: scope.row.id } }" custom v-slot="{ navigate }">
                    <el-button type="primary" size="small" @click="navigate" class="edit" data-cy="entityEditButton">
                      <span class="d-none d-md-inline">编辑</span>
                    </el-button>
                  </router-link>
                  <el-button type="info" size="small" @click="openDoc(scope.row)" data-cy="entityDocButton">
                    <span class="d-none d-md-inline">调用说明</span>
                  </el-button>
                  <router-link :to="{ name: 'DataApiStats', params: { dataApiId: scope.row.id } }" custom v-slot="{ navigate }">
                    <el-button type="success" size="small" @click="navigate" data-cy="entityStatsButton">
                      <span class="d-none d-md-inline">统计</span>
                    </el-button>
                  </router-link>
                  <el-dropdown trigger="click" @command="command => handleRowCommand(command, scope.row)">
                    <el-button type="info" plain size="small" data-cy="entityMoreButton">
                      <span class="d-none d-md-inline">更多</span>
                      <font-awesome-icon icon="ellipsis-vertical"></font-awesome-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="move" data-cy="entityMoveDirectoryButton">移动到目录</el-dropdown-item>
                        <el-dropdown-item command="delete" divided data-cy="entityDeleteButton">删除</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div v-show="dataApis && dataApis.length > 0">
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
    </div>

    <app-modal ref="removeEntity" id="removeEntity">
      <template #modal-title>
        <span data-cy="dataApiDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p>确定要删除数据服务 {{ removeId }} 吗？删除后第三方将无法继续调用该接口。</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeDialog()">取消</el-button>
          <el-button type="primary" data-cy="entityConfirmDeleteButton" @click="removeDataApi()"> 删除 </el-button>
        </div>
      </template>
    </app-modal>

    <app-modal v-model="docModalVisible" title="调用说明" size="lg" ok-only ok-title="关闭">
      <div class="modal-body" v-if="docApi">
        <h5>{{ docApi.name }}（{{ docApi.code }}）</h5>
        <p class="text-muted">{{ docApi.description }}</p>
        <table class="table table-bordered table-sm">
          <tbody>
            <tr>
              <th style="width: 110px">请求方式</th>
              <td>{{ docMethod }}</td>
            </tr>
            <tr>
              <th>请求地址</th>
              <td>
                <code>{{ docUrl }}</code>
              </td>
            </tr>
            <tr>
              <th>鉴权方式</th>
              <td>
                复用账号的 MCP Token，两种方式任选其一：<br />
                ① Header：<code>Authorization: Bearer mcp_xxx</code><br />
                ② Query：<code>?access_token=mcp_xxx</code>
              </td>
            </tr>
            <tr v-if="docApi.sourceType === 'TABLE'">
              <th>过滤与分页</th>
              <td>
                Query 中与表字段同名的参数会按等值过滤；<code>pageNum</code>（默认 1）、<code>pageSize</code>（默认 20，最大
                1000）用于分页。
              </td>
            </tr>
            <tr v-else-if="docApi.sourceType === 'SQL'">
              <th>SQL 参数</th>
              <td>SQL 中形如 <code>$&#123;param&#125;</code> 的占位符需在调用时以同名参数传入，服务仅支持只读查询。</td>
            </tr>
            <tr v-else-if="docApi.sourceType === 'DATASET'">
              <th>数据集参数</th>
              <td>
                <span v-if="docApi.datasetType === 'SQL'">
                  SQL 数据集的 <code>$&#123;param&#125;</code> 参数需在调用时全部传入；<code>pageNum</code>（默认
                  1）、<code>pageSize</code>（默认 1000，最大 10000）用于分页。
                </span>
                <span v-else>
                  指标数据集按保存的指标、维度与时间范围执行；可传 <code>start</code> / <code>end</code> 覆盖时间范围，<code>pageNum</code>
                  / <code>pageSize</code> 用于分页。
                </span>
              </td>
            </tr>
            <tr v-else>
              <th>代理说明</th>
              <td>
                该服务代理注册的已有 API，调用时会转发到目标接口；可在调用参数中传入主请求配置里
                <code>$&#123;param&#125;</code> 占位符对应的值，服务端会按需先获取 Token 再完成调用。
              </td>
            </tr>
          </tbody>
        </table>
        <h6 class="mt-3">curl 示例</h6>
        <pre class="bg-light border rounded p-3 mb-0" style="white-space: pre-wrap; word-break: break-all">{{ docCurl }}</pre>
      </div>
    </app-modal>

    <app-modal v-model="directoryDialogVisible" :title="directoryDialogTitle" size="md">
      <div class="modal-body">
        <el-form name="directoryForm" label-position="top" @submit.prevent="saveDirectory">
          <el-form-item label="目录名称">
            <el-input v-model="directoryForm.name" placeholder="请输入目录名称" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="directoryForm.sortOrder" :min="0" :max="9999" :controls="false" />
          </el-form-item>
        </el-form>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="directoryDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveDirectory">确定</el-button>
        </div>
      </template>
    </app-modal>

    <app-modal v-model="moveDialogVisible" title="移动到目录" size="md">
      <div class="modal-body">
        <p>
          数据服务：<strong>{{ moveDataApi?.name }}</strong>
        </p>
        <el-form label-position="top">
          <el-form-item label="目标目录">
            <el-tree-select
              v-model="moveTargetDirectoryId"
              :data="directoryTree"
              :props="treeProps"
              node-key="id"
              check-strictly
              clearable
              placeholder="不选择表示未分类"
              style="width: 100%"
            />
          </el-form-item>
        </el-form>
        <p class="text-muted mb-0">清空选择后，数据服务将移动到「未分类」。</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeMoveDialog">取消</el-button>
          <el-button type="primary" @click="confirmMove">确定</el-button>
        </div>
      </template>
    </app-modal>
  </div>
</template>

<script lang="ts" src="./data-api.component.ts"></script>

<style scoped>
.data-api-container {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
}

.data-api-layout {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
}

.tree-panel {
  width: 280px;
  min-width: 240px;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.tree-header {
  padding: 10px 12px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tree-header h5 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.tree-actions {
  display: flex;
  gap: 8px;
}

.tree-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.tree-shortcut {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  margin-bottom: 4px;
  border-radius: 4px;
  font-size: 14px;
  color: #303133;
  cursor: pointer;
}

.tree-shortcut:hover {
  background: #f5f7fa;
}

.tree-shortcut.active {
  background: #ecf5ff;
  color: #409eff;
}

.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  padding-right: 8px;
}

.tree-icon {
  margin-right: 6px;
}

.folder-icon {
  color: #e6a23c;
}

.tree-node-actions {
  display: none;
  gap: 0;
  align-items: center;
}

.tree-node-actions :deep(.el-button) {
  height: 20px;
  margin-left: 0;
  padding: 0 2px;
}

.tree-node-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.tree-node-actions :deep(a) {
  display: inline-flex;
  margin: 0;
}

.custom-tree-node:hover .tree-node-actions {
  display: inline-flex;
}

.list-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding-left: 12px;
}
</style>
