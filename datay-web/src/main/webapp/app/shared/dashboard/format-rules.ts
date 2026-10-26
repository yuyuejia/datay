import type { WidgetFormatRule } from './types';

const toNumber = (value: unknown): number | null => {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
};

/** 判断数值是否命中单条条件格式规则。 */
export function matchesFormatRule(rule: WidgetFormatRule, value: unknown): boolean {
  const numeric = toNumber(value);
  if (numeric === null) {
    return false;
  }
  switch (rule.operator) {
    case 'gt':
      return numeric > rule.value;
    case 'gte':
      return numeric >= rule.value;
    case 'lt':
      return numeric < rule.value;
    case 'lte':
      return numeric <= rule.value;
    case 'eq':
      return numeric === rule.value;
    case 'neq':
      return numeric !== rule.value;
    case 'between':
      return rule.value2 !== undefined && numeric >= rule.value && numeric <= rule.value2;
    default:
      return false;
  }
}

/** 按字段与数值解析第一条命中的条件格式规则。 */
export function resolveFormatRule(
  rules: WidgetFormatRule[] | undefined,
  field: string | undefined,
  value: unknown,
): WidgetFormatRule | undefined {
  return rules?.find(rule => (!rule.field || !field || rule.field === field) && matchesFormatRule(rule, value));
}

/** 把条件格式规则转换为单元格内联样式。 */
export function formatRuleStyle(rule: WidgetFormatRule | undefined): Record<string, string | number> {
  if (!rule) {
    return {};
  }
  const style: Record<string, string | number> = {};
  if (rule.color) {
    style.color = rule.color;
  }
  if (rule.background) {
    style.backgroundColor = rule.background;
  }
  if (rule.bold) {
    style.fontWeight = 600;
  }
  return style;
}
