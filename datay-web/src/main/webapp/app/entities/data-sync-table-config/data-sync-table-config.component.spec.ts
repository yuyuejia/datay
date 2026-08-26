import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';

import DataSyncTableConfig from './data-sync-table-config.vue';
import DataSyncTableConfigService from './data-sync-table-config.service';
import AlertService from '@/shared/alert/alert.service';

type DataSyncTableConfigComponentType = InstanceType<typeof DataSyncTableConfig>;

const bModalStub = {
  render: () => {},
  methods: {
    hide: () => {},
    show: () => {},
  },
};

describe('Component Tests', () => {
  let alertService: AlertService;

  describe('DataSyncTableConfig Management Component', () => {
    let dataSyncTableConfigServiceStub: SinonStubbedInstance<DataSyncTableConfigService>;
    let mountOptions: MountingOptions<DataSyncTableConfigComponentType>['global'];

    beforeEach(() => {
      dataSyncTableConfigServiceStub = sinon.createStubInstance<DataSyncTableConfigService>(DataSyncTableConfigService);
      dataSyncTableConfigServiceStub.retrieve.resolves({ headers: {} });

      alertService = new AlertService({
        bvToast: {
          toast: vitest.fn(),
        } as any,
      });

      mountOptions = {
        stubs: {
          jhiItemCount: true,
          bPagination: true,
          bModal: bModalStub as any,
          'font-awesome-icon': true,
          'b-badge': true,
          'jhi-sort-indicator': true,
          'b-button': true,
          'router-link': true,
        },
        directives: {
          'b-modal': {},
        },
        provide: {
          alertService,
          dataSyncTableConfigService: () => dataSyncTableConfigServiceStub,
        },
      };
    });

    describe('Mount', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        dataSyncTableConfigServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        const wrapper = shallowMount(DataSyncTableConfig, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(dataSyncTableConfigServiceStub.retrieve.calledOnce).toBeTruthy();
        expect(comp.dataSyncTableConfigs[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should calculate the sort attribute for an id', async () => {
        // WHEN
        const wrapper = shallowMount(DataSyncTableConfig, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(dataSyncTableConfigServiceStub.retrieve.lastCall.firstArg).toMatchObject({
          sort: ['id,asc'],
        });
      });
    });
    describe('Handles', () => {
      let comp: DataSyncTableConfigComponentType;

      beforeEach(async () => {
        const wrapper = shallowMount(DataSyncTableConfig, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();
        dataSyncTableConfigServiceStub.retrieve.reset();
        dataSyncTableConfigServiceStub.retrieve.resolves({ headers: {}, data: [] });
      });

      it('should load a page', async () => {
        // GIVEN
        dataSyncTableConfigServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        comp.page = 2;
        await comp.$nextTick();

        // THEN
        expect(dataSyncTableConfigServiceStub.retrieve.called).toBeTruthy();
        expect(comp.dataSyncTableConfigs[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should not load a page if the page is the same as the previous page', () => {
        // WHEN
        comp.page = 1;

        // THEN
        expect(dataSyncTableConfigServiceStub.retrieve.called).toBeFalsy();
      });

      it('should re-initialize the page', async () => {
        // GIVEN
        comp.page = 2;
        await comp.$nextTick();
        dataSyncTableConfigServiceStub.retrieve.reset();
        dataSyncTableConfigServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        comp.clear();
        await comp.$nextTick();

        // THEN
        expect(comp.page).toEqual(1);
        expect(dataSyncTableConfigServiceStub.retrieve.callCount).toEqual(1);
        expect(comp.dataSyncTableConfigs[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should calculate the sort attribute for a non-id attribute', async () => {
        // WHEN
        comp.propOrder = 'name';
        await comp.$nextTick();

        // THEN
        expect(dataSyncTableConfigServiceStub.retrieve.lastCall.firstArg).toMatchObject({
          sort: ['name,asc', 'id'],
        });
      });

      it('Should call delete service on confirmDelete', async () => {
        // GIVEN
        dataSyncTableConfigServiceStub.delete.resolves({});

        // WHEN
        comp.prepareRemove({ id: 123 });

        comp.removeDataSyncTableConfig();
        await comp.$nextTick(); // clear components

        // THEN
        expect(dataSyncTableConfigServiceStub.delete.called).toBeTruthy();

        // THEN
        await comp.$nextTick(); // handle component clear watch
        expect(dataSyncTableConfigServiceStub.retrieve.callCount).toEqual(1);
      });
    });
  });
});
