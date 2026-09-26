<template>
  <div>
    <h2 id="page-heading" data-cy="ETLComponentHeading">
      <span id="etl-component-heading">ETL Components</span>
      <div class="d-flex justify-content-end">
        <el-button type="info" class="mr-2" @click="handleSyncList" :disabled="isFetching">
          <font-awesome-icon icon="sync" :spin="isFetching"></font-awesome-icon> <span>Refresh list</span>
        </el-button>
        <router-link :to="{ name: 'ETLComponentCreate' }" custom v-slot="{ navigate }">
          <el-button type="primary" @click="navigate" id="jh-create-entity" data-cy="entityCreateButton" class="jh-create-entity create-etl-component">
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>创建新 ETL Component</span>
          </el-button>
        </router-link>
      </div>
    </h2>
    <br />
    <div class="alert alert-warning" v-if="!isFetching && eTLComponents && eTLComponents.length === 0">
      <span>No ETL Components found</span>
    </div>
    <div class="table-responsive" v-if="eTLComponents && eTLComponents.length > 0">
      <table class="table table-striped" aria-describedby="eTLComponents">
        <thead>
          <tr>
            <th scope="row" @click="changeOrder('id')">
              <span>ID</span> <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'id'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('name')">
              <span>Name</span> <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'name'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('code')">
              <span>Code</span> <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'code'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('desc')">
              <span>Desc</span> <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'desc'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('group')">
              <span>Group</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'group'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('type')">
              <span>Type</span> <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'type'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('config')">
              <span>Config</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'config'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('status')">
              <span>Status</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'status'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('updateTime')">
              <span>Update Time</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'updateTime'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('createTime')">
              <span>Create Time</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'createTime'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('creater')">
              <span>Creater</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'creater'"></jhi-sort-indicator>
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
          <tr v-for="eTLComponent in eTLComponents" :key="eTLComponent.id" data-cy="entityTable">
            <td>
              <router-link :to="{ name: 'ETLComponentView', params: { eTLComponentId: eTLComponent.id } }">{{
                eTLComponent.id
              }}</router-link>
            </td>
            <td>{{ eTLComponent.name }}</td>
            <td>{{ eTLComponent.code }}</td>
            <td>{{ eTLComponent.desc }}</td>
            <td>{{ eTLComponent.group }}</td>
            <td>{{ eTLComponent.type }}</td>
            <td>{{ eTLComponent.config }}</td>
            <td>{{ eTLComponent.status }}</td>
            <td>{{ formatDateShort(eTLComponent.updateTime) || '' }}</td>
            <td>{{ formatDateShort(eTLComponent.createTime) || '' }}</td>
            <td>{{ eTLComponent.creater }}</td>
            <td>{{ eTLComponent.tenantId }}</td>
            <td>{{ eTLComponent.dr }}</td>
            <td class="text-right">
              <div class="btn-group">
                <router-link :to="{ name: 'ETLComponentView', params: { eTLComponentId: eTLComponent.id } }" custom v-slot="{ navigate }">
                  <el-button type="info" size="small" @click="navigate" class="details" data-cy="entityDetailsButton">
                    <font-awesome-icon icon="eye"></font-awesome-icon>
                    <span class="d-none d-md-inline">查看</span>
                  </el-button>
                </router-link>
                <router-link :to="{ name: 'ETLComponentEdit', params: { eTLComponentId: eTLComponent.id } }" custom v-slot="{ navigate }">
                  <el-button type="primary" size="small" @click="navigate" class="edit" data-cy="entityEditButton">
                    <font-awesome-icon icon="pencil-alt"></font-awesome-icon>
                    <span class="d-none d-md-inline">编辑</span>
                  </el-button>
                </router-link>
                <el-button size="small" @click="prepareRemove(eTLComponent)" type="danger" data-cy="entityDeleteButton">
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
        <span id="datafusionApp.eTLComponent.delete.question" data-cy="eTLComponentDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p id="jhi-delete-eTLComponent-heading">你确定要删除 ETL Component {{ removeId }} 吗？</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeDialog()">取消</el-button>
          <el-button type="primary" id="jhi-confirm-delete-eTLComponent" data-cy="entityConfirmDeleteButton" @click="removeETLComponent()">
            删除
          </el-button>
        </div>
      </template>
    </app-modal>
    <div v-show="eTLComponents && eTLComponents.length > 0">
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

<script lang="ts" src="./etl-component.component.ts"></script>
