import { Authority } from "@/shared/security/authority";
const Entities = () => import("@/entities/entities.vue");

const DataSource = () => import("@/entities/data-source/data-source.vue");
const DataSourceUpdate = () =>
  import("@/entities/data-source/data-source-update.vue");
const DataSourceDetails = () =>
  import("@/entities/data-source/data-source-details.vue");
const DataSourceQuery = () => import("@/entities/data-source/data-query.vue");

const JobInstance = () => import("@/entities/job-instance/job-instance.vue");
const JobInstanceUpdate = () =>
  import("@/entities/job-instance/job-instance-update.vue");
const JobInstanceDetails = () =>
  import("@/entities/job-instance/job-instance-details.vue");

const Job = () => import("@/entities/job/job.vue");
const JobUpdate = () => import("@/entities/job/job-update.vue");
const JobDetails = () => import("@/entities/job/job-details.vue");

const DagJob = () => import("@/entities/dag-job/dag-job.vue");
const DagJobDesign = () => import("@/entities/dag-job/dag-job-design.vue");

const JobDepend = () => import("@/entities/job-depend/job-depend.vue");
const JobDependUpdate = () =>
  import("@/entities/job-depend/job-depend-update.vue");
const JobDependDetails = () =>
  import("@/entities/job-depend/job-depend-details.vue");

const DataSync = () => import("@/entities/data-sync/data-sync.vue");
const DataSyncUpdate = () =>
  import("@/entities/data-sync/data-sync-update.vue");
const DataSyncDetails = () =>
  import("@/entities/data-sync/data-sync-details.vue");

const DataSyncTableConfig = () =>
  import("@/entities/data-sync-table-config/data-sync-table-config.vue");
const DataSyncTableConfigUpdate = () =>
  import("@/entities/data-sync-table-config/data-sync-table-config-update.vue");
const DataSyncTableConfigDetails = () =>
  import(
    "@/entities/data-sync-table-config/data-sync-table-config-details.vue"
  );

const ETLTask = () => import("@/entities/etl-task/etl-task.vue");
const ETLTaskUpdate = () => import("@/entities/etl-task/etl-task-update.vue");
const ETLTaskDetails = () => import("@/entities/etl-task/etl-task-details.vue");
const ETLTaskDesign = () => import("@/entities/etl-task/etl-task-design.vue");

const ETLComponent = () => import("@/entities/etl-component/etl-component.vue");
const ETLComponentUpdate = () =>
  import("@/entities/etl-component/etl-component-update.vue");
const ETLComponentDetails = () =>
  import("@/entities/etl-component/etl-component-details.vue");

const ETLNode = () => import("@/entities/etl-node/etl-node.vue");
const ETLNodeUpdate = () => import("@/entities/etl-node/etl-node-update.vue");
const ETLNodeDetails = () => import("@/entities/etl-node/etl-node-details.vue");

const ETLEdge = () => import("@/entities/etl-edge/etl-edge.vue");
const ETLEdgeUpdate = () => import("@/entities/etl-edge/etl-edge-update.vue");
const ETLEdgeDetails = () => import("@/entities/etl-edge/etl-edge-details.vue");

const DpTable = () => import("@/entities/dp-table/dp-table.vue");
const DpTableUpdate = () => import("@/entities/dp-table/dp-table-update.vue");
const DpTableDetails = () => import("@/entities/dp-table/dp-table-details.vue");

const ServiceConfig = () =>
  import("@/entities/service-config/service-config.vue");
const ServiceConfigUpdate = () =>
  import("@/entities/service-config/service-config-update.vue");
const ServiceConfigDetails = () =>
  import("@/entities/service-config/service-config-details.vue");

const DataModelList = () => import("@/entities/data-model/data-model.vue");
const DataModelUpdate = () =>
  import("@/entities/data-model/data-model-update.vue");

const DataApiList = () => import("@/entities/data-api/data-api.vue");
const DataApiUpdate = () => import("@/entities/data-api/data-api-update.vue");

// jhipster-needle-add-entity-to-router-import - JHipster will import entities to the router here

