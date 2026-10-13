import { vitest } from 'vitest';
import { shallowMount } from '@vue/test-utils';
import axios from 'axios';
import sinon from 'sinon';
import { createTestingPinia } from '@pinia/testing';
import { type RouteLocation } from 'vue-router';

import Settings from './settings.vue';
import { useStore } from '@/store';
import { EMAIL_ALREADY_USED_TYPE } from '@/constants';

type SettingsComponentType = InstanceType<typeof Settings>;

let route: Partial<RouteLocation>;
const routerReplaceMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ replace: routerReplaceMock }),
}));

const pinia = createTestingPinia({ stubActions: false });

const store = useStore();

const axiosStub = {
  get: sinon.stub(axios, 'get'),
  post: sinon.stub(axios, 'post'),
};

describe('Settings Component', () => {
  let settings: SettingsComponentType;
  const account = {
    firstName: 'John',
    lastName: 'Doe',
    email: 'john.doe@jhipster.org',
  };

  beforeEach(() => {
    axiosStub.get.resolves({});
    axiosStub.post.reset();
    routerReplaceMock.mockReset();

    route = { path: '/account/settings', query: {} };
    store.setAuthentication(account);
    const wrapper = shallowMount(Settings, {
      global: {
        plugins: [pinia],
      },
    });
    settings = wrapper.vm;
  });

  it('should open the account tab by default', () => {
    expect(settings.activeTab).toBe('account');
  });

  it('should open the password tab when asked through the tab query parameter', () => {
    // WHEN
    route = { path: '/account/settings', query: { tab: 'password' } };
    const wrapper = shallowMount(Settings, {
      global: {
        plugins: [pinia],
      },
    });

    // THEN
    expect(wrapper.vm.activeTab).toBe('password');
  });

  it('should send the current identity upon save', async () => {
    // GIVEN
    axiosStub.post.resolves({});

    // WHEN
    await settings.save();
    await settings.$nextTick();

    // THEN
    expect(axiosStub.post.calledWith('api/account', account)).toBeTruthy();
  });

  it('should notify of success upon successful save', async () => {
    // GIVEN
    axiosStub.post.resolves(account);

    // WHEN
    await settings.save();
    await settings.$nextTick();

    // THEN
    expect(settings.error).toBeNull();
    expect(settings.success).toBe('OK');
  });

  it('should notify of error upon failed save', async () => {
    // GIVEN
    const error = { response: { status: 417 } };
    axiosStub.post.rejects(error);

    // WHEN
    await settings.save();
    await settings.$nextTick();

    // THEN
    expect(settings.error).toEqual('ERROR');
    expect(settings.errorEmailExists).toBeNull();
    expect(settings.success).toBeNull();
  });

  it('should notify of error upon error 400', async () => {
    // GIVEN
    const error = { response: { status: 400, data: {} } };
    axiosStub.post.rejects(error);

    // WHEN
    await settings.save();
    await settings.$nextTick();

    // THEN
    expect(settings.error).toEqual('ERROR');
    expect(settings.errorEmailExists).toBeNull();
    expect(settings.success).toBeNull();
  });

  it('should notify of error upon email already used', async () => {
    // GIVEN
    const error = { response: { status: 400, data: { type: EMAIL_ALREADY_USED_TYPE } } };
    axiosStub.post.rejects(error);

    // WHEN
    await settings.save();
    await settings.$nextTick();

    // THEN
    expect(settings.errorEmailExists).toEqual('ERROR');
    expect(settings.error).toBeNull();
    expect(settings.success).toBeNull();
  });
});
