import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import DpTableDetails from './dp-table-details.vue';
import DpTableService from './dp-table.service';
import AlertService from '@/shared/alert/alert.service';

type DpTableDetailsComponentType = InstanceType<typeof DpTableDetails>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const dpTableSample = { id: 123 };

describe('Component Tests', () => {
  let alertService: AlertService;

  afterEach(() => {
    vitest.resetAllMocks();
  });

  describe('DpTable Management Detail Component', () => {
    let dpTableServiceStub: SinonStubbedInstance<DpTableService>;
    let mountOptions: MountingOptions<DpTableDetailsComponentType>['global'];

    beforeEach(() => {
      route = {};
      dpTableServiceStub = sinon.createStubInstance<DpTableService>(DpTableService);

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
          dpTableService: () => dpTableServiceStub,
        },
      };
    });

    describe('Navigate to details', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        dpTableServiceStub.find.resolves(dpTableSample);
        route = {
          params: {
            dpTableId: `${123}`,
          },
        };
        const wrapper = shallowMount(DpTableDetails, { global: mountOptions });
        const comp = wrapper.vm;
        // WHEN
        await comp.$nextTick();

        // THEN
        expect(comp.dpTable).toMatchObject(dpTableSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        dpTableServiceStub.find.resolves(dpTableSample);
        const wrapper = shallowMount(DpTableDetails, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
