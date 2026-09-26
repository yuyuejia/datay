import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';

import JobInstance from './job-instance.vue';
import JobInstanceService from './job-instance.service';
import AlertService from '@/shared/alert/alert.service';

type JobInstanceComponentType = InstanceType<typeof JobInstance>;

const bModalStub = {
  render: () => {},
  methods: {
    hide: () => {},
    show: () => {},
  },
};

describe('Component Tests', () => {
  let alertService: AlertService;

  describe('JobInstance Management Component', () => {
    let jobInstanceServiceStub: SinonStubbedInstance<JobInstanceService>;
    let mountOptions: MountingOptions<JobInstanceComponentType>['global'];

    beforeEach(() => {
      jobInstanceServiceStub = sinon.createStubInstance<JobInstanceService>(JobInstanceService);
      jobInstanceServiceStub.retrieve.resolves({ headers: {} });

      alertService = new AlertService();

      mountOptions = {
        stubs: {
          jhiItemCount: true,
          bPagination: true,
          AppModal: bModalStub as any,
          'font-awesome-icon': true,
          'b-badge': true,
          'jhi-sort-indicator': true,
          'b-button': true,
          'router-link': true,
        },
        provide: {
          alertService,
          jobInstanceService: () => jobInstanceServiceStub,
        },
      };
    });

    describe('Mount', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        jobInstanceServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        const wrapper = shallowMount(JobInstance, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(jobInstanceServiceStub.retrieve.calledOnce).toBeTruthy();
        expect(comp.jobInstances[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should calculate the sort attribute for an id', async () => {
        // WHEN
        const wrapper = shallowMount(JobInstance, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(jobInstanceServiceStub.retrieve.lastCall.firstArg).toMatchObject({
          sort: ['id,asc'],
        });
      });
    });
    describe('Handles', () => {
      let comp: JobInstanceComponentType;

      beforeEach(async () => {
        const wrapper = shallowMount(JobInstance, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();
        jobInstanceServiceStub.retrieve.reset();
        jobInstanceServiceStub.retrieve.resolves({ headers: {}, data: [] });
      });

      it('should load a page', async () => {
        // GIVEN
        jobInstanceServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        comp.page = 2;
        await comp.$nextTick();

        // THEN
        expect(jobInstanceServiceStub.retrieve.called).toBeTruthy();
        expect(comp.jobInstances[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should not load a page if the page is the same as the previous page', () => {
        // WHEN
        comp.page = 1;

        // THEN
        expect(jobInstanceServiceStub.retrieve.called).toBeFalsy();
      });

      it('should re-initialize the page', async () => {
        // GIVEN
        comp.page = 2;
        await comp.$nextTick();
        jobInstanceServiceStub.retrieve.reset();
        jobInstanceServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        comp.clear();
        await comp.$nextTick();

        // THEN
        expect(comp.page).toEqual(1);
        expect(jobInstanceServiceStub.retrieve.callCount).toEqual(1);
        expect(comp.jobInstances[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should calculate the sort attribute for a non-id attribute', async () => {
        // WHEN
        comp.propOrder = 'name';
        await comp.$nextTick();

        // THEN
        expect(jobInstanceServiceStub.retrieve.lastCall.firstArg).toMatchObject({
          sort: ['name,asc', 'id'],
        });
      });

      it('Should call delete service on confirmDelete', async () => {
        // GIVEN
        jobInstanceServiceStub.delete.resolves({});

        // WHEN
        comp.prepareRemove({ id: 123 });

        comp.removeJobInstance();
        await comp.$nextTick(); // clear components

        // THEN
        expect(jobInstanceServiceStub.delete.called).toBeTruthy();

        // THEN
        await comp.$nextTick(); // handle component clear watch
        expect(jobInstanceServiceStub.retrieve.callCount).toEqual(1);
      });
    });
  });
});
