import axios from "axios";

import { type IMetricDirectory } from "@/shared/model/metric-directory.model";

const baseApiUrl = "api/metric-directories";

export default class MetricDirectoryService {
  find(id: string): Promise<IMetricDirectory> {
    return new Promise<IMetricDirectory>((resolve, reject) => {
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

  retrieve(): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}`)
        .then((res) => {
          resolve(res);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }

  retrieveByParent(parentId?: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      const params = parentId != null ? { params: { parentId } } : {};
      axios
        .get(`${baseApiUrl}/by-parent`, params)
        .then((res) => {
          resolve(res);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }

  delete(id: string): Promise<any> {
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

  create(entity: IMetricDirectory): Promise<IMetricDirectory> {
    return new Promise<IMetricDirectory>((resolve, reject) => {
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

  update(entity: IMetricDirectory): Promise<IMetricDirectory> {
    return new Promise<IMetricDirectory>((resolve, reject) => {
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
}
