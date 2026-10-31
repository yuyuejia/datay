import { describe, expect, it } from 'vitest';

import { TAB_DEFINITIONS, findTabDefinitionByPath } from './tab.config';

describe('Tab Config', () => {
  it('should register the alert center as a closable tab', () => {
    expect(TAB_DEFINITIONS.AlertCenter).toEqual({
      path: '/alert-center',
      title: '告警中心',
      componentName: 'AlertCenter',
      closable: true,
    });
  });

  it('should resolve the alert center route to its tab definition', () => {
    expect(findTabDefinitionByPath('/alert-center')?.name).toBe('AlertCenter');
  });

  it('should register data migration as a closable tab under the integration menu', () => {
    expect(TAB_DEFINITIONS.DataSync).toEqual({
      path: '/data-sync',
      title: '数据迁移',
      componentName: 'DataSync',
      closable: true,
    });
  });

  it('should resolve the data migration route to its tab definition', () => {
    expect(findTabDefinitionByPath('/data-sync')?.name).toBe('DataSync');
  });
});
