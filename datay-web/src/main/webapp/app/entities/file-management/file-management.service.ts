import axios from 'axios';

const baseApiUrl = 'api/files';

export interface FileInfo {
  path: string;
  name: string;
  size: number;
  lastModified: number;
  isDirectory: boolean;
}

export interface UploadResult {
  path: string;
  name: string;
  originalName: string;
  size: number;
  contentType: string;
}

export interface FileStorageConfig {
  type: string;
  localBasePath: string;
  minioEndpoint: string;
  minioAccessKey: string;
  minioSecretKey: string;
  minioBucketName: string;
}

export default class FileManagementService {
  list(path: string = ''): Promise<FileInfo[]> {
    return new Promise<FileInfo[]>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/list`, { params: { path } })
        .then(res => resolve(res.data))
        .catch(err => reject(err));
    });
  }

  upload(file: File, path: string = ''): Promise<UploadResult> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('path', path);
    return new Promise<UploadResult>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}/upload`, formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        })
        .then(res => resolve(res.data))
        .catch(err => reject(err));
    });
  }

  delete(path: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .delete(`${baseApiUrl}/delete`, { params: { path } })
        .then(res => resolve(res.data))
        .catch(err => reject(err));
    });
  }

  mkdir(path: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}/mkdir`, null, { params: { path } })
        .then(res => resolve(res.data))
        .catch(err => reject(err));
    });
  }

  getDownloadUrl(path: string): string {
    return `${baseApiUrl}/download?path=${encodeURIComponent(path)}`;
  }

  async download(path: string): Promise<void> {
    const res = await axios.get(`${baseApiUrl}/download`, {
      params: { path },
      responseType: 'blob',
    });
    const url = window.URL.createObjectURL(res.data);
    const fileName = path.includes('/') ? path.substring(path.lastIndexOf('/') + 1) : path;
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
  }

  getStorageConfig(): Promise<FileStorageConfig> {
    return new Promise<FileStorageConfig>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/storage-config`)
        .then(res => resolve(res.data))
        .catch(err => reject(err));
    });
  }

  updateStorageConfig(config: FileStorageConfig): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}/storage-config`, config)
        .then(res => resolve(res.data))
        .catch(err => reject(err));
    });
  }

  testStorageConfig(): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/storage-config/test`)
        .then(res => resolve(res.data))
        .catch(err => reject(err));
    });
  }
}