import axios from 'axios';

const baseApiUrl = 'api/data-quality-tasks';
const resultApiUrl = 'api/data-quality-results';

export interface IDataQualityRuleBinding {
  ruleId?: string;
  ruleCode?: string;
  ruleName?: string;
  ruleLevel?: string;
  ruleType?: string;
  column?: string;
  columns?: string[];
  config?: Record<string, any>;
  severity?: string;
  maxInvalidRows?: number;
  maxInvalidRatio?: number;
}

export interface IDataQualityRuleResult {
  ruleKey?: string;
  ruleName?: string;
  ruleLevel?: string;
  columnName?: string;
  ruleType?: string;
  severity?: string;
  checkedRows?: number;
  invalidRows?: number;
  invalidRatio?: number;
  maxInvalidRows?: number;
  maxInvalidRatio?: number;
  status?: string;
  message?: string;
  detailUrl?: string;
}

export interface IDataQualityResult {
  id?: string;
  taskId?: string;
  taskName?: string;
  jobId?: string;
  jobInstanceId?: string;
  instanceCode?: string;
  status?: string;
  overallStatus?: string;
  startTime?: string;
  endTime?: string;
  createTime?: string;
  jobMessage?: string;
  dataSourceId?: string;
  schemaName?: string;
  tableName?: string;
  outputDir?: string;
  resultFormat?: string;
  summaryPath?: string;
  downloadUrl?: string;
  totalRules?: number;
  passRules?: number;
  warnRules?: number;
  failRules?: number;
  checkedRows?: number;
  rules?: IDataQualityRuleResult[];
}

export interface IDataQualityTask {
  id?: string;
  taskName?: string;
  taskDesc?: string;
  dataSourceId?: string;
  schemaName?: string;
  tableName?: string;
  whereClause?: string;
  failOnWarn?: boolean;
  sampleLimit?: number;
  outputFormat?: string;
  outputDir?: string;
  jobId?: string;
  status?: string;
  lastStatus?: string;
  lastRunTime?: string;
  createTime?: string;
  updateTime?: string;
  creater?: string;
  rules?: IDataQualityRuleBinding[];
  lastResult?: IDataQualityResult;
}

export default class DataQualityTaskService {
  retrieve(params: Record<string, any> = {}): Promise<any> {
    return axios.get(baseApiUrl, { params });
  }

  find(id: string): Promise<IDataQualityTask> {
    return axios.get(`${baseApiUrl}/${id}`).then(res => res.data);
  }

  create(task: IDataQualityTask): Promise<IDataQualityTask> {
    return axios.post(baseApiUrl, task).then(res => res.data);
  }

  update(task: IDataQualityTask): Promise<IDataQualityTask> {
    return axios.put(`${baseApiUrl}/${task.id}`, task).then(res => res.data);
  }

  delete(id: string): Promise<any> {
    return axios.delete(`${baseApiUrl}/${id}`);
  }

  execute(id: string): Promise<IDataQualityResult> {
    return axios.post(`${baseApiUrl}/${id}/execute`).then(res => res.data);
  }

  getResults(taskId: string, page = 0, size = 20): Promise<any> {
    return axios.get(`${baseApiUrl}/${taskId}/results`, { params: { page, size, sort: ['createTime,desc'] } });
  }

  getResult(id: string): Promise<IDataQualityResult> {
    return axios.get(`${resultApiUrl}/${id}`).then(res => res.data);
  }

  getResultByInstanceId(jobInstanceId: string): Promise<IDataQualityResult> {
    return axios.get(`${resultApiUrl}/by-instance-id/${encodeURIComponent(jobInstanceId)}`).then(res => res.data);
  }

  getResultByInstanceCode(instanceCode: string): Promise<IDataQualityResult> {
    return axios.get(`${resultApiUrl}/by-instance-code/${encodeURIComponent(instanceCode)}`).then(res => res.data);
  }

  getDownloadUrl(result: IDataQualityResult): string {
    if (result?.id) {
      return `${resultApiUrl}/by-instance-id/${encodeURIComponent(result.id)}/download`;
    }
    return '';
  }

  getDetailUrl(result: IDataQualityResult, rule: IDataQualityRuleResult): string {
    if (!result?.id || !rule?.ruleKey) {
      return '';
    }
    return `${resultApiUrl}/by-instance-id/${encodeURIComponent(result.id)}/detail?ruleKey=${encodeURIComponent(rule.ruleKey)}`;
  }

  async downloadFile(url: string, fileName: string): Promise<void> {
    const response = await axios.get(url, { responseType: 'blob' });
    const objectUrl = window.URL.createObjectURL(response.data as Blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(objectUrl);
  }
}
