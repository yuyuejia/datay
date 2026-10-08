import axios from 'axios';

const baseApiUrl = 'api/ai/config';

export interface ILlmConfig {
  enabled: boolean;
  baseUrl: string;
  apiKey: string;
  model: string;
  temperature: number;
  timeoutSeconds: number;
  maxToolRounds: number;
  maxToolCallsPerRound: number;
  allowMutatingTools: boolean;
}

export interface ILlmTestResult {
  success?: boolean;
  strategy?: string;
  error?: string;
}

export default class LlmConfigService {
  getConfig(): Promise<ILlmConfig> {
    return new Promise<ILlmConfig>((resolve, reject) => {
      axios
        .get(baseApiUrl)
        .then(res => resolve(res.data))
        .catch(err => reject(err));
    });
  }

  updateConfig(config: ILlmConfig): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .post(baseApiUrl, config)
        .then(res => resolve(res.data))
        .catch(err => reject(err));
    });
  }

  testConfig(): Promise<ILlmTestResult> {
    return new Promise<ILlmTestResult>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/test`)
        .then(res => resolve(res.data))
        .catch(err => reject(err));
    });
  }
}
