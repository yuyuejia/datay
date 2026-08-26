import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import ETLNodeDetails from './etl-node-details.vue';
import ETLNodeService from './etl-node.service';
import AlertService from '@/shared/alert/alert.service';

type ETLNodeDetailsComponentType = InstanceType<typeof ETLNodeDetails>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const eTLNodeSample = { id: 123 };

describe('Component Tests', () => {
  let alertService: AlertService;

  afterEach(() => {
    vitest.resetAllMocks();
  });

  describe('ETLNode Management Detail Component', () => {
    let eTLNodeServiceStub: SinonStubbedInstance<ETLNodeService>;
    let mountOptions: MountingOptions<ETLNodeDetailsComponentType>['global'];

    beforeEach(() => {
      route = {};
      eTLNodeServiceStub = sinon.createStubInstance<ETLNodeService>(ETLNodeService);

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
          eTLNodeService: () => eTLNodeServiceStub,
        },
      };
    });

    describe('Navigate to details', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        eTLNodeServiceStub.find.resolves(eTLNodeSample);
        route = {
          params: {
            eTLNodeId: `${123}`,
          },
        };
        const wrapper = shallowMount(ETLNodeDetails, { global: mountOptions });
        const comp = wrapper.vm;
        // WHEN
        await comp.$nextTick();

        // THEN
        expect(comp.eTLNode).toMatchObject(eTLNodeSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        eTLNodeServiceStub.find.resolves(eTLNodeSample);
        const wrapper = shallowMount(ETLNodeDetails, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
