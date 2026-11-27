import type { LinkageSelection } from './types';

/** 未选中数据项 / 系列的置灰透明度。 */
const DIM_OPACITY = 0.22;
const DIM_LINE_OPACITY = 0.16;
const SELECTED_LINE_OPACITY = 0.95;

function dimItem(item: any, opacity: number, selected: boolean, type?: string): any {
  const base = Array.isArray(item) ? { value: item } : item && typeof item === 'object' ? { ...item } : { value: item };
  const result: Record<string, any> = { ...base, itemStyle: { ...(base.itemStyle ?? {}), opacity } };
  // 折线 / 散点的选中点适当放大，避免只靠透明度区分不明显。
  if (selected && (type === 'line' || type === 'scatter')) {
    result.symbolSize = Math.max(Number(base.symbolSize ?? 0), 10);
  }
  return result;
}

/**
 * 联动选中态：把源图表中被点击的数据项保持高亮，其余数据项 / 系列置灰。
 *
 * <p>只处理选中态视觉，不改变数据与交互；取消联动时调用方不再传入 selection 即可恢复。</p>
 */
export function applyLinkageSelection(
  option: Record<string, any> | undefined | null,
  selection?: LinkageSelection | null,
): Record<string, any> | undefined | null {
  if (!option || !selection || selection.dataIndex === undefined) {
    return option;
  }
  const seriesList = Array.isArray(option.series) ? option.series : option.series ? [option.series] : [];
  if (seriesList.length === 0) {
    return option;
  }
  const selectedSeries = selection.seriesIndex ?? 0;
  const selectedIndex = selection.dataIndex;
  const series = seriesList.map((seriesItem: Record<string, any>, seriesIndex: number) => {
    const next: Record<string, any> = { ...seriesItem };
    const isSelectedSeries = seriesIndex === selectedSeries;
    if (Array.isArray(seriesItem.data)) {
      next.data = seriesItem.data.map((item: any, dataIndex: number) => {
        const selected = isSelectedSeries && dataIndex === selectedIndex;
        return dimItem(item, selected ? 1 : DIM_OPACITY, selected, seriesItem.type);
      });
    }
    // 系列级透明度固定为 1：置灰只由数据项 opacity 控制。
    // 若按选中系列保留旧的系列级 0.22，切换系列后会把新选中项一起乘灰。
    next.itemStyle = { ...(seriesItem.itemStyle ?? {}), opacity: 1 };
    if (seriesItem.type === 'line' || seriesItem.type === 'area') {
      next.lineStyle = {
        ...(seriesItem.lineStyle ?? {}),
        opacity: isSelectedSeries ? SELECTED_LINE_OPACITY : DIM_LINE_OPACITY,
      };
      if (seriesItem.areaStyle) {
        next.areaStyle = {
          ...seriesItem.areaStyle,
          opacity: isSelectedSeries ? (seriesItem.areaStyle.opacity ?? 0.25) : 0.05,
        };
      }
    }
    if (seriesItem.label) {
      next.label = { ...seriesItem.label, opacity: isSelectedSeries ? 1 : DIM_OPACITY };
    }
    return next;
  });
  return { ...option, series };
}
