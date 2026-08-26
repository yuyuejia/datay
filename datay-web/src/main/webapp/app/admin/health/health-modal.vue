<template>
  <div class="modal-body pad">
    <div v-if="currentHealth && currentHealth.details">
      <h5>属性</h5>
      <div class="table-responsive">
        <table class="table table-striped" aria-describedby="Health">
          <thead>
            <tr>
              <th class="text-left" scope="col">名称</th>
              <th class="text-left" scope="col">值</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in currentHealth.details.details" :key="index">
              <td class="text-left">{{ index }}</td>
              <td class="text-left">{{ readableValue(item) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <div v-if="currentHealth && currentHealth.error">
      <h4>错误</h4>
      <pre>{{ currentHealth.error }}</pre>
    </div>
  </div>
</template>

<script lang="ts" src="./health-modal.component.ts"></script>
