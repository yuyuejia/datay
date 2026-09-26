<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <div v-if="dataSyncTableConfig">
        <h2 class="jh-entity-heading" data-cy="dataSyncTableConfigDetailsHeading">
          <span>Data Sync Table Config</span> {{ dataSyncTableConfig.id }}
        </h2>
        <dl class="row jh-entity-details">
          <dt>
            <span>Sync Task</span>
          </dt>
          <dd>
            <span>{{ dataSyncTableConfig.syncTask }}</span>
          </dd>
          <dt>
            <span>Src Datasource</span>
          </dt>
          <dd>
            <span>{{ dataSyncTableConfig.srcDatasource }}</span>
          </dd>
          <dt>
            <span>Src Schema Name</span>
          </dt>
          <dd>
            <span>{{ dataSyncTableConfig.srcSchemaName }}</span>
          </dd>
          <dt>
            <span>Src Table Name</span>
          </dt>
          <dd>
            <span>{{ dataSyncTableConfig.srcTableName }}</span>
          </dd>
          <dt>
            <span>Src Col Pks</span>
          </dt>
          <dd>
            <span>{{ dataSyncTableConfig.srcColPks }}</span>
          </dd>
          <dt>
            <span>Des Datasource</span>
          </dt>
          <dd>
            <span>{{ dataSyncTableConfig.desDatasource }}</span>
          </dd>
          <dt>
            <span>Des Schema Name</span>
          </dt>
          <dd>
            <span>{{ dataSyncTableConfig.desSchemaName }}</span>
          </dd>
          <dt>
            <span>Des Table Name</span>
          </dt>
          <dd>
            <span>{{ dataSyncTableConfig.desTableName }}</span>
          </dd>
          <dt>
            <span>Des Col Pks</span>
          </dt>
          <dd>
            <span>{{ dataSyncTableConfig.desColPks }}</span>
          </dd>
          <dt>
            <span>Job Desc</span>
          </dt>
          <dd>
            <span>{{ dataSyncTableConfig.jobDesc }}</span>
          </dd>
          <dt>
            <span>Colume Config</span>
          </dt>
          <dd>
            <span>{{ dataSyncTableConfig.columeConfig }}</span>
          </dd>
          <dt>
            <span>Update Time</span>
          </dt>
          <dd>
            <span v-if="dataSyncTableConfig.updateTime">{{ formatDateLong(dataSyncTableConfig.updateTime) }}</span>
          </dd>
          <dt>
            <span>Create Time</span>
          </dt>
          <dd>
            <span v-if="dataSyncTableConfig.createTime">{{ formatDateLong(dataSyncTableConfig.createTime) }}</span>
          </dd>
          <dt>
            <span>Project</span>
          </dt>
          <dd>
            <span>{{ dataSyncTableConfig.project }}</span>
          </dd>
          <dt>
            <span>Tenant Id</span>
          </dt>
          <dd>
            <span>{{ dataSyncTableConfig.tenantId }}</span>
          </dd>
          <dt>
            <span>Dr</span>
          </dt>
          <dd>
            <span>{{ dataSyncTableConfig.dr }}</span>
          </dd>
        </dl>
        <el-button type="info" native-type="submit" @click.prevent="previousState()" data-cy="entityDetailsBackButton">
          <font-awesome-icon icon="arrow-left"></font-awesome-icon>&nbsp;<span>返回</span>
        </el-button>
        <router-link
          v-if="dataSyncTableConfig.id"
          :to="{ name: 'DataSyncTableConfigEdit', params: { dataSyncTableConfigId: dataSyncTableConfig.id } }"
          custom
          v-slot="{ navigate }"
        >
          <el-button type="primary" @click="navigate">
            <font-awesome-icon icon="pencil-alt"></font-awesome-icon>&nbsp;<span>编辑</span>
          </el-button>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./data-sync-table-config-details.component.ts"></script>
