import { formatRuleStyle, matchesFormatRule, resolveFormatRule } from './format-rules';
import type { WidgetFormatRule } from './types';

const rule = (overrides: Partial<WidgetFormatRule>): WidgetFormatRule => ({
  operator: 'gt',
  value: 100,
  ...overrides,
});

describe('format-rules', () => {
  it('支持比较与区间规则', () => {
    expect(matchesFormatRule(rule({ operator: 'gt', value: 100 }), 120)).toBe(true);
    expect(matchesFormatRule(rule({ operator: 'gte', value: 120 }), 120)).toBe(true);
    expect(matchesFormatRule(rule({ operator: 'lt', value: 100 }), 80)).toBe(true);
    expect(matchesFormatRule(rule({ operator: 'eq', value: 100 }), 100)).toBe(true);
    expect(matchesFormatRule(rule({ operator: 'neq', value: 100 }), 100)).toBe(false);
    expect(matchesFormatRule(rule({ operator: 'between', value: 10, value2: 20 }), 15)).toBe(true);
    expect(matchesFormatRule(rule({ operator: 'between', value: 10, value2: 20 }), 25)).toBe(false);
  });

  it('空值与非法数值不命中规则', () => {
    expect(matchesFormatRule(rule({}), null)).toBe(false);
    expect(matchesFormatRule(rule({}), 'not-a-number')).toBe(false);
  });

  it('按字段解析第一条命中规则并生成样式', () => {
    const rules = [
      rule({ field: 'amount', operator: 'gt', value: 100, color: '#67c23a', bold: true }),
      rule({ field: 'quantity', operator: 'gt', value: 10, background: '#fef0f0' }),
    ];

    expect(resolveFormatRule(rules, 'amount', 120)).toMatchObject({ field: 'amount' });
    expect(resolveFormatRule(rules, 'quantity', 5)).toBeUndefined();
    expect(resolveFormatRule(rules, undefined, 120)).toMatchObject({ field: 'amount' });
    expect(formatRuleStyle(rules[0])).toEqual({ color: '#67c23a', fontWeight: 600 });
  });
});
