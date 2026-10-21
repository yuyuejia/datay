import { flushPromises, shallowMount } from '@vue/test-utils';
import sinon from 'sinon';

import AlertCenter from './alert-center.vue';
import AlertCenterService from './alert-center.service';
import AlertService from '@/shared/alert/alert.service';

type AlertCenterComponentType = InstanceType<typeof AlertCenter>;

describe('AlertCenter Component', () => {
  let sandbox: sinon.SinonSandbox;
  let getSummary: sinon.SinonStub;
  let retrieveEvents: sinon.SinonStub;
  let retrieveRules: sinon.SinonStub;
  let retrieveChannels: sinon.SinonStub;

  beforeEach(() => {
    sandbox = sinon.createSandbox();
    getSummary = sandbox.stub(AlertCenterService.prototype, 'getSummary').resolves({ firing: 1, acknowledged: 2, resolved: 3 });
    retrieveEvents = sandbox
      .stub(AlertCenterService.prototype, 'retrieveEvents')
      .resolves({ headers: { 'x-total-count': '1' }, data: [{ id: 'event-1' }] });
    retrieveRules = sandbox.stub(AlertCenterService.prototype, 'retrieveRules').resolves([{ id: 'rule-1' }]);
    retrieveChannels = sandbox.stub(AlertCenterService.prototype, 'retrieveChannels').resolves([{ id: 'channel-1' }]);
  });

  afterEach(() => {
    sandbox.restore();
  });

  it('should load summary, events, rules and channels on mount', async () => {
    const wrapper = shallowMount(AlertCenter, {
      global: {
        provide: {
          alertService: new AlertService(),
        },
        stubs: {
          'font-awesome-icon': true,
          'el-table-column': true,
        },
      },
    });
    const component = wrapper.vm as AlertCenterComponentType;

    await flushPromises();

    expect(getSummary.calledOnce).toBe(true);
    expect(retrieveEvents.calledOnce).toBe(true);
    expect(retrieveRules.calledOnce).toBe(true);
    expect(retrieveChannels.calledOnce).toBe(true);
    expect(component.summary).toEqual({ firing: 1, acknowledged: 2, resolved: 3 });
    expect(component.events).toEqual([{ id: 'event-1' }]);
    expect(component.eventTotal).toBe(1);
  });
});
