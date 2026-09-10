import axios from "axios";

import buildPaginationQueryOpts from "@/shared/sort/sorts";
import { type IJob } from "@/shared/model/job.model";

const baseApiUrl = "api/jobs";

/**
 * Service for the Shell task management (Shell任务). A Shell task is persisted
 * as a regular Job whose type is {@code SHELL} and whose {@code jobContext}
 * stores the shell script to execute.
 */
export default class ShellJobService {
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

  /** List the Shell jobs (paged). */
  retrieveShellJobs(paginationQuery?: any): Promise<any> {
    return this.retrieve({ ...paginationQuery, type: "SHELL" });
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
}
