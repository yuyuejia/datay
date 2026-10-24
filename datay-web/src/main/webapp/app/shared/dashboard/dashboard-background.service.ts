import axios from 'axios';
import FileManagementService from '@/entities/file-management/file-management.service';

export interface DashboardBackgroundUpload {
  path: string;
  contentType?: string;
}

/**
 * 大屏背景图：复用文件存储上传/删除，spec 中只保存路径。
 *
 * <p>应用使用 JWT 认证，CSS background-image 无法携带 Authorization 头，
 * 因此图片通过 axios 加载成 blob URL 后再用于预览和画布背景。
 */
export default class DashboardBackgroundService {
  private files = new FileManagementService();

  upload(file: File): Promise<DashboardBackgroundUpload> {
    const path = `dashboard-backgrounds/${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    return this.files.upload(file, path).then(result => ({
      path: result.path,
      contentType: result.contentType,
    }));
  }

  remove(path: string): Promise<void> {
    return this.files.delete(path).then(() => undefined);
  }

  async loadImageUrl(path: string): Promise<string> {
    const response = await axios.get('api/files/view', {
      params: { path },
      responseType: 'blob',
    });
    return URL.createObjectURL(response.data);
  }
}
