import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import ETLComponentDetails from './etl-component-details.vue';
import ETLComponentService from './etl-component.service';
import AlertService from '@/shared/alert/alert.service';

type ETLComponentDetailsComponentType = InstanceType<typeof ETLComponentDetails>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const eTLComponentSample = { id: 123 };

describe('Component Tests', () => {
  let alertService: AlertService;

  afterEach(() => {
    vitest.resetAllMocks();
  });

  describe('ETLComponent Management Detail Component', () => {
    let eTLComponentServiceStub: SinonStubbedInstance<ETLComponentService>;
    let mountOptions: MountingOptions<ETLComponentDetailsComponentType>['global'];

    beforeEach(() => {
      route = {};
      eTLComponentServiceStub = sinon.createStubInstance<ETLComponentService>(ETLComponentService);

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
          eTLComponentService: () => eTLComponentServiceStub,
        },
      };
    });

    describe('Navigate to details', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        eTLComponentServiceStub.find.resolves(eTLComponentSample);
        route = {
          params: {
            eTLComponentId: `${123}`,
          },
        };
        const wrapper = shallowMount(ETLComponentDetails, { global: mountOptions });
        const comp = wrapper.vm;
        // WHEN
        await comp.$nextTick();

        // THEN
        expect(comp.eTLComponent).toMatchObject(eTLComponentSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        eTLComponentServiceStub.find.resolves(eTLComponentSample);
        const wrapper = shallowMount(ETLComponentDetails, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
