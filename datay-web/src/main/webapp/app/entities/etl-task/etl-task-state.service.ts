import axios from 'axios';

const baseApiUrl = 'api/etl-task-state';

export interface IStatusStorageConfig {
  type: string;
  localBasePath: string;
  minioEndpoint: string;
  minioAccessKey: string;
  minioSecretKey: string;
  minioBucketName: string;
}

export interface IEtlTaskStateResponse {
  taskId: string;
  taskName: string;
  taskCode: string;
  jobCode: string;
  state: Record<string, any>;
  nodeNames?: Record<string, string>;
}

export interface IEtlTaskStateSummary {
  taskId: string;
  taskName: string;
  taskCode: string;
  jobCode: string;
  keys: string[];
}

export default class EtlTaskStateService {
  getConfig(): Promise<IStatusStorageConfig> {
    return new Promise<IStatusStorageConfig>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/config`)
        .then(res => resolve(res.data))
        .catch(err => reject(err));
    });
  }

  updateConfig(config: IStatusStorageConfig): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}/config`, config)
        .then(res => resolve(res.data))
        .catch(err => reject(err));
    });
  }

  testConfig(): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/config/test`)
        .then(res => resolve(res.data))
        .catch(err => reject(err));
    });
  }

  listStates(): Promise<IEtlTaskStateSummary[]> {
    return new Promise<IEtlTaskStateSummary[]>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}`)
        .then(res => resolve(res.data))
        .catch(err => reject(err));
    });
  }

  getState(taskId: string): Promise<IEtlTaskStateResponse> {
    return new Promise<IEtlTaskStateResponse>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${taskId}`)
        .then(res => resolve(res.data))
        .catch(err => reject(err));
    });
  }

  patchState(taskId: string, patch: Record<string, any>): Promise<IEtlTaskStateResponse> {
    return new Promise<IEtlTaskStateResponse>((resolve, reject) => {
      axios
        .patch(`${baseApiUrl}/${taskId}`, patch)
        .then(res => resolve(res.data))
        .catch(err => reject(err));
    });
  }

  deleteState(taskId: string, key?: string, force = false): Promise<IEtlTaskStateResponse> {
    return new Promise<IEtlTaskStateResponse>((resolve, reject) => {
      axios
        .delete(`${baseApiUrl}/${taskId}`, { params: { key, force } })
        .then(res => resolve(res.data))
        .catch(err => reject(err));
    });
  }
}
