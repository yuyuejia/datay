import axios from 'axios';

const baseApiUrl = 'api/ai/decision/config';

export interface IDecisionConfig {
  enabled: boolean;
  baseUrl: string;
  apiKey: string;
  model: string;
  timeoutSeconds: number;
}

export interface IDecisionTestResult {
  success?: boolean;
  strategy?: string;
  error?: string;
}

export default class DecisionConfigService {
  getConfig(): Promise<IDecisionConfig> {
    return new Promise<IDecisionConfig>((resolve, reject) => {
      axios
        .get(baseApiUrl)
        .then(res => resolve(res.data))
        .catch(err => reject(err));
    });
  }

  updateConfig(config: IDecisionConfig): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .post(baseApiUrl, config)
        .then(res => resolve(res.data))
        .catch(err => reject(err));
    });
  }

  testConfig(): Promise<IDecisionTestResult> {
    return new Promise<IDecisionTestResult>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/test`)
        .then(res => resolve(res.data))
        .catch(err => reject(err));
    });
  }
}