export default {
  path: "/",
  component: Entities,
  children: [
    {
      path: "data-source",
      name: "DataSource",
      component: DataSource,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "data-source/new",
      name: "DataSourceCreate",
      component: DataSourceUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "data-source/:dataSourceId/edit",
      name: "DataSourceEdit",
      component: DataSourceUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "data-source/:dataSourceId/view",
      name: "DataSourceView",
      component: DataSourceDetails,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "data-source/:dataSourceId/query",
      name: "DataSourceQuery",
      component: DataSourceQuery,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "job-instance",
      name: "JobInstance",
      component: JobInstance,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "job-instance/new",
      name: "JobInstanceCreate",
      component: JobInstanceUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "job-instance/:jobInstanceId/edit",
      name: "JobInstanceEdit",
      component: JobInstanceUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "job-instance/:jobInstanceId/view",
      name: "JobInstanceView",
      component: JobInstanceDetails,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "job",
      name: "Job",
      component: Job,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "job/new",
      name: "JobCreate",
      component: JobUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "job/:jobId/edit",
      name: "JobEdit",
      component: JobUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "job/:jobId/view",
      name: "JobView",
      component: JobDetails,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "dag-job",
      name: "DagJob",
      component: DagJob,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "dag-job/design-new",
      name: "DagJobDesignNew",
      component: DagJobDesign,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "dag-job/:jobId/design",
      name: "DagJobDesign",
      component: DagJobDesign,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "job-depend",
      name: "JobDepend",
      component: JobDepend,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "job-depend/new",
      name: "JobDependCreate",
      component: JobDependUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "job-depend/:jobDependId/edit",
      name: "JobDependEdit",
      component: JobDependUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "job-depend/:jobDependId/view",
      name: "JobDependView",
      component: JobDependDetails,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "data-sync",
      name: "DataSync",
      component: DataSync,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "data-sync/new",
      name: "DataSyncCreate",
      component: DataSyncUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "data-sync/:dataSyncId/edit",
      name: "DataSyncEdit",
      component: DataSyncUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "data-sync/:dataSyncId/view",
      name: "DataSyncView",
      component: DataSyncDetails,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "data-sync-table-config",
      name: "DataSyncTableConfig",
      component: DataSyncTableConfig,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "data-sync-table-config/new",
      name: "DataSyncTableConfigCreate",
      component: DataSyncTableConfigUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "data-sync-table-config/:dataSyncTableConfigId/edit",
      name: "DataSyncTableConfigEdit",
      component: DataSyncTableConfigUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "data-sync-table-config/:dataSyncTableConfigId/view",
      name: "DataSyncTableConfigView",
      component: DataSyncTableConfigDetails,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "etl-task",
      name: "ETLTask",
      component: ETLTask,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "etl-task/new",
      name: "ETLTaskCreate",
      component: ETLTaskDesign,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "etl-task/design-new",
      name: "ETLTaskDesignNew",
      component: ETLTaskDesign,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "etl-task/:eTLTaskId/edit",
      name: "ETLTaskEdit",
      component: ETLTaskUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "etl-task/:eTLTaskId/view",
      name: "ETLTaskView",
      component: ETLTaskDetails,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "etl-task/:eTLTaskId/design",
      name: "ETLTaskDesign",
      component: ETLTaskDesign,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "etl-component",
      name: "ETLComponent",
      component: ETLComponent,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "etl-component/new",
      name: "ETLComponentCreate",
      component: ETLComponentUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "etl-component/:eTLComponentId/edit",
      name: "ETLComponentEdit",
      component: ETLComponentUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "etl-component/:eTLComponentId/view",
      name: "ETLComponentView",
      component: ETLComponentDetails,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "etl-node",
      name: "ETLNode",
      component: ETLNode,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "etl-node/new",
      name: "ETLNodeCreate",
      component: ETLNodeUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "etl-node/:eTLNodeId/edit",
      name: "ETLNodeEdit",
      component: ETLNodeUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "etl-node/:eTLNodeId/view",
      name: "ETLNodeView",
      component: ETLNodeDetails,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "etl-edge",
      name: "ETLEdge",
      component: ETLEdge,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "etl-edge/new",
      name: "ETLEdgeCreate",
      component: ETLEdgeUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "etl-edge/:eTLEdgeId/edit",
      name: "ETLEdgeEdit",
      component: ETLEdgeUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "etl-edge/:eTLEdgeId/view",
      name: "ETLEdgeView",
      component: ETLEdgeDetails,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "dp-table",
      name: "DpTable",
      component: DpTable,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "dp-table/new",
      name: "DpTableCreate",
      component: DpTableUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "dp-table/:dpTableId/edit",
      name: "DpTableEdit",
      component: DpTableUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "dp-table/:dpTableId/view",
      name: "DpTableView",
      component: DpTableDetails,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "service-config",
      name: "ServiceConfig",
      component: ServiceConfig,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "service-config/new",
      name: "ServiceConfigCreate",
      component: ServiceConfigUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "service-config/:serviceConfigId/edit",
      name: "ServiceConfigEdit",
      component: ServiceConfigUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "service-config/:serviceConfigId/view",
      name: "ServiceConfigView",
      component: ServiceConfigDetails,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "data-model",
      name: "DataModel",
      component: DataModelList,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "data-model/new",
      name: "DataModelCreate",
      component: DataModelUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "data-model/:dataModelId/edit",
      name: "DataModelEdit",
      component: DataModelUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "data-api",
      name: "DataApi",
      component: DataApiList,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "data-api/new",
      name: "DataApiCreate",
      component: DataApiUpdate,
      meta: { authorities: [Authority.USER] },
    },
    {
      path: "data-api/:dataApiId/edit",
      name: "DataApiEdit",
      component: DataApiUpdate,
      meta: { authorities: [Authority.USER] },
    },
    // jhipster-needle-add-entity-to-router - JHipster will add entities to the router here
  ],
};
