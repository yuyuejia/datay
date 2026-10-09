import axios from 'axios';

const baseApiUrl = 'api/data-sources';

export default class DataQueryService {
  executeQuery(dataSourceId: string, sql: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}/${dataSourceId}/query`, { sql })
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }
}