<template>
  <div>
    <h2 id="page-heading" data-cy="ETLNodeHeading">
      <span id="etl-node-heading">ETL Nodes</span>
      <div class="d-flex justify-content-end">
        <button class="btn btn-info mr-2" @click="handleSyncList" :disabled="isFetching">
          <font-awesome-icon icon="sync" :spin="isFetching"></font-awesome-icon> <span>Refresh list</span>
        </button>
        <router-link :to="{ name: 'ETLNodeCreate' }" custom v-slot="{ navigate }">
          <button
            @click="navigate"
            id="jh-create-entity"
            data-cy="entityCreateButton"
            class="btn btn-primary jh-create-entity create-etl-node"
          >
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>创建新 ETL Node</span>
          </button>
        </router-link>
      </div>
    </h2>
    <br />
    <div class="alert alert-warning" v-if="!isFetching && eTLNodes && eTLNodes.length === 0">
      <span>No ETL Nodes found</span>
    </div>
    <div class="table-responsive" v-if="eTLNodes && eTLNodes.length > 0">
      <table class="table table-striped" aria-describedby="eTLNodes">
        <thead>
          <tr>
            <th scope="row" @click="changeOrder('id')">
              <span>ID</span> <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'id'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('taskId')">
              <span>Task Id</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'taskId'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('label')">
              <span>Label</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'label'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('code')">
              <span>Code</span> <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'code'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('desc')">
              <span>Desc</span> <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'desc'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('type')">
              <span>Type</span> <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'type'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('config')">
              <span>Config</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'config'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('xAxis')">
              <span>X Axis</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'xAxis'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('yAxis')">
              <span>Y Axis</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'yAxis'"></jhi-sort-indicator>
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
          <tr v-for="eTLNode in eTLNodes" :key="eTLNode.id" data-cy="entityTable">
            <td>
              <router-link :to="{ name: 'ETLNodeView', params: { eTLNodeId: eTLNode.id } }">{{ eTLNode.id }}</router-link>
            </td>
            <td>{{ eTLNode.taskId }}</td>
            <td>{{ eTLNode.label }}</td>
            <td>{{ eTLNode.code }}</td>
            <td>{{ eTLNode.desc }}</td>
            <td>{{ eTLNode.type }}</td>
            <td>{{ eTLNode.config }}</td>
            <td>{{ eTLNode.xAxis }}</td>
            <td>{{ eTLNode.yAxis }}</td>
            <td>{{ eTLNode.status }}</td>
            <td>{{ formatDateShort(eTLNode.updateTime) || '' }}</td>
            <td>{{ formatDateShort(eTLNode.createTime) || '' }}</td>
            <td>{{ eTLNode.tenantId }}</td>
            <td>{{ eTLNode.dr }}</td>
            <td class="text-right">
              <div class="btn-group">
                <router-link :to="{ name: 'ETLNodeView', params: { eTLNodeId: eTLNode.id } }" custom v-slot="{ navigate }">
                  <button @click="navigate" class="btn btn-info btn-sm details" data-cy="entityDetailsButton">
                    <font-awesome-icon icon="eye"></font-awesome-icon>
                    <span class="d-none d-md-inline">查看</span>
                  </button>
                </router-link>
                <router-link :to="{ name: 'ETLNodeEdit', params: { eTLNodeId: eTLNode.id } }" custom v-slot="{ navigate }">
                  <button @click="navigate" class="btn btn-primary btn-sm edit" data-cy="entityEditButton">
                    <font-awesome-icon icon="pencil-alt"></font-awesome-icon>
                    <span class="d-none d-md-inline">编辑</span>
                  </button>
                </router-link>
                <b-button
                  @click="prepareRemove(eTLNode)"
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
        <span id="datafusionApp.eTLNode.delete.question" data-cy="eTLNodeDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p id="jhi-delete-eTLNode-heading">你确定要删除 ETL Node {{ removeId }} 吗？</p>
      </div>
      <template #modal-footer>
        <div>
          <button type="button" class="btn btn-secondary" @click="closeDialog()">取消</button>
          <button
            type="button"
            class="btn btn-primary"
            id="jhi-confirm-delete-eTLNode"
            data-cy="entityConfirmDeleteButton"
            @click="removeETLNode()"
          >
            删除
          </button>
        </div>
      </template>
    </b-modal>
    <div v-show="eTLNodes && eTLNodes.length > 0">
      <div class="row justify-content-center">
        <jhi-item-count :page="page" :total="queryCount" :itemsPerPage="itemsPerPage"></jhi-item-count>
      </div>
      <div class="row justify-content-center">
        <b-pagination size="md" :total-rows="totalItems" v-model="page" :per-page="itemsPerPage"></b-pagination>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./etl-node.component.ts"></script>
