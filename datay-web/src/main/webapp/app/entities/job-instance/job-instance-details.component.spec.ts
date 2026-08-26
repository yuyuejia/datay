import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import JobInstanceDetails from './job-instance-details.vue';
import JobInstanceService from './job-instance.service';
import AlertService from '@/shared/alert/alert.service';

type JobInstanceDetailsComponentType = InstanceType<typeof JobInstanceDetails>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const jobInstanceSample = { id: 123 };

describe('Component Tests', () => {
  let alertService: AlertService;

  afterEach(() => {
    vitest.resetAllMocks();
  });

  describe('JobInstance Management Detail Component', () => {
    let jobInstanceServiceStub: SinonStubbedInstance<JobInstanceService>;
    let mountOptions: MountingOptions<JobInstanceDetailsComponentType>['global'];

    beforeEach(() => {
      route = {};
      jobInstanceServiceStub = sinon.createStubInstance<JobInstanceService>(JobInstanceService);

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
          jobInstanceService: () => jobInstanceServiceStub,
        },
      };
    });

    describe('Navigate to details', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        jobInstanceServiceStub.find.resolves(jobInstanceSample);
        route = {
          params: {
            jobInstanceId: `${123}`,
          },
        };
        const wrapper = shallowMount(JobInstanceDetails, { global: mountOptions });
        const comp = wrapper.vm;
        // WHEN
        await comp.$nextTick();

        // THEN
        expect(comp.jobInstance).toMatchObject(jobInstanceSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        jobInstanceServiceStub.find.resolves(jobInstanceSample);
        const wrapper = shallowMount(JobInstanceDetails, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
