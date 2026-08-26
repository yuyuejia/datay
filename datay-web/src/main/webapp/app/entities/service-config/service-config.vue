<template>
  <div>
    <h2 id="page-heading" data-cy="ServiceConfigHeading">
      <span id="service-config-heading">Service Configs</span>
      <div class="d-flex justify-content-end">
        <button class="btn btn-info mr-2" @click="handleSyncList" :disabled="isFetching">
          <font-awesome-icon icon="sync" :spin="isFetching"></font-awesome-icon> <span>Refresh list</span>
        </button>
        <router-link :to="{ name: 'ServiceConfigCreate' }" custom v-slot="{ navigate }">
          <button
            @click="navigate"
            id="jh-create-entity"
            data-cy="entityCreateButton"
            class="btn btn-primary jh-create-entity create-service-config"
          >
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>创建新 Service Config</span>
          </button>
        </router-link>
      </div>
    </h2>
    <br />
    <div class="alert alert-warning" v-if="!isFetching && serviceConfigs && serviceConfigs.length === 0">
      <span>No Service Configs found</span>
    </div>
    <div class="table-responsive" v-if="serviceConfigs && serviceConfigs.length > 0">
      <table class="table table-striped" aria-describedby="serviceConfigs">
        <thead>
          <tr>
            <th scope="row" @click="changeOrder('id')">
              <span>ID</span> <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'id'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('dfGroup')">
              <span>Df Group</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'dfGroup'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('dfKey')">
              <span>Df Key</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'dfKey'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('dfValue')">
              <span>Df Value</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'dfValue'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('createTime')">
              <span>Create Time</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'createTime'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('ytenantId')">
              <span>Ytenant Id</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'ytenantId'"></jhi-sort-indicator>
            </th>
            <th scope="row"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="serviceConfig in serviceConfigs" :key="serviceConfig.id" data-cy="entityTable">
            <td>
              <router-link :to="{ name: 'ServiceConfigView', params: { serviceConfigId: serviceConfig.id } }">{{
                serviceConfig.id
              }}</router-link>
            </td>
            <td>{{ serviceConfig.dfGroup }}</td>
            <td>{{ serviceConfig.dfKey }}</td>
            <td>{{ serviceConfig.dfValue }}</td>
            <td>{{ formatDateShort(serviceConfig.createTime) || '' }}</td>
            <td>{{ serviceConfig.ytenantId }}</td>
            <td class="text-right">
              <div class="btn-group">
                <router-link
                  :to="{ name: 'ServiceConfigView', params: { serviceConfigId: serviceConfig.id } }"
                  custom
                  v-slot="{ navigate }"
                >
                  <button @click="navigate" class="btn btn-info btn-sm details" data-cy="entityDetailsButton">
                    <font-awesome-icon icon="eye"></font-awesome-icon>
                    <span class="d-none d-md-inline">查看</span>
                  </button>
                </router-link>
                <router-link
                  :to="{ name: 'ServiceConfigEdit', params: { serviceConfigId: serviceConfig.id } }"
                  custom
                  v-slot="{ navigate }"
                >
                  <button @click="navigate" class="btn btn-primary btn-sm edit" data-cy="entityEditButton">
                    <font-awesome-icon icon="pencil-alt"></font-awesome-icon>
                    <span class="d-none d-md-inline">编辑</span>
                  </button>
                </router-link>
                <b-button
                  @click="prepareRemove(serviceConfig)"
                  variant="danger"
                  class="btn btn-sm"
                  data-cy="entityDeleteButton"
                  v-b-modal.removeEntity
                >
                  <font-awesome-icon icon="times"></font-awesome-icon>
                  <span class="d-none d-md-inline">删除</span>
                </b-button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <b-modal ref="removeEntity" id="removeEntity">
      <template #modal-title>
        <span id="datafusionApp.serviceConfig.delete.question" data-cy="serviceConfigDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p id="jhi-delete-serviceConfig-heading">你确定要删除 Service Config {{ removeId }} 吗？</p>
      </div>
      <template #modal-footer>
        <div>
          <button type="button" class="btn btn-secondary" @click="closeDialog()">取消</button>
          <button
            type="button"
            class="btn btn-primary"
            id="jhi-confirm-delete-serviceConfig"
            data-cy="entityConfirmDeleteButton"
            @click="removeServiceConfig()"
          >
            删除
          </button>
        </div>
      </template>
    </b-modal>
    <div v-show="serviceConfigs && serviceConfigs.length > 0">
      <div class="row justify-content-center">
        <jhi-item-count :page="page" :total="queryCount" :itemsPerPage="itemsPerPage"></jhi-item-count>
      </div>
      <div class="row justify-content-center">
        <b-pagination size="md" :total-rows="totalItems" v-model="page" :per-page="itemsPerPage"></b-pagination>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./service-config.component.ts"></script>
