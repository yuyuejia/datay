<template>
  <div>
    <h2 id="page-heading" data-cy="DpTableHeading">
      <span id="dp-table-heading">Dp Tables</span>
      <div class="d-flex justify-content-end">
        <el-button type="info" class="mr-2" @click="handleSyncList" :disabled="isFetching">
          <font-awesome-icon icon="sync" :spin="isFetching"></font-awesome-icon> <span>Refresh list</span>
        </el-button>
        <router-link :to="{ name: 'DpTableCreate' }" custom v-slot="{ navigate }">
          <el-button type="primary" @click="navigate" id="jh-create-entity" data-cy="entityCreateButton" class="jh-create-entity create-dp-table">
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>创建新 Dp Table</span>
          </el-button>
        </router-link>
      </div>
    </h2>
    <br />
    <div class="alert alert-warning" v-if="!isFetching && dpTables && dpTables.length === 0">
      <span>No Dp Tables found</span>
    </div>
    <div class="table-responsive" v-if="dpTables && dpTables.length > 0">
      <table class="table table-striped" aria-describedby="dpTables">
        <thead>
          <tr>
            <th scope="row" @click="changeOrder('id')">
              <span>ID</span> <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'id'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('name')">
              <span>Name</span> <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'name'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('schemaName')">
              <span>Schema Name</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'schemaName'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('description')">
              <span>Description</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'description'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('sourceDBType')">
              <span>Source DB Type</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'sourceDBType'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('sourceId')">
              <span>Source Id</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'sourceId'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('sourceSchema')">
              <span>Source Schema</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'sourceSchema'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('sourceTable')">
              <span>Source Table</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'sourceTable'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('sqlContent')">
              <span>Sql Content</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'sqlContent'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('fileType')">
              <span>File Type</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'fileType'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('filePath')">
              <span>File Path</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'filePath'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('updateTime')">
              <span>Update Time</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'updateTime'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('createTime')">
              <span>Create Time</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'createTime'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('tenantId')">
              <span>Tenant Id</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'tenantId'"></jhi-sort-indicator>
            </th>
            <th scope="row"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="dpTable in dpTables" :key="dpTable.id" data-cy="entityTable">
            <td>
              <router-link :to="{ name: 'DpTableView', params: { dpTableId: dpTable.id } }">{{ dpTable.id }}</router-link>
            </td>
            <td>{{ dpTable.name }}</td>
            <td>{{ dpTable.schemaName }}</td>
            <td>{{ dpTable.description }}</td>
            <td>{{ dpTable.sourceDBType }}</td>
            <td>{{ dpTable.sourceId }}</td>
            <td>{{ dpTable.sourceSchema }}</td>
            <td>{{ dpTable.sourceTable }}</td>
            <td>{{ dpTable.sqlContent }}</td>
            <td>{{ dpTable.fileType }}</td>
            <td>{{ dpTable.filePath }}</td>
            <td>{{ formatDateShort(dpTable.updateTime) || '' }}</td>
            <td>{{ formatDateShort(dpTable.createTime) || '' }}</td>
            <td>{{ dpTable.tenantId }}</td>
            <td class="text-right">
              <div class="btn-group">
                <router-link :to="{ name: 'DpTableView', params: { dpTableId: dpTable.id } }" custom v-slot="{ navigate }">
                  <el-button type="info" size="small" @click="navigate" class="details" data-cy="entityDetailsButton">
                    <font-awesome-icon icon="eye"></font-awesome-icon>
                    <span class="d-none d-md-inline">查看</span>
                  </el-button>
                </router-link>
                <router-link :to="{ name: 'DpTableEdit', params: { dpTableId: dpTable.id } }" custom v-slot="{ navigate }">
                  <el-button type="primary" size="small" @click="navigate" class="edit" data-cy="entityEditButton">
                    <font-awesome-icon icon="pencil-alt"></font-awesome-icon>
                    <span class="d-none d-md-inline">编辑</span>
                  </el-button>
                </router-link>
                <el-button size="small" @click="prepareRemove(dpTable)" type="danger" data-cy="entityDeleteButton">
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
        <span id="datafusionApp.dpTable.delete.question" data-cy="dpTableDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p id="jhi-delete-dpTable-heading">你确定要删除 Dp Table {{ removeId }} 吗？</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeDialog()">取消</el-button>
          <el-button type="primary" id="jhi-confirm-delete-dpTable" data-cy="entityConfirmDeleteButton" @click="removeDpTable()">
            删除
          </el-button>
        </div>
      </template>
    </app-modal>
    <div v-show="dpTables && dpTables.length > 0">
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

<script lang="ts" src="./dp-table.component.ts"></script>
