import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import ETLEdgeDetails from './etl-edge-details.vue';
import ETLEdgeService from './etl-edge.service';
import AlertService from '@/shared/alert/alert.service';

type ETLEdgeDetailsComponentType = InstanceType<typeof ETLEdgeDetails>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const eTLEdgeSample = { id: 123 };

describe('Component Tests', () => {
  let alertService: AlertService;

  afterEach(() => {
    vitest.resetAllMocks();
  });

  describe('ETLEdge Management Detail Component', () => {
    let eTLEdgeServiceStub: SinonStubbedInstance<ETLEdgeService>;
    let mountOptions: MountingOptions<ETLEdgeDetailsComponentType>['global'];

    beforeEach(() => {
      route = {};
      eTLEdgeServiceStub = sinon.createStubInstance<ETLEdgeService>(ETLEdgeService);

      alertService = new AlertService();

      mountOptions = {
        stubs: {
          'font-awesome-icon': true,
          'router-link': true,
        },
        provide: {
          alertService,
          eTLEdgeService: () => eTLEdgeServiceStub,
        },
      };
    });

    describe('Navigate to details', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        eTLEdgeServiceStub.find.resolves(eTLEdgeSample);
        route = {
          params: {
            eTLEdgeId: `${123}`,
          },
        };
        const wrapper = shallowMount(ETLEdgeDetails, { global: mountOptions });
        const comp = wrapper.vm;
        // WHEN
        await comp.$nextTick();

        // THEN
        expect(comp.eTLEdge).toMatchObject(eTLEdgeSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        eTLEdgeServiceStub.find.resolves(eTLEdgeSample);
        const wrapper = shallowMount(ETLEdgeDetails, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
