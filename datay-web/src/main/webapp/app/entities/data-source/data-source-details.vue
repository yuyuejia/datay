<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <div v-if="dataSource">
        <h2 class="jh-entity-heading" data-cy="dataSourceDetailsHeading"><span>Data Source</span> {{ dataSource.id }}</h2>
        <dl class="row jh-entity-details">
          <dt>
            <span>Name</span>
          </dt>
          <dd>
            <span>{{ dataSource.name }}</span>
          </dd>
          <dt>
            <span>Description</span>
          </dt>
          <dd>
            <span>{{ dataSource.description }}</span>
          </dd>
          <dt>
            <span>Type</span>
          </dt>
          <dd>
            <span>{{ dataSource.type }}</span>
          </dd>
          <dt>
            <span>Url</span>
          </dt>
          <dd>
            <span>{{ dataSource.url }}</span>
          </dd>
          <dt>
            <span>Hostname</span>
          </dt>
          <dd>
            <span>{{ dataSource.hostname }}</span>
          </dd>
          <dt>
            <span>Port</span>
          </dt>
          <dd>
            <span>{{ dataSource.port }}</span>
          </dd>
          <dt>
            <span>Schema Name</span>
          </dt>
          <dd>
            <span>{{ dataSource.schemaName }}</span>
          </dd>
          <dt>
            <span>Username</span>
          </dt>
          <dd>
            <span>{{ dataSource.username }}</span>
          </dd>
          <dt>
            <span>Password</span>
          </dt>
          <dd>
            <span>{{ dataSource.password }}</span>
          </dd>
          <dt>
            <span>Update Time</span>
          </dt>
          <dd>
            <span v-if="dataSource.updateTime">{{ formatDateLong(dataSource.updateTime) }}</span>
          </dd>
          <dt>
            <span>Create Time</span>
          </dt>
          <dd>
            <span v-if="dataSource.createTime">{{ formatDateLong(dataSource.createTime) }}</span>
          </dd>
          <dt>
            <span>Tenant Id</span>
          </dt>
          <dd>
            <span>{{ dataSource.tenantId }}</span>
          </dd>
        </dl>
        <el-button type="info" native-type="submit" @click.prevent="previousState()" data-cy="entityDetailsBackButton">
          <font-awesome-icon icon="arrow-left"></font-awesome-icon>&nbsp;<span>返回</span>
        </el-button>
        <el-button type="primary" v-if="dataSource.id" @click="openEditModal">
          <font-awesome-icon icon="pencil-alt"></font-awesome-icon>&nbsp;<span>编辑</span>
        </el-button>
      </div>
    </div>

    <data-source-modal
      v-model:show="modalShow"
      mode="edit"
      :data-source-id="dataSource.id"
      @saved="onModalSaved"
    ></data-source-modal>
  </div>
</template>

<script lang="ts" src="./data-source-details.component.ts"></script>