<template>
  <div>
    <!-- <b-dropdown-item to="/etl-task">
      <font-awesome-icon icon="asterisk" />
      <span>ETL任务</span>
    </b-dropdown-item> -->
    <b-dropdown-item to="/data-sync">
      <span>数据同步</span>
    </b-dropdown-item>
    <b-dropdown-item to="/dag-job">
      <span>任务编排</span>
    </b-dropdown-item>
    <b-dropdown-item to="/job">
      <span>任务定义</span>
    </b-dropdown-item>
    <b-dropdown-item to="/job-instance">
      <span>任务实例</span>
    </b-dropdown-item>

    <!-- jhipster-needle-add-entity-to-menu - JHipster will add entities to the menu here -->
  </div>
</template>

<script lang="ts" src="./entities-menu.component.ts"></script>
