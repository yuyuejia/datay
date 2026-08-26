<template>
  <div>
    <h2 id="page-heading" data-cy="ETLEdgeHeading">
      <span id="etl-edge-heading">ETL Edges</span>
      <div class="d-flex justify-content-end">
        <button class="btn btn-info mr-2" @click="handleSyncList" :disabled="isFetching">
          <font-awesome-icon icon="sync" :spin="isFetching"></font-awesome-icon> <span>Refresh list</span>
        </button>
        <router-link :to="{ name: 'ETLEdgeCreate' }" custom v-slot="{ navigate }">
          <button
            @click="navigate"
            id="jh-create-entity"
            data-cy="entityCreateButton"
            class="btn btn-primary jh-create-entity create-etl-edge"
          >
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>创建新 ETL Edge</span>
          </button>
        </router-link>
      </div>
    </h2>
    <br />
    <div class="alert alert-warning" v-if="!isFetching && eTLEdges && eTLEdges.length === 0">
      <span>No ETL Edges found</span>
    </div>
    <div class="table-responsive" v-if="eTLEdges && eTLEdges.length > 0">
      <table class="table table-striped" aria-describedby="eTLEdges">
        <thead>
          <tr>
            <th scope="row" @click="changeOrder('id')">
              <span>ID</span> <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'id'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('taskId')">
              <span>Task Id</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'taskId'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('name')">
              <span>Name</span> <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'name'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('code')">
              <span>Code</span> <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'code'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('source')">
              <span>Source</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'source'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('target')">
              <span>Target</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'target'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('config')">
              <span>Config</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'config'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('status')">
              <span>Status</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'status'"></jhi-sort-indicator>
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
          <tr v-for="eTLEdge in eTLEdges" :key="eTLEdge.id" data-cy="entityTable">
            <td>
              <router-link :to="{ name: 'ETLEdgeView', params: { eTLEdgeId: eTLEdge.id } }">{{ eTLEdge.id }}</router-link>
            </td>
            <td>{{ eTLEdge.taskId }}</td>
            <td>{{ eTLEdge.name }}</td>
            <td>{{ eTLEdge.code }}</td>
            <td>{{ eTLEdge.source }}</td>
            <td>{{ eTLEdge.target }}</td>
            <td>{{ eTLEdge.config }}</td>
            <td>{{ eTLEdge.status }}</td>
            <td>{{ eTLEdge.tenantId }}</td>
            <td>{{ eTLEdge.dr }}</td>
            <td class="text-right">
              <div class="btn-group">
                <router-link :to="{ name: 'ETLEdgeView', params: { eTLEdgeId: eTLEdge.id } }" custom v-slot="{ navigate }">
                  <button @click="navigate" class="btn btn-info btn-sm details" data-cy="entityDetailsButton">
                    <font-awesome-icon icon="eye"></font-awesome-icon>
                    <span class="d-none d-md-inline">查看</span>
                  </button>
                </router-link>
                <router-link :to="{ name: 'ETLEdgeEdit', params: { eTLEdgeId: eTLEdge.id } }" custom v-slot="{ navigate }">
                  <button @click="navigate" class="btn btn-primary btn-sm edit" data-cy="entityEditButton">
                    <font-awesome-icon icon="pencil-alt"></font-awesome-icon>
                    <span class="d-none d-md-inline">编辑</span>
                  </button>
                </router-link>
                <b-button
                  @click="prepareRemove(eTLEdge)"
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
        <span id="datafusionApp.eTLEdge.delete.question" data-cy="eTLEdgeDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p id="jhi-delete-eTLEdge-heading">你确定要删除 ETL Edge {{ removeId }} 吗？</p>
      </div>
      <template #modal-footer>
        <div>
          <button type="button" class="btn btn-secondary" @click="closeDialog()">取消</button>
          <button
            type="button"
            class="btn btn-primary"
            id="jhi-confirm-delete-eTLEdge"
            data-cy="entityConfirmDeleteButton"
            @click="removeETLEdge()"
          >
            删除
          </button>
        </div>
      </template>
    </b-modal>
    <div v-show="eTLEdges && eTLEdges.length > 0">
      <div class="row justify-content-center">
        <jhi-item-count :page="page" :total="queryCount" :itemsPerPage="itemsPerPage"></jhi-item-count>
      </div>
      <div class="row justify-content-center">
        <b-pagination size="md" :total-rows="totalItems" v-model="page" :per-page="itemsPerPage"></b-pagination>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./etl-edge.component.ts"></script>
