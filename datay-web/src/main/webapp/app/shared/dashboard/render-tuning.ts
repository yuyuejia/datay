/** 图表明暗/尺寸修正相关的纯函数与常量，便于单测覆盖。 */

/** 增量更新时按组件类型替换的配置项，避免切图表后残留旧 series/坐标轴。 */
export const CHART_REPLACE_MERGE = ['series', 'xAxis', 'yAxis', 'grid', 'geo', 'visualMap', 'radar'];

/** setOption 的合并参数：保留动画与过渡，同时按需替换结构性配置。 */
export function chartSetOptionOptions(): Record<string, any> {
  return {
    notMerge: false,
    lazyUpdate: true,
    replaceMerge: CHART_REPLACE_MERGE,
  };
}
