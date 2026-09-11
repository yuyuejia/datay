<template>
  <div>
    <b-dropdown-item to="/sql-job" active-class="active">
        <span>SQL 任务</span>
    </b-dropdown-item>
    <b-dropdown-item to="/dag-job" active-class="active">
      <span>任务编排</span>
    </b-dropdown-item>
    <b-dropdown-item to="/shell-job" active-class="active">
      <span>Shell 任务</span>
    </b-dropdown-item>
    <b-dropdown-item to="/job" active-class="active">
      <span>任务定义</span>
    </b-dropdown-item>
    <b-dropdown-item to="/job-instance" active-class="active">
      <span>任务实例</span>
    </b-dropdown-item>

    <!-- jhipster-needle-add-entity-to-menu - JHipster will add entities to the menu here -->
  </div>
</template>

<script lang="ts" src="./entities-menu.component.ts"></script>
