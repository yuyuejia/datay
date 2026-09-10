import axios from "axios";

import buildPaginationQueryOpts from "@/shared/sort/sorts";
import { type IJob } from "@/shared/model/job.model";

const baseApiUrl = "api/jobs";
const dataSourceApiUrl = "api/data-sources";

/**
 * Payload persisted inside {@link IJob.jobContext} for a SQL task.
 */
export interface ISqlTaskConfig {
  dataSourceId?: number | null;
  schema?: string | null;
  sql?: string | null;
}

/**
 * Service for the SQL task management (SQL任务). A SQL task is persisted as a
 * regular Job whose type is {@code SQL} and whose {@code jobContext} stores the
 * selected data source and the SQL to execute.
 */
export default class SqlJobService {
  /**
   * Query jobs. Supported extra query params: {@code type}, {@code typeNot}.
   */
  retrieve(paginationQuery?: any): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}?${buildPaginationQueryOpts(paginationQuery)}`)
        .then((res) => {
          resolve(res);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }

  /** List the SQL jobs (paged). */
  retrieveSqlJobs(paginationQuery?: any): Promise<any> {
    return this.retrieve({ ...paginationQuery, type: "SQL" });
  }

  find(id: number): Promise<IJob> {
    return new Promise<IJob>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${id}`)
        .then((res) => {
          resolve(res.data);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }

  create(entity: IJob): Promise<IJob> {
    return new Promise<IJob>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}`, entity)
        .then((res) => {
          resolve(res.data);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }

  update(entity: IJob): Promise<IJob> {
    return new Promise<IJob>((resolve, reject) => {
      axios
        .put(`${baseApiUrl}/${entity.id}`, entity)
        .then((res) => {
          resolve(res.data);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }

  delete(id: number): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .delete(`${baseApiUrl}/${id}`)
        .then((res) => {
          resolve(res);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }

  run(id: number): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}/${id}/run`)
        .then((res) => {
          resolve(res);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }

  online(id: number): Promise<IJob> {
    return new Promise<IJob>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}/${id}/online`)
        .then((res) => {
          resolve(res.data);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }

  offline(id: number): Promise<IJob> {
    return new Promise<IJob>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}/${id}/offline`)
        .then((res) => {
          resolve(res.data);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }

  /** Debug run: execute the given SQL against a data source without saving a job. */
  debugQuery(dataSourceId: number, sql: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .post(`${dataSourceApiUrl}/${dataSourceId}/query`, { sql })
        .then((res) => {
          resolve(res.data);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }
}
