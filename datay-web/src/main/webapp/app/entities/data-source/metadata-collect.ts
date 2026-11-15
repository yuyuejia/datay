import axios from 'axios';

const baseApiUrl = 'api/metadata-collect-tasks';

export interface IMetadataCollectRun {
  id?: string;
  instanceCode?: string;
  status?: string;
  startTime?: string;
  endTime?: string;
  createTime?: string;
  jobMessage?: string;
  outputDir?: string;
  tablesPath?: string;
  columnsPath?: string;
  tablesDownloadUrl?: string;
  columnsDownloadUrl?: string;
}

export interface IMetadataCollectTask {
  id?: string;
  dataSourceId?: string;
  dataSourceName?: string;
  taskName?: string;
  schemaNames?: string[];
  cron?: string;
  enabled?: boolean;
  jobId?: string;
  status?: string;
  lastStatus?: string;
  lastRunTime?: string;
  createTime?: string;
  updateTime?: string;
  creater?: string;
  tenantId?: string;
  lastRun?: IMetadataCollectRun;
}

export default class MetadataCollectTaskService {
  getByDataSource(dataSourceId: string): Promise<IMetadataCollectTask | null> {
    return axios.get(`${baseApiUrl}/by-data-source/${dataSourceId}`).then(res => res.data ?? null);
  }

  save(task: IMetadataCollectTask): Promise<IMetadataCollectTask> {
    return axios.post(baseApiUrl, task).then(res => res.data);
  }

  execute(id: string): Promise<IMetadataCollectRun> {
    return axios.post(`${baseApiUrl}/${id}/execute`).then(res => res.data);
  }

  getRuns(taskId: string, page = 0, size = 20): Promise<any> {
    return axios.get(`${baseApiUrl}/${taskId}/runs`, { params: { page, size, sort: ['createTime,desc'] } });
  }

  delete(id: string): Promise<any> {
    return axios.delete(`${baseApiUrl}/${id}`);
  }
}
