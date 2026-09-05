import axios from "axios";

import { type IModelDirectory } from "@/shared/model/model-directory.model";

const baseApiUrl = "api/model-directories";

export default class ModelDirectoryService {
  find(id: number): Promise<IModelDirectory> {
    return new Promise<IModelDirectory>((resolve, reject) => {
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

  retrieveByParent(parentId?: number): Promise<any> {
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

  create(entity: IModelDirectory): Promise<IModelDirectory> {
    return new Promise<IModelDirectory>((resolve, reject) => {
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

  update(entity: IModelDirectory): Promise<IModelDirectory> {
    return new Promise<IModelDirectory>((resolve, reject) => {
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
