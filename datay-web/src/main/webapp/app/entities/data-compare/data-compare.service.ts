import axios from 'axios';

const baseApiUrl = 'api/data-compare-tasks';
const resultApiUrl = 'api/data-compare-results';

export interface IDataCompareTable {
  leftTable?: string;
  rightTable?: string;
  leftWhere?: string;
  rightWhere?: string;
  keyColumns?: string;
  compareColumns?: string;
}

export interface IDataCompareTableResult {
  index?: number;
  leftTable?: string;
  rightTable?: string;
  leftRowCount?: number | null;
  rightRowCount?: number | null;
  sameRowCount?: number | null;
  diffRowCount?: number | null;
  onlyLeftCount?: number | null;
  onlyRightCount?: number | null;
  valueDiffCount?: number | null;
  resultPath?: string;
  summaryPath?: string;
  resultFormat?: string;
  downloadUrl?: string;
  summaryUrl?: string;
}

export interface IDataCompareResult {
  id?: string;
  taskId?: string;
  taskName?: string;
  jobId?: string;
  jobInstanceId?: string;
  instanceCode?: string;
  status?: string;
  startTime?: string;
  endTime?: string;
  leftRowCount?: number | null;
  rightRowCount?: number | null;
  sameRowCount?: number | null;
  diffRowCount?: number | null;
  onlyLeftCount?: number | null;
  onlyRightCount?: number | null;
  valueDiffCount?: number | null;
  compareRecordCount?: boolean;
  compareDetail?: boolean;
  outputDir?: string;
  resultPath?: string;
  summaryPath?: string;
  resultFormat?: string;
  jobMessage?: string;
  createTime?: string;
  downloadUrl?: string;
  tables?: IDataCompareTableResult[];
}

export interface IDataCompareTask {
  id?: string;
  taskName?: string;
  taskDesc?: string;
  leftDataSourceId?: string;
  leftSchema?: string;
  leftTable?: string;
  leftWhere?: string;
  rightDataSourceId?: string;
  rightSchema?: string;
  rightTable?: string;
  rightWhere?: string;
  keyColumns?: string;
  compareColumns?: string;
  compareRecordCount?: boolean;
  compareDetail?: boolean;
  ignoreCase?: boolean;
  trimWhitespace?: boolean;
  outputFormat?: string;
  outputDir?: string;
  jobId?: string;
  status?: string;
  lastStatus?: string;
  lastRunTime?: string;
  createTime?: string;
  updateTime?: string;
  creater?: string;
  tables?: IDataCompareTable[];
  lastResult?: IDataCompareResult;
}

export default class DataCompareService {
  retrieve(params: Record<string, any> = {}): Promise<any> {
    return axios.get(baseApiUrl, { params });
  }

  find(id: string): Promise<IDataCompareTask> {
    return axios.get(`${baseApiUrl}/${id}`).then(res => res.data);
  }

  create(task: IDataCompareTask): Promise<IDataCompareTask> {
    return axios.post(baseApiUrl, task).then(res => res.data);
  }

  update(task: IDataCompareTask): Promise<IDataCompareTask> {
    return axios.put(`${baseApiUrl}/${task.id}`, task).then(res => res.data);
  }

  delete(id: string): Promise<any> {
    return axios.delete(`${baseApiUrl}/${id}`);
  }

  execute(id: string): Promise<IDataCompareResult> {
    return axios.post(`${baseApiUrl}/${id}/execute`).then(res => res.data);
  }

  getResults(taskId: string, page = 0, size = 20): Promise<any> {
    return axios.get(`${baseApiUrl}/${taskId}/results`, { params: { page, size, sort: ['createTime,desc'] } });
  }

  getResult(id: string): Promise<IDataCompareResult> {
    return axios.get(`${resultApiUrl}/${id}`).then(res => res.data);
  }

  getResultByInstanceId(jobInstanceId: string): Promise<IDataCompareResult> {
    return axios.get(`${resultApiUrl}/by-instance-id/${encodeURIComponent(jobInstanceId)}`).then(res => res.data);
  }

  getResultByInstanceCode(instanceCode: string): Promise<IDataCompareResult> {
    return axios.get(`${resultApiUrl}/by-instance-code/${encodeURIComponent(instanceCode)}`).then(res => res.data);
  }

  previewResult(id: string, limit = 100, tableIndex = 0): Promise<any> {
    return axios.get(`${resultApiUrl}/${id}/preview`, { params: { limit, tableIndex } }).then(res => res.data);
  }

  getDownloadUrl(result: IDataCompareResult): string {
    if (result?.downloadUrl) {
      return this.apiUrl(result.downloadUrl);
    }
    if (result?.resultPath) {
      return `api/files/download?path=${encodeURIComponent(result.resultPath)}`;
    }
    return '';
  }

  getSummaryDownloadUrl(jobInstanceId: string): string {
    return this.apiUrl(`${resultApiUrl}/by-instance-id/${encodeURIComponent(jobInstanceId)}/summary`);
  }

  async downloadResultFile(result: IDataCompareResult, table?: IDataCompareTableResult): Promise<void> {
    const url = table?.downloadUrl ? this.apiUrl(table.downloadUrl) : this.getDownloadUrl(result);
    if (!url) {
      throw new Error('结果文件地址为空');
    }
    const path = table?.resultPath || result.resultPath;
    const fileName = path?.split('/').pop() || `diff_result_${result.jobInstanceId || ''}.csv`;
    await this.downloadBlob(url, fileName);
  }

  async downloadSummaryFile(jobInstanceId: string, table?: IDataCompareTableResult): Promise<void> {
    if (table?.summaryUrl) {
      const fileName = table.summaryPath?.split('/').pop() || `summary_${jobInstanceId}.csv`;
      await this.downloadBlob(this.apiUrl(table.summaryUrl), fileName);
      return;
    }
    await this.downloadBlob(this.getSummaryDownloadUrl(jobInstanceId), `summary_${jobInstanceId}.csv`);
  }

  /**
   * 结果文件接口受 JWT 保护，必须通过 axios 携带 Authorization 下载，
   * 不能使用 window.open。
   */
  private async downloadBlob(url: string, fileName: string): Promise<void> {
    const response = await axios.get(url, { responseType: 'blob' });
    const blob = response.data as Blob;
    const objectUrl = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(objectUrl);
  }

  /**
   * axios 拦截器会统一补 SERVER_API_URL（"/"），因此这里保持不带前导斜杠的 api/...。
   */
  private apiUrl(url: string): string {
    return (url || '').replace(/^\/+/, '');
  }
}
