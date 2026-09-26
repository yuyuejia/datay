<template>
  <div>
    <h2 id="page-heading" data-cy="ServiceConfigHeading" class="d-flex align-items-center justify-content-between flex-wrap-nowrap">
      <span id="service-config-heading">服务配置</span>
      <div class="d-flex align-items-center">
        <el-button type="info" class="mr-2" @click="handleSyncList" :disabled="isFetching">
          <font-awesome-icon icon="sync" :spin="isFetching"></font-awesome-icon>
          <span>刷新列表</span>
        </el-button>
        <el-button type="primary" @click="openCreateModal" id="jh-create-entity" data-cy="entityCreateButton" class="jh-create-entity create-service-config">
          <font-awesome-icon icon="plus"></font-awesome-icon>
          <span>创建新配置</span>
        </el-button>
      </div>
    </h2>
    <br />
    <div class="alert alert-warning" v-if="!isFetching && serviceConfigs && serviceConfigs.length === 0">
      <span>未找到服务配置</span>
    </div>
    <div v-if="serviceConfigs && serviceConfigs.length > 0">
      <el-table :data="serviceConfigs" style="width: 100%" @sort-change="handleSortChange">
        <el-table-column prop="id" label="ID" sortable="custom" width="90">
          <template #default="scope">
            <router-link :to="{ name: 'ServiceConfigView', params: { serviceConfigId: scope.row.id } }">{{ scope.row.id }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="dfGroup" label="配置分组" sortable="custom" width="180" show-overflow-tooltip></el-table-column>
        <el-table-column prop="dfKey" label="配置项" sortable="custom" width="220" show-overflow-tooltip></el-table-column>
        <el-table-column prop="dfValue" label="配置值" sortable="custom" min-width="260" show-overflow-tooltip></el-table-column>
        <el-table-column prop="tenantId" label="租户ID" sortable="custom" width="120"></el-table-column>
        <el-table-column prop="createTime" label="创建时间" sortable="custom" width="170">
          <template #default="scope">
            {{ formatDateShort(scope.row.createTime) || '' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="240">
          <template #default="scope">
            <div class="btn-group">
              <router-link
                :to="{ name: 'ServiceConfigView', params: { serviceConfigId: scope.row.id } }"
                custom
                v-slot="{ navigate }"
              >
                <el-button type="info" size="small" @click="navigate" class="details" data-cy="entityDetailsButton">
                  <span class="d-none d-md-inline">查看</span>
                </el-button>
              </router-link>
              <router-link :to="{ name: 'ServiceConfigEdit', params: { serviceConfigId: scope.row.id } }" custom v-slot="{ navigate }">
                <el-button type="primary" size="small" @click="navigate" class="edit" data-cy="entityEditButton">
                  <span class="d-none d-md-inline">编辑</span>
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
        <span id="datafusionApp.serviceConfig.delete.question" data-cy="serviceConfigDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p id="jhi-delete-serviceConfig-heading">你确定要删除服务配置 {{ removeId }} 吗？</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeDialog()">取消</el-button>
          <el-button type="primary" id="jhi-confirm-delete-serviceConfig" data-cy="entityConfirmDeleteButton" @click="removeServiceConfig()">
            删除
          </el-button>
        </div>
      </template>
    </app-modal>
    <app-modal ref="editEntity" id="editEntity">
      <template #modal-title>
        <span data-cy="serviceConfigCreateDialogHeading">创建新配置</span>
      </template>
      <div class="modal-body">
        <div class="form-group">
          <label class="form-control-label" for="service-config-dfGroup">配置分组</label>
          <el-input name="dfGroup" id="service-config-dfGroup" data-cy="dfGroup" v-model="editServiceConfig.dfGroup" placeholder="如：file-storage、data-warehouse" clearable/>
        </div>
        <div class="form-group">
          <label class="form-control-label" for="service-config-dfKey">配置项</label>
          <el-input name="dfKey" id="service-config-dfKey" data-cy="dfKey" v-model="editServiceConfig.dfKey" placeholder="请输入配置项" clearable/>
        </div>
        <div class="form-group">
          <label class="form-control-label" for="service-config-dfValue">配置值</label>
          <textarea
            class="form-control"
            name="dfValue"
            id="service-config-dfValue"
            data-cy="dfValue"
            rows="3"
            v-model="editServiceConfig.dfValue"
            placeholder="请输入配置值"
          ></textarea>
        </div>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeEditDialog()">取消</el-button>
          <el-button type="primary" id="jhi-confirm-create-serviceConfig" data-cy="entityConfirmCreateButton" :disabled="isSaving" @click="saveServiceConfig()">
            保存
          </el-button>
        </div>
      </template>
    </app-modal>
    <div v-show="serviceConfigs && serviceConfigs.length > 0">
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

<script lang="ts" src="./service-config.component.ts"></script>
