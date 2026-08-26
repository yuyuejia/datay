import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import dayjs from 'dayjs';
import JobInstanceUpdate from './job-instance-update.vue';
import JobInstanceService from './job-instance.service';
import { DATE_TIME_LONG_FORMAT } from '@/shared/composables/date-format';
import AlertService from '@/shared/alert/alert.service';

type JobInstanceUpdateComponentType = InstanceType<typeof JobInstanceUpdate>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const jobInstanceSample = { id: 123 };

describe('Component Tests', () => {
  let mountOptions: MountingOptions<JobInstanceUpdateComponentType>['global'];
  let alertService: AlertService;

  describe('JobInstance Management Update Component', () => {
    let comp: JobInstanceUpdateComponentType;
    let jobInstanceServiceStub: SinonStubbedInstance<JobInstanceService>;

    beforeEach(() => {
      route = {};
      jobInstanceServiceStub = sinon.createStubInstance<JobInstanceService>(JobInstanceService);
      jobInstanceServiceStub.retrieve.onFirstCall().resolves(Promise.resolve([]));

      alertService = new AlertService({
        bvToast: {
          toast: vitest.fn(),
        } as any,
      });

      mountOptions = {
        stubs: {
          'font-awesome-icon': true,
          'b-input-group': true,
          'b-input-group-prepend': true,
          'b-form-datepicker': true,
          'b-form-input': true,
        },
        provide: {
          alertService,
          jobInstanceService: () => jobInstanceServiceStub,
        },
      };
    });

    afterEach(() => {
      vitest.resetAllMocks();
    });

    describe('load', () => {
      beforeEach(() => {
        const wrapper = shallowMount(JobInstanceUpdate, { global: mountOptions });
        comp = wrapper.vm;
      });
      it('Should convert date from string', () => {
        // GIVEN
        const date = new Date('2019-10-15T11:42:02Z');

        // WHEN
        const convertedDate = comp.convertDateTimeFromServer(date);

        // THEN
        expect(convertedDate).toEqual(dayjs(date).format(DATE_TIME_LONG_FORMAT));
      });

      it('Should not convert date if date is not present', () => {
        expect(comp.convertDateTimeFromServer(null)).toBeNull();
      });
    });

    describe('save', () => {
      it('Should call update service on save for existing entity', async () => {
        // GIVEN
        const wrapper = shallowMount(JobInstanceUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.jobInstance = jobInstanceSample;
        jobInstanceServiceStub.update.resolves(jobInstanceSample);

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(jobInstanceServiceStub.update.calledWith(jobInstanceSample)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });

      it('Should call create service on save for new entity', async () => {
        // GIVEN
        const entity = {};
        jobInstanceServiceStub.create.resolves(entity);
        const wrapper = shallowMount(JobInstanceUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.jobInstance = entity;

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(jobInstanceServiceStub.create.calledWith(entity)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });
    });

    describe('Before route enter', () => {
      it('Should retrieve data', async () => {
        // GIVEN
        jobInstanceServiceStub.find.resolves(jobInstanceSample);
        jobInstanceServiceStub.retrieve.resolves([jobInstanceSample]);

        // WHEN
        route = {
          params: {
            jobInstanceId: `${jobInstanceSample.id}`,
          },
        };
        const wrapper = shallowMount(JobInstanceUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(comp.jobInstance).toMatchObject(jobInstanceSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        jobInstanceServiceStub.find.resolves(jobInstanceSample);
        const wrapper = shallowMount(JobInstanceUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
