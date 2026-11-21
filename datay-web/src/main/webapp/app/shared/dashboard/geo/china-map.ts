import { echarts } from '../echarts';
import chinaGeoJson from './china.json';

/** 内置中国省级地图名称。 */
export const CHINA_MAP_NAME = 'china';

const provinceNames = Array.from(
  new Set(
    (chinaGeoJson as any).features
      .map((feature: any) => String(feature?.properties?.name ?? '').trim())
      .filter((name: string) => name.length > 0),
  ),
);

const provinceNameSet = new Set(provinceNames);

/** 省级名称按长度倒序，用于前缀匹配「广东省广州市」这类完整地址。 */
const provinceNamesByLength = [...provinceNames].sort((left, right) => right.length - left.length);

const PROVINCE_SUFFIX_PATTERN = /(壮族自治区|回族自治区|维吾尔自治区|特别行政区|自治区|省|市)$/;

echarts.registerMap(CHINA_MAP_NAME, chinaGeoJson as any);

/**
 * 把数据集中的区域名称归一化为中国省级地图名称。
 *
 * <p>支持「北京市」「广东省」「广西壮族自治区」「上海市浦东新区」等常见写法，
 * 无法匹配省级行政区时返回 undefined，由调用方忽略该行。
 */
export function matchChinaProvinceName(raw: unknown): string | undefined {
  if (raw === null || raw === undefined) {
    return undefined;
  }
  let name = String(raw).trim().replace(/\s+/g, '');
  if (!name) {
    return undefined;
  }
  if (name.startsWith('中国')) {
    name = name.slice(2);
  }
  if (provinceNameSet.has(name)) {
    return name;
  }
  const stripped = name.replace(PROVINCE_SUFFIX_PATTERN, '');
  if (provinceNameSet.has(stripped)) {
    return stripped;
  }
  return provinceNamesByLength.find(province => name.startsWith(province));
}

/** 当前内置地图支持的省级区域名称。 */
export function listChinaProvinceNames(): string[] {
  return [...provinceNames];
}
