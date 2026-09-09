<template>
  <div>
    <h2 id="page-heading" data-cy="DataApiHeading" class="d-flex align-items-center justify-content-between flex-wrap-nowrap">
      <span id="data-api-heading">数据服务列表</span>
      <div class="d-flex align-items-center">
        <input
          type="text"
          class="form-control mr-2"
          style="width: 280px"
          v-model="search"
          placeholder="按名称 / 编码 / 表 / 描述搜索"
        />
        <router-link :to="{ name: 'DataApiCreate' }" custom v-slot="{ navigate }">
          <button
            @click="navigate"
            id="jh-create-entity"
            data-cy="entityCreateButton"
            class="btn btn-primary jh-create-entity create-data-api"
          >
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>新建数据服务</span>
          </button>
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
            <el-tag v-else type="success" size="small">数据表</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="内容" sortable="custom" min-width="180">
          <template #default="scope">{{ tableDisplay(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="数据源" width="140">
          <template #default="scope">{{ dataSourceNames[scope.row.dataSourceId] || scope.row.dataSourceId }}</template>
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
                <el-button @click="navigate" class="btn btn-primary btn-sm edit" data-cy="entityEditButton">
                  <span class="d-none d-md-inline">编辑</span>
                </el-button>
              </router-link>
              <el-button @click="openDoc(scope.row)" class="btn btn-info btn-sm" data-cy="entityDocButton">
                <span class="d-none d-md-inline">调用说明</span>
              </el-button>
              <el-button
                @click="prepareRemove(scope.row)"
                variant="danger"
                class="btn btn-danger btn-sm"
                data-cy="entityDeleteButton"
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
        <span data-cy="dataApiDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p>确定要删除数据服务 {{ removeId }} 吗？删除后第三方将无法继续调用该接口。</p>
      </div>
      <template #modal-footer>
        <div>
          <button type="button" class="btn btn-secondary" @click="closeDialog()">取消</button>
          <button type="button" class="btn btn-primary" data-cy="entityConfirmDeleteButton" @click="removeDataApi()">
            删除
          </button>
        </div>
      </template>
    </b-modal>

    <b-modal v-model="docModalVisible" title="调用说明" size="lg" ok-only ok-title="关闭">
      <div class="modal-body" v-if="docApi">
        <h5>{{ docApi.name }}（{{ docApi.code }}）</h5>
        <p class="text-muted">{{ docApi.description }}</p>
        <table class="table table-bordered table-sm">
          <tbody>
            <tr>
              <th style="width: 110px">请求方式</th>
              <td>GET / POST</td>
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
            <tr v-else>
              <th>SQL 参数</th>
              <td>SQL 中形如 <code>$&#123;param&#125;</code> 的占位符需在调用时以同名参数传入，服务仅支持只读查询。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </b-modal>

    <div v-show="dataApis && dataApis.length > 0">
      <div class="row justify-content-center">
        <jhi-item-count :page="page" :total="queryCount" :itemsPerPage="itemsPerPage"></jhi-item-count>
      </div>
      <div class="row justify-content-center">
        <b-pagination size="md" :total-rows="totalItems" v-model="page" :per-page="itemsPerPage"></b-pagination>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./data-api.component.ts"></script>
