import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import ServiceConfigDetails from './service-config-details.vue';
import ServiceConfigService from './service-config.service';
import AlertService from '@/shared/alert/alert.service';

type ServiceConfigDetailsComponentType = InstanceType<typeof ServiceConfigDetails>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const serviceConfigSample = { id: 123 };

describe('Component Tests', () => {
  let alertService: AlertService;

  afterEach(() => {
    vitest.resetAllMocks();
  });

  describe('ServiceConfig Management Detail Component', () => {
    let serviceConfigServiceStub: SinonStubbedInstance<ServiceConfigService>;
    let mountOptions: MountingOptions<ServiceConfigDetailsComponentType>['global'];

    beforeEach(() => {
      route = {};
      serviceConfigServiceStub = sinon.createStubInstance<ServiceConfigService>(ServiceConfigService);

      alertService = new AlertService({
        bvToast: {
          toast: vitest.fn(),
        } as any,
      });

      mountOptions = {
        stubs: {
          'font-awesome-icon': true,
          'router-link': true,
        },
        provide: {
          alertService,
          serviceConfigService: () => serviceConfigServiceStub,
        },
      };
    });

    describe('Navigate to details', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        serviceConfigServiceStub.find.resolves(serviceConfigSample);
        route = {
          params: {
            serviceConfigId: `${123}`,
          },
        };
        const wrapper = shallowMount(ServiceConfigDetails, { global: mountOptions });
        const comp = wrapper.vm;
        // WHEN
        await comp.$nextTick();

        // THEN
        expect(comp.serviceConfig).toMatchObject(serviceConfigSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        serviceConfigServiceStub.find.resolves(serviceConfigSample);
        const wrapper = shallowMount(ServiceConfigDetails, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
