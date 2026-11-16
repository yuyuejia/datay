<template>
  <sql-job-editor :job-id="jobId" :directory-id="directoryId" standalone @saved="onSaved" />
</template>

<script lang="ts" src="./sql-job-update.component.ts"></script>
