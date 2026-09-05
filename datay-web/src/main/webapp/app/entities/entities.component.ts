import { defineComponent, provide } from "vue";

import DataSourceService from "./data-source/data-source.service";
import JobInstanceService from "./job-instance/job-instance.service";
import JobService from "./job/job.service";
import JobDependService from "./job-depend/job-depend.service";
import DataSyncService from "./data-sync/data-sync.service";
import DataSyncTableConfigService from "./data-sync-table-config/data-sync-table-config.service";
import ETLTaskService from "./etl-task/etl-task.service";
import ETLComponentService from "./etl-component/etl-component.service";
import ETLNodeService from "./etl-node/etl-node.service";
import ETLEdgeService from "./etl-edge/etl-edge.service";
import DpTableService from "./dp-table/dp-table.service";
import ServiceConfigService from "./service-config/service-config.service";
import DataModelService from "./data-model/data-model.service";
import ModelDirectoryService from "./data-model/model-directory.service";
import UserService from "@/entities/user/user.service";
// jhipster-needle-add-entity-service-to-entities-component-import - JHipster will import entities services here

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: "Entities",
  setup() {
    provide("userService", () => new UserService());
    provide("dataSourceService", () => new DataSourceService());
    provide("jobInstanceService", () => new JobInstanceService());
    provide("jobService", () => new JobService());
    provide("jobDependService", () => new JobDependService());
    provide("dataSyncService", () => new DataSyncService());
    provide(
      "dataSyncTableConfigService",
      () => new DataSyncTableConfigService(),
    );
    provide("eTLTaskService", () => new ETLTaskService());
    provide("eTLComponentService", () => new ETLComponentService());
    provide("eTLNodeService", () => new ETLNodeService());
    provide("eTLEdgeService", () => new ETLEdgeService());
    provide("dpTableService", () => new DpTableService());
    provide("serviceConfigService", () => new ServiceConfigService());
    provide("dataModelService", () => new DataModelService());
    provide("modelDirectoryService", () => new ModelDirectoryService());
    // jhipster-needle-add-entity-service-to-entities-component - JHipster will import entities services here
  },
});
