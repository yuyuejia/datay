<template>
  <el-dropdown-item command="/sql-job">
    <span>SQL 任务</span>
  </el-dropdown-item>
  <el-dropdown-item command="/dag-job">
    <span>任务编排</span>
  </el-dropdown-item>
  <el-dropdown-item command="/shell-job">
    <span>Shell 任务</span>
  </el-dropdown-item>
  <el-dropdown-item command="/job">
    <span>任务定义</span>
  </el-dropdown-item>
  <el-dropdown-item command="/job-instance">
    <span>任务实例</span>
  </el-dropdown-item>

  <!-- jhipster-needle-add-entity-to-menu - JHipster will add entities to the menu here -->
</template>

<script lang="ts" src="./entities-menu.component.ts"></script>
