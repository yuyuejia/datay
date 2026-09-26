import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import dayjs from 'dayjs';
import JobUpdate from './job-update.vue';
import JobService from './job.service';
import { DATE_TIME_LONG_FORMAT } from '@/shared/composables/date-format';
import AlertService from '@/shared/alert/alert.service';

type JobUpdateComponentType = InstanceType<typeof JobUpdate>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const jobSample = { id: 123 };

describe('Component Tests', () => {
  let mountOptions: MountingOptions<JobUpdateComponentType>['global'];
  let alertService: AlertService;

  describe('Job Management Update Component', () => {
    let comp: JobUpdateComponentType;
    let jobServiceStub: SinonStubbedInstance<JobService>;

    beforeEach(() => {
      route = {};
      jobServiceStub = sinon.createStubInstance<JobService>(JobService);
      jobServiceStub.retrieve.onFirstCall().resolves(Promise.resolve([]));

      alertService = new AlertService();

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
          jobService: () => jobServiceStub,
        },
      };
    });

    afterEach(() => {
      vitest.resetAllMocks();
    });

    describe('load', () => {
      beforeEach(() => {
        const wrapper = shallowMount(JobUpdate, { global: mountOptions });
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
        const wrapper = shallowMount(JobUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.job = jobSample;
        jobServiceStub.update.resolves(jobSample);

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(jobServiceStub.update.calledWith(jobSample)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });

      it('Should call create service on save for new entity', async () => {
        // GIVEN
        const entity = {};
        jobServiceStub.create.resolves(entity);
        const wrapper = shallowMount(JobUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.job = entity;

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(jobServiceStub.create.calledWith(entity)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });
    });

    describe('Before route enter', () => {
      it('Should retrieve data', async () => {
        // GIVEN
        jobServiceStub.find.resolves(jobSample);
        jobServiceStub.retrieve.resolves([jobSample]);

        // WHEN
        route = {
          params: {
            jobId: `${jobSample.id}`,
          },
        };
        const wrapper = shallowMount(JobUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(comp.job).toMatchObject(jobSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        jobServiceStub.find.resolves(jobSample);
        const wrapper = shallowMount(JobUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
