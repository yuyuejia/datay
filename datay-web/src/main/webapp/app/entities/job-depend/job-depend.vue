<template>
  <div>
    <h2 id="page-heading" data-cy="JobDependHeading">
      <span id="job-depend-heading">Job Depends</span>
      <div class="d-flex justify-content-end">
        <button class="btn btn-info mr-2" @click="handleSyncList" :disabled="isFetching">
          <font-awesome-icon icon="sync" :spin="isFetching"></font-awesome-icon> <span>Refresh list</span>
        </button>
        <router-link :to="{ name: 'JobDependCreate' }" custom v-slot="{ navigate }">
          <button
            @click="navigate"
            id="jh-create-entity"
            data-cy="entityCreateButton"
            class="btn btn-primary jh-create-entity create-job-depend"
          >
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>创建新 Job Depend</span>
          </button>
        </router-link>
      </div>
    </h2>
    <br />
    <div class="alert alert-warning" v-if="!isFetching && jobDepends && jobDepends.length === 0">
      <span>No Job Depends found</span>
    </div>
    <div class="table-responsive" v-if="jobDepends && jobDepends.length > 0">
      <table class="table table-striped" aria-describedby="jobDepends">
        <thead>
          <tr>
            <th scope="row" @click="changeOrder('id')">
              <span>ID</span> <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'id'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('parentJobCode')">
              <span>Parent Job Code</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'parentJobCode'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('childJobCode')">
              <span>Child Job Code</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'childJobCode'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('jobCode')">
              <span>Job Code</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'jobCode'"></jhi-sort-indicator>
            </th>
            <th scope="row" @click="changeOrder('lastInterval')">
              <span>Last Interval</span>
              <jhi-sort-indicator :current-order="propOrder" :reverse="reverse" :field-name="'lastInterval'"></jhi-sort-indicator>
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
          <tr v-for="jobDepend in jobDepends" :key="jobDepend.id" data-cy="entityTable">
            <td>
              <router-link :to="{ name: 'JobDependView', params: { jobDependId: jobDepend.id } }">{{ jobDepend.id }}</router-link>
            </td>
            <td>{{ jobDepend.parentJobCode }}</td>
            <td>{{ jobDepend.childJobCode }}</td>
            <td>{{ jobDepend.jobCode }}</td>
            <td>{{ jobDepend.lastInterval }}</td>
            <td>{{ formatDateShort(jobDepend.createTime) || '' }}</td>
            <td>{{ jobDepend.tenantId }}</td>
            <td class="text-right">
              <div class="btn-group">
                <router-link :to="{ name: 'JobDependView', params: { jobDependId: jobDepend.id } }" custom v-slot="{ navigate }">
                  <button @click="navigate" class="btn btn-info btn-sm details" data-cy="entityDetailsButton">
                    <font-awesome-icon icon="eye"></font-awesome-icon>
                    <span class="d-none d-md-inline">查看</span>
                  </button>
                </router-link>
                <router-link :to="{ name: 'JobDependEdit', params: { jobDependId: jobDepend.id } }" custom v-slot="{ navigate }">
                  <button @click="navigate" class="btn btn-primary btn-sm edit" data-cy="entityEditButton">
                    <font-awesome-icon icon="pencil-alt"></font-awesome-icon>
                    <span class="d-none d-md-inline">编辑</span>
                  </button>
                </router-link>
                <b-button
                  @click="prepareRemove(jobDepend)"
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
        <span id="datafusionApp.jobDepend.delete.question" data-cy="jobDependDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p id="jhi-delete-jobDepend-heading">你确定要删除 Job Depend {{ removeId }} 吗？</p>
      </div>
      <template #modal-footer>
        <div>
          <button type="button" class="btn btn-secondary" @click="closeDialog()">取消</button>
          <button
            type="button"
            class="btn btn-primary"
            id="jhi-confirm-delete-jobDepend"
            data-cy="entityConfirmDeleteButton"
            @click="removeJobDepend()"
          >
            删除
          </button>
        </div>
      </template>
    </b-modal>
    <div v-show="jobDepends && jobDepends.length > 0">
      <div class="row justify-content-center">
        <jhi-item-count :page="page" :total="queryCount" :itemsPerPage="itemsPerPage"></jhi-item-count>
      </div>
      <div class="row justify-content-center">
        <b-pagination size="md" :total-rows="totalItems" v-model="page" :per-page="itemsPerPage"></b-pagination>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./job-depend.component.ts"></script>
