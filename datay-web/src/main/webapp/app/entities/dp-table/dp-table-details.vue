<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <div v-if="dpTable">
        <h2 class="jh-entity-heading" data-cy="dpTableDetailsHeading"><span>Dp Table</span> {{ dpTable.id }}</h2>
        <dl class="row jh-entity-details">
          <dt>
            <span>Name</span>
          </dt>
          <dd>
            <span>{{ dpTable.name }}</span>
          </dd>
          <dt>
            <span>Schema Name</span>
          </dt>
          <dd>
            <span>{{ dpTable.schemaName }}</span>
          </dd>
          <dt>
            <span>Description</span>
          </dt>
          <dd>
            <span>{{ dpTable.description }}</span>
          </dd>
          <dt>
            <span>Source DB Type</span>
          </dt>
          <dd>
            <span>{{ dpTable.sourceDBType }}</span>
          </dd>
          <dt>
            <span>Source Id</span>
          </dt>
          <dd>
            <span>{{ dpTable.sourceId }}</span>
          </dd>
          <dt>
            <span>Source Schema</span>
          </dt>
          <dd>
            <span>{{ dpTable.sourceSchema }}</span>
          </dd>
          <dt>
            <span>Source Table</span>
          </dt>
          <dd>
            <span>{{ dpTable.sourceTable }}</span>
          </dd>
          <dt>
            <span>Sql Content</span>
          </dt>
          <dd>
            <span>{{ dpTable.sqlContent }}</span>
          </dd>
          <dt>
            <span>File Type</span>
          </dt>
          <dd>
            <span>{{ dpTable.fileType }}</span>
          </dd>
          <dt>
            <span>File Path</span>
          </dt>
          <dd>
            <span>{{ dpTable.filePath }}</span>
          </dd>
          <dt>
            <span>Update Time</span>
          </dt>
          <dd>
            <span v-if="dpTable.updateTime">{{ formatDateLong(dpTable.updateTime) }}</span>
          </dd>
          <dt>
            <span>Create Time</span>
          </dt>
          <dd>
            <span v-if="dpTable.createTime">{{ formatDateLong(dpTable.createTime) }}</span>
          </dd>
          <dt>
            <span>Tenant Id</span>
          </dt>
          <dd>
            <span>{{ dpTable.tenantId }}</span>
          </dd>
        </dl>
        <el-button type="info" native-type="submit" @click.prevent="previousState()" data-cy="entityDetailsBackButton">
          <font-awesome-icon icon="arrow-left"></font-awesome-icon>&nbsp;<span>返回</span>
        </el-button>
        <router-link v-if="dpTable.id" :to="{ name: 'DpTableEdit', params: { dpTableId: dpTable.id } }" custom v-slot="{ navigate }">
          <el-button type="primary" @click="navigate">
            <font-awesome-icon icon="pencil-alt"></font-awesome-icon>&nbsp;<span>编辑</span>
          </el-button>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./dp-table-details.component.ts"></script>
