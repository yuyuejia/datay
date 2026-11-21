import { echarts } from '../echarts';
import { CHINA_MAP_NAME, listChinaProvinceNames, matchChinaProvinceName } from './china-map';

describe('china map region names', () => {
  it('注册中国地图', () => {
    expect(echarts.getMap(CHINA_MAP_NAME)).toBeTruthy();
  });

  it.each([
    ['北京', '北京'],
    ['北京市', '北京'],
    ['广东省', '广东'],
    ['广西壮族自治区', '广西'],
    ['内蒙古自治区', '内蒙古'],
    ['宁夏回族自治区', '宁夏'],
    ['新疆维吾尔自治区', '新疆'],
    ['西藏自治区', '西藏'],
    ['香港特别行政区', '香港'],
    ['上海市浦东新区', '上海'],
    ['广东省广州市', '广东'],
  ])('把 %s 归一化为 %s', (raw, expected) => {
    expect(matchChinaProvinceName(raw)).toBe(expected);
  });

  it('忽略无法匹配的地址', () => {
    expect(matchChinaProvinceName('未知地区')).toBeUndefined();
    expect(matchChinaProvinceName('')).toBeUndefined();
    expect(matchChinaProvinceName(null)).toBeUndefined();
  });

  it('暴露内置省级区域名称', () => {
    const names = listChinaProvinceNames();
    expect(names).toContain('北京');
    expect(names).toContain('广东');
    expect(names.length).toBeGreaterThanOrEqual(34);
  });
});
