import { type MountingOptions, flushPromises, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';

import ETLTask from './etl-task.vue';
import ETLTaskService from './etl-task.service';
import ETLTaskDirectoryService from './etl-task-directory.service';
import AlertService from '@/shared/alert/alert.service';

type ETLTaskComponentType = InstanceType<typeof ETLTask>;

const bModalStub = {
  render: () => {},
  methods: {
    hide: () => {},
    show: () => {},
  },
};

describe('Component Tests', () => {
  let alertService: AlertService;

  describe('ETLTask Management Component', () => {
    let eTLTaskServiceStub: SinonStubbedInstance<ETLTaskService>;
    let etlTaskDirectoryServiceStub: SinonStubbedInstance<ETLTaskDirectoryService>;
    let mountOptions: MountingOptions<ETLTaskComponentType>['global'];

    beforeEach(() => {
      eTLTaskServiceStub = sinon.createStubInstance<ETLTaskService>(ETLTaskService);
      eTLTaskServiceStub.retrieve.resolves({ headers: {} });
      etlTaskDirectoryServiceStub = sinon.createStubInstance<ETLTaskDirectoryService>(ETLTaskDirectoryService);
      etlTaskDirectoryServiceStub.retrieve.resolves({ data: [] });

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
          eTLTaskService: () => eTLTaskServiceStub,
          etlTaskDirectoryService: () => etlTaskDirectoryServiceStub,
        },
      };
    });

    describe('Mount', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        eTLTaskServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        const wrapper = shallowMount(ETLTask, { global: mountOptions });
        const comp = wrapper.vm;
        await flushPromises();
        await comp.$nextTick();

        // THEN
        expect(eTLTaskServiceStub.retrieve.calledOnce).toBeTruthy();
        expect(comp.eTLTasks[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should use the default update time descending sort', async () => {
        // WHEN
        const wrapper = shallowMount(ETLTask, { global: mountOptions });
        const comp = wrapper.vm;
        await flushPromises();
        await comp.$nextTick();

        // THEN
        expect(eTLTaskServiceStub.retrieve.lastCall.firstArg).toMatchObject({
          sort: ['updateTime,desc', 'id'],
        });
      });
    });
    describe('Handles', () => {
      let comp: ETLTaskComponentType;

      beforeEach(async () => {
        const wrapper = shallowMount(ETLTask, { global: mountOptions });
        comp = wrapper.vm;
        await flushPromises();
        await comp.$nextTick();
        eTLTaskServiceStub.retrieve.reset();
        eTLTaskServiceStub.retrieve.resolves({ headers: {}, data: [] });
      });

      it('should load a page', async () => {
        // GIVEN
        eTLTaskServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        comp.page = 2;
        await flushPromises();
        await comp.$nextTick();

        // THEN
        expect(eTLTaskServiceStub.retrieve.called).toBeTruthy();
        expect(comp.eTLTasks[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should not load a page if the page is the same as the previous page', () => {
        // WHEN
        comp.page = 1;

        // THEN
        expect(eTLTaskServiceStub.retrieve.called).toBeFalsy();
      });

      it('should re-initialize the page', async () => {
        // GIVEN
        comp.page = 2;
        await flushPromises();
        await comp.$nextTick();
        eTLTaskServiceStub.retrieve.reset();
        eTLTaskServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        comp.clear();
        await flushPromises();
        await comp.$nextTick();

        // THEN
        expect(comp.page).toEqual(1);
        expect(eTLTaskServiceStub.retrieve.callCount).toEqual(1);
        expect(comp.eTLTasks[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should pass the selected directory when filtering tasks', async () => {
        // GIVEN
        eTLTaskServiceStub.retrieve.resolves({ headers: {}, data: [] });

        // WHEN
        comp.selectedDirectoryId = 'dir-1';
        await flushPromises();
        await comp.$nextTick();

        // THEN
        expect(eTLTaskServiceStub.retrieve.lastCall.firstArg).toMatchObject({
          directoryId: 'dir-1',
          type: 'BATCH',
        });
      });

      it('should calculate the sort attribute for a non-id attribute', async () => {
        // WHEN
        comp.propOrder = 'name';
        await flushPromises();
        await comp.$nextTick();

        // THEN
        expect(eTLTaskServiceStub.retrieve.lastCall.firstArg).toMatchObject({
          sort: ['name,desc', 'id'],
        });
      });

      it('Should call delete service on confirmDelete', async () => {
        // GIVEN
        eTLTaskServiceStub.delete.resolves({});

        // WHEN
        comp.prepareRemove({ id: 123 });

        comp.removeETLTask();
        await flushPromises();
        await comp.$nextTick(); // clear components

        // THEN
        expect(eTLTaskServiceStub.delete.called).toBeTruthy();

        // THEN
        await flushPromises();
        await comp.$nextTick(); // handle component clear watch
        expect(eTLTaskServiceStub.retrieve.callCount).toEqual(1);
      });
    });
  });
});
