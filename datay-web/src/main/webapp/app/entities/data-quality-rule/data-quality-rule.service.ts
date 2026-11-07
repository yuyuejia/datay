import axios from 'axios';

const baseApiUrl = 'api/data-quality-rules';

export interface IDataQualityRule {
  id?: string;
  ruleName?: string;
  ruleCode?: string;
  ruleLevel?: string;
  ruleType?: string;
  description?: string;
  defaultConfig?: string;
  severity?: string;
  enabled?: boolean;
  builtin?: boolean;
  createTime?: string;
  updateTime?: string;
  creater?: string;
  tenantId?: string;
}

export interface IQualityRuleType {
  code: string;
  label: string;
  level: string;
  description: string;
}

export default class DataQualityRuleService {
  retrieve(params: Record<string, any> = {}): Promise<any> {
    return axios.get(baseApiUrl, { params });
  }

  find(id: string): Promise<IDataQualityRule> {
    return axios.get(`${baseApiUrl}/${id}`).then(res => res.data);
  }

  create(rule: IDataQualityRule): Promise<IDataQualityRule> {
    return axios.post(baseApiUrl, rule).then(res => res.data);
  }

  update(rule: IDataQualityRule): Promise<IDataQualityRule> {
    return axios.put(`${baseApiUrl}/${rule.id}`, rule).then(res => res.data);
  }

  delete(id: string): Promise<any> {
    return axios.delete(`${baseApiUrl}/${id}`);
  }

  getTypes(): Promise<IQualityRuleType[]> {
    return axios.get(`${baseApiUrl}/types`).then(res => res.data);
  }
}
