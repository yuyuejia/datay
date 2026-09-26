<template>
  <div>
    <h2 id="page-heading" data-cy="DataSyncTableConfigHeading">
      <span id="data-sync-table-config-heading">Data Sync Table Configs</span>
      <div class="d-flex justify-content-end">
        <el-button type="info" class="mr-2" @click="handleSyncList" :disabled="isFetching">
          <font-awesome-icon icon="sync" :spin="isFetching"></font-awesome-icon> <span>Refresh list</span>
        </el-button>
        <router-link :to="{ name: 'DataSyncTableConfigCreate' }" custom v-slot="{ navigate }">
          <el-button type="primary" @click="navigate" id="jh-create-entity" data-cy="entityCreateButton" class="jh-create-entity create-data-sync-table-config">
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>创建新 Data Sync Table Config</span>
          </el-button>
        </router-link>
      </div>
    </h2>
    <br />
    <div class="alert alert-warning" v-if="!isFetching && dataSyncTableConfigs && dataSyncTableConfigs.length === 0">
      <span>No Data Sync Table Configs found</span>
    </div>
    <div class="table-responsive" v-if="dataSyncTableConfigs && dataSyncTableConfigs.length > 0">
      <table class="table table-striped" aria-describedby="dataSyncTableConfigs">
        <thead>
          <tr>
            <th scope="row" @click="changeOrder('id')">
              <span>ID</span> <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'id'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('syncTask')">
              <span>Sync Task</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'syncTask'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('srcDatasource')">
              <span>Src Datasource</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'srcDatasource'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('srcSchemaName')">
              <span>Src Schema Name</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'srcSchemaName'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('srcTableName')">
              <span>Src Table Name</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'srcTableName'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('srcColPks')">
              <span>Src Col Pks</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'srcColPks'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('desDatasource')">
              <span>Des Datasource</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'desDatasource'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('desSchemaName')">
              <span>Des Schema Name</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'desSchemaName'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('desTableName')">
              <span>Des Table Name</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'desTableName'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('desColPks')">
              <span>Des Col Pks</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'desColPks'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('jobDesc')">
              <span>Job Desc</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'jobDesc'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('columeConfig')">
              <span>Colume Config</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'columeConfig'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('updateTime')">
              <span>Update Time</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'updateTime'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('createTime')">
              <span>Create Time</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'createTime'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('project')">
              <span>Project</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'project'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('tenantId')">
              <span>Tenant Id</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'tenantId'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('dr')">
              <span>Dr</span> <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'dr'"></jhi-sort-indicator>
            </th>
            <th scope="row"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="dataSyncTableConfig in dataSyncTableConfigs" :key="dataSyncTableConfig.id" data-cy="entityTable">
            <td>
              <router-link :to="{ name: 'DataSyncTableConfigView', params: { dataSyncTableConfigId: dataSyncTableConfig.id } }">{{
                dataSyncTableConfig.id
              }}</router-link>
            </td>
            <td>{{ dataSyncTableConfig.syncTask }}</td>
            <td>{{ dataSyncTableConfig.srcDatasource }}</td>
            <td>{{ dataSyncTableConfig.srcSchemaName }}</td>
            <td>{{ dataSyncTableConfig.srcTableName }}</td>
            <td>{{ dataSyncTableConfig.srcColPks }}</td>
            <td>{{ dataSyncTableConfig.desDatasource }}</td>
            <td>{{ dataSyncTableConfig.desSchemaName }}</td>
            <td>{{ dataSyncTableConfig.desTableName }}</td>
            <td>{{ dataSyncTableConfig.desColPks }}</td>
            <td>{{ dataSyncTableConfig.jobDesc }}</td>
            <td>{{ dataSyncTableConfig.columeConfig }}</td>
            <td>{{ formatDateShort(dataSyncTableConfig.updateTime) || '' }}</td>
            <td>{{ formatDateShort(dataSyncTableConfig.createTime) || '' }}</td>
            <td>{{ dataSyncTableConfig.project }}</td>
            <td>{{ dataSyncTableConfig.tenantId }}</td>
            <td>{{ dataSyncTableConfig.dr }}</td>
            <td class="text-right">
              <div class="btn-group">
                <router-link
                  :to="{ name: 'DataSyncTableConfigView', params: { dataSyncTableConfigId: dataSyncTableConfig.id } }"
                  custom
                  v-slot="{ navigate }"
                >
                  <el-button type="info" size="small" @click="navigate" class="details" data-cy="entityDetailsButton">
                    <font-awesome-icon icon="eye"></font-awesome-icon>
                    <span class="d-none d-md-inline">查看</span>
                  </el-button>
                </router-link>
                <router-link
                  :to="{ name: 'DataSyncTableConfigEdit', params: { dataSyncTableConfigId: dataSyncTableConfig.id } }"
                  custom
                  v-slot="{ navigate }"
                >
                  <el-button type="primary" size="small" @click="navigate" class="edit" data-cy="entityEditButton">
                    <font-awesome-icon icon="pencil-alt"></font-awesome-icon>
                    <span class="d-none d-md-inline">编辑</span>
                  </el-button>
                </router-link>
                <el-button size="small" @click="prepareRemove(dataSyncTableConfig)" type="danger" data-cy="entityDeleteButton">
                  <font-awesome-icon icon="times"></font-awesome-icon>
                  <span class="d-none d-md-inline">删除</span>
                </el-button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <app-modal ref="removeEntity" id="removeEntity">
      <template #modal-title>
        <span id="datafusionApp.dataSyncTableConfig.delete.question" data-cy="dataSyncTableConfigDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p id="jhi-delete-dataSyncTableConfig-heading">你确定要删除 Data Sync Table Config {{ removeId }} 吗？</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeDialog()">取消</el-button>
          <el-button type="primary" id="jhi-confirm-delete-dataSyncTableConfig" data-cy="entityConfirmDeleteButton" @click="removeDataSyncTableConfig()">
            删除
          </el-button>
        </div>
      </template>
    </app-modal>
    <div v-show="dataSyncTableConfigs && dataSyncTableConfigs.length > 0">
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

<script lang="ts" src="./data-sync-table-config.component.ts"></script>
