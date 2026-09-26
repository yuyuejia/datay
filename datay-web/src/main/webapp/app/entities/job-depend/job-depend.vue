<template>
  <div>
    <h2 id="page-heading" data-cy="JobDependHeading">
      <span id="job-depend-heading">Job Depends</span>
      <div class="d-flex justify-content-end">
        <el-button type="info" class="mr-2" @click="handleSyncList" :disabled="isFetching">
          <font-awesome-icon icon="sync" :spin="isFetching"></font-awesome-icon> <span>Refresh list</span>
        </el-button>
        <router-link :to="{ name: 'JobDependCreate' }" custom v-slot="{ navigate }">
          <el-button type="primary" @click="navigate" id="jh-create-entity" data-cy="entityCreateButton" class="jh-create-entity create-job-depend">
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>创建新 Job Depend</span>
          </el-button>
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
                  <el-button type="info" size="small" @click="navigate" class="details" data-cy="entityDetailsButton">
                    <font-awesome-icon icon="eye"></font-awesome-icon>
                    <span class="d-none d-md-inline">查看</span>
                  </el-button>
                </router-link>
                <router-link :to="{ name: 'JobDependEdit', params: { jobDependId: jobDepend.id } }" custom v-slot="{ navigate }">
                  <el-button type="primary" size="small" @click="navigate" class="edit" data-cy="entityEditButton">
                    <font-awesome-icon icon="pencil-alt"></font-awesome-icon>
                    <span class="d-none d-md-inline">编辑</span>
                  </el-button>
                </router-link>
                <el-button size="small" @click="prepareRemove(jobDepend)" type="danger" data-cy="entityDeleteButton">
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
        <span id="datafusionApp.jobDepend.delete.question" data-cy="jobDependDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p id="jhi-delete-jobDepend-heading">你确定要删除 Job Depend {{ removeId }} 吗？</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeDialog()">取消</el-button>
          <el-button type="primary" id="jhi-confirm-delete-jobDepend" data-cy="entityConfirmDeleteButton" @click="removeJobDepend()">
            删除
          </el-button>
        </div>
      </template>
    </app-modal>
    <div v-show="jobDepends && jobDepends.length > 0">
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

<script lang="ts" src="./job-depend.component.ts"></script>
