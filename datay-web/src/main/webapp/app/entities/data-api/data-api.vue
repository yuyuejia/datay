<template>
  <div>
    <h2 id="page-heading" data-cy="DataApiHeading" class="d-flex align-items-center justify-content-between flex-wrap-nowrap">
      <span id="data-api-heading">数据服务列表</span>
      <div class="d-flex align-items-center">
        <el-input class="mr-2" style="width: 280px" v-model="search" placeholder="按名称 / 编码 / 表 / 描述搜索" clearable/>
        <router-link :to="{ name: 'DataApiCreate' }" custom v-slot="{ navigate }">
          <el-button type="primary" @click="navigate" id="jh-create-entity" data-cy="entityCreateButton" class="jh-create-entity create-data-api">
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>新建数据服务</span>
          </el-button>
        </router-link>
      </div>
    </h2>
    <br />
    <div class="alert alert-warning" v-if="!isFetching && dataApis && dataApis.length === 0">
      <span>未找到数据服务</span>
    </div>
    <div v-if="dataApis && dataApis.length > 0">
      <el-table :data="dataApis" style="width: 100%" @sort-change="handleSortChange">
        <el-table-column prop="name" label="名称" sortable="custom" min-width="150"></el-table-column>
        <el-table-column prop="code" label="编码" sortable="custom" width="140"></el-table-column>
        <el-table-column label="来源" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.sourceType === 'SQL'" type="warning" size="small">自定义 SQL</el-tag>
            <el-tag v-else-if="scope.row.sourceType === 'API'" type="primary" size="small">已有 API</el-tag>
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
        <el-table-column label="操作" fixed="right" width="200">
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
        <span data-cy="dataApiDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p>确定要删除数据服务 {{ removeId }} 吗？删除后第三方将无法继续调用该接口。</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeDialog()">取消</el-button>
          <el-button type="primary" data-cy="entityConfirmDeleteButton" @click="removeDataApi()">
            删除
          </el-button>
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
                Query 中与表字段同名的参数会按等值过滤；<code>pageNum</code>（默认 1）、<code>pageSize</code>（默认 20，最大 1000）用于分页。
              </td>
            </tr>
            <tr v-else-if="docApi.sourceType === 'SQL'">
              <th>SQL 参数</th>
              <td>SQL 中形如 <code>$&#123;param&#125;</code> 的占位符需在调用时以同名参数传入，服务仅支持只读查询。</td>
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
</template>

<script lang="ts" src="./data-api.component.ts"></script>
