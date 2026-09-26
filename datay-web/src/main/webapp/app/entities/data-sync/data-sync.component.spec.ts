import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';

import DataSync from './data-sync.vue';
import DataSyncService from './data-sync.service';
import AlertService from '@/shared/alert/alert.service';

type DataSyncComponentType = InstanceType<typeof DataSync>;

const bModalStub = {
  render: () => {},
  methods: {
    hide: () => {},
    show: () => {},
  },
};

describe('Component Tests', () => {
  let alertService: AlertService;

  describe('DataSync Management Component', () => {
    let dataSyncServiceStub: SinonStubbedInstance<DataSyncService>;
    let mountOptions: MountingOptions<DataSyncComponentType>['global'];

    beforeEach(() => {
      dataSyncServiceStub = sinon.createStubInstance<DataSyncService>(DataSyncService);
      dataSyncServiceStub.retrieve.resolves({ headers: {} });

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
          dataSyncService: () => dataSyncServiceStub,
        },
      };
    });

    describe('Mount', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        dataSyncServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        const wrapper = shallowMount(DataSync, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(dataSyncServiceStub.retrieve.calledOnce).toBeTruthy();
        expect(comp.dataSyncs[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should calculate the sort attribute for an id', async () => {
        // WHEN
        const wrapper = shallowMount(DataSync, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(dataSyncServiceStub.retrieve.lastCall.firstArg).toMatchObject({
          sort: ['id,asc'],
        });
      });
    });
    describe('Handles', () => {
      let comp: DataSyncComponentType;

      beforeEach(async () => {
        const wrapper = shallowMount(DataSync, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();
        dataSyncServiceStub.retrieve.reset();
        dataSyncServiceStub.retrieve.resolves({ headers: {}, data: [] });
      });

      it('should load a page', async () => {
        // GIVEN
        dataSyncServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        comp.page = 2;
        await comp.$nextTick();

        // THEN
        expect(dataSyncServiceStub.retrieve.called).toBeTruthy();
        expect(comp.dataSyncs[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should not load a page if the page is the same as the previous page', () => {
        // WHEN
        comp.page = 1;

        // THEN
        expect(dataSyncServiceStub.retrieve.called).toBeFalsy();
      });

      it('should re-initialize the page', async () => {
        // GIVEN
        comp.page = 2;
        await comp.$nextTick();
        dataSyncServiceStub.retrieve.reset();
        dataSyncServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        comp.clear();
        await comp.$nextTick();

        // THEN
        expect(comp.page).toEqual(1);
        expect(dataSyncServiceStub.retrieve.callCount).toEqual(1);
        expect(comp.dataSyncs[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should calculate the sort attribute for a non-id attribute', async () => {
        // WHEN
        comp.propOrder = 'name';
        await comp.$nextTick();

        // THEN
        expect(dataSyncServiceStub.retrieve.lastCall.firstArg).toMatchObject({
          sort: ['name,asc', 'id'],
        });
      });

      it('Should call delete service on confirmDelete', async () => {
        // GIVEN
        dataSyncServiceStub.delete.resolves({});

        // WHEN
        comp.prepareRemove({ id: 123 });

        comp.removeDataSync();
        await comp.$nextTick(); // clear components

        // THEN
        expect(dataSyncServiceStub.delete.called).toBeTruthy();

        // THEN
        await comp.$nextTick(); // handle component clear watch
        expect(dataSyncServiceStub.retrieve.callCount).toEqual(1);
      });
    });
  });
});
