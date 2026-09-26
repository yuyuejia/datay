import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';

import JobDepend from './job-depend.vue';
import JobDependService from './job-depend.service';
import AlertService from '@/shared/alert/alert.service';

type JobDependComponentType = InstanceType<typeof JobDepend>;

const bModalStub = {
  render: () => {},
  methods: {
    hide: () => {},
    show: () => {},
  },
};

describe('Component Tests', () => {
  let alertService: AlertService;

  describe('JobDepend Management Component', () => {
    let jobDependServiceStub: SinonStubbedInstance<JobDependService>;
    let mountOptions: MountingOptions<JobDependComponentType>['global'];

    beforeEach(() => {
      jobDependServiceStub = sinon.createStubInstance<JobDependService>(JobDependService);
      jobDependServiceStub.retrieve.resolves({ headers: {} });

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
          jobDependService: () => jobDependServiceStub,
        },
      };
    });

    describe('Mount', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        jobDependServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        const wrapper = shallowMount(JobDepend, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(jobDependServiceStub.retrieve.calledOnce).toBeTruthy();
        expect(comp.jobDepends[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should calculate the sort attribute for an id', async () => {
        // WHEN
        const wrapper = shallowMount(JobDepend, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(jobDependServiceStub.retrieve.lastCall.firstArg).toMatchObject({
          sort: ['id,asc'],
        });
      });
    });
    describe('Handles', () => {
      let comp: JobDependComponentType;

      beforeEach(async () => {
        const wrapper = shallowMount(JobDepend, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();
        jobDependServiceStub.retrieve.reset();
        jobDependServiceStub.retrieve.resolves({ headers: {}, data: [] });
      });

      it('should load a page', async () => {
        // GIVEN
        jobDependServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        comp.page = 2;
        await comp.$nextTick();

        // THEN
        expect(jobDependServiceStub.retrieve.called).toBeTruthy();
        expect(comp.jobDepends[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should not load a page if the page is the same as the previous page', () => {
        // WHEN
        comp.page = 1;

        // THEN
        expect(jobDependServiceStub.retrieve.called).toBeFalsy();
      });

      it('should re-initialize the page', async () => {
        // GIVEN
        comp.page = 2;
        await comp.$nextTick();
        jobDependServiceStub.retrieve.reset();
        jobDependServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        comp.clear();
        await comp.$nextTick();

        // THEN
        expect(comp.page).toEqual(1);
        expect(jobDependServiceStub.retrieve.callCount).toEqual(1);
        expect(comp.jobDepends[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should calculate the sort attribute for a non-id attribute', async () => {
        // WHEN
        comp.propOrder = 'name';
        await comp.$nextTick();

        // THEN
        expect(jobDependServiceStub.retrieve.lastCall.firstArg).toMatchObject({
          sort: ['name,asc', 'id'],
        });
      });

      it('Should call delete service on confirmDelete', async () => {
        // GIVEN
        jobDependServiceStub.delete.resolves({});

        // WHEN
        comp.prepareRemove({ id: 123 });

        comp.removeJobDepend();
        await comp.$nextTick(); // clear components

        // THEN
        expect(jobDependServiceStub.delete.called).toBeTruthy();

        // THEN
        await comp.$nextTick(); // handle component clear watch
        expect(jobDependServiceStub.retrieve.callCount).toEqual(1);
      });
    });
  });
});
