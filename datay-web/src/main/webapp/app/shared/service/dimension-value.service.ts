import axios from "axios";

export interface IDimensionValuePage {
  values: string[];
  total: number;
  page: number;
  size: number;
}

/**
 * 维度取值查询服务：按维度模型字段分页查询去重取值。
 */
export default class DimensionValueService {
  pageValues(
    dimensionModelId: string,
    fieldName: string,
    keyword?: string,
    page = 1,
    size = 20,
  ): Promise<IDimensionValuePage> {
    return axios
      .get(`api/data-models/${dimensionModelId}/values`, {
        params: { fieldName, keyword, page, size },
      })
      .then((res) => res.data);
  }
}
