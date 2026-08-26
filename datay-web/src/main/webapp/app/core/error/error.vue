<template>
  <div>
    <div class="row">
      <div class="col-md-3">
        <span class="hipster img-fluid rounded"></span>
      </div>
      <div class="col-md-9">
        <h1>错误页面!</h1>

        <div v-if="errorMessage">
          <div class="alert alert-danger">{{ errorMessage }}</div>
        </div>
        <div v-if="error403" class="alert alert-danger">您没有权限访问此页面.</div>
        <div v-if="error404" class="alert alert-warning">该页面不存在.</div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./error.component.ts"></script>
