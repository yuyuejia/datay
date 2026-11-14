import { type MountingOptions, flushPromises, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';

import DataApi from './data-api.vue';
import DataApiService from './data-api.service';
import DataSourceService from '@/entities/data-source/data-source.service';
import ETLTaskDirectoryService from '@/entities/etl-task/etl-task-directory.service';
import AlertService from '@/shared/alert/alert.service';

type DataApiComponentType = InstanceType<typeof DataApi>;

const bModalStub = {
  render: () => {},
  methods: {
    hide: () => {},
    show: () => {},
  },
};

describe('Component Tests', () => {
  let alertService: AlertService;

  describe('DataApi Management Component', () => {
    let dataApiServiceStub: SinonStubbedInstance<DataApiService>;
    let dataSourceServiceStub: SinonStubbedInstance<DataSourceService>;
    let etlTaskDirectoryServiceStub: SinonStubbedInstance<ETLTaskDirectoryService>;
    let mountOptions: MountingOptions<DataApiComponentType>['global'];

    beforeEach(() => {
      dataApiServiceStub = sinon.createStubInstance<DataApiService>(DataApiService);
      dataApiServiceStub.retrieve.resolves({ headers: {}, data: [] });
      dataSourceServiceStub = sinon.createStubInstance<DataSourceService>(DataSourceService);
      dataSourceServiceStub.retrieve.resolves({ data: [] });
      etlTaskDirectoryServiceStub = sinon.createStubInstance<ETLTaskDirectoryService>(ETLTaskDirectoryService);
      etlTaskDirectoryServiceStub.retrieve.resolves({ data: [] });

      alertService = new AlertService();

      mountOptions = {
        stubs: {
          jhiItemCount: true,
          bPagination: true,
          AppModal: bModalStub as any,
          'font-awesome-icon': true,
          'router-link': true,
          'el-table': true,
          'el-table-column': true,
          'el-tag': true,
          'el-button': true,
        },
        provide: {
          alertService,
          dataApiService: () => dataApiServiceStub,
          dataSourceService: () => dataSourceServiceStub,
          etlTaskDirectoryService: () => etlTaskDirectoryServiceStub,
        },
      };
    });

    describe('Mount', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        dataApiServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123, code: 'order_query' }] });

        // WHEN
        const wrapper = shallowMount(DataApi, { global: mountOptions });
        const comp = wrapper.vm;
        await flushPromises();

        // THEN
        expect(dataApiServiceStub.retrieve.calledOnce).toBeTruthy();
        expect(comp.dataApis[0]).toEqual(expect.objectContaining({ id: 123 }));
      });
    });

    describe('Handles', () => {
      let comp: DataApiComponentType;

      beforeEach(async () => {
        const wrapper = shallowMount(DataApi, { global: mountOptions });
        comp = wrapper.vm;
        await flushPromises();
        dataApiServiceStub.retrieve.reset();
        dataApiServiceStub.retrieve.resolves({ headers: {}, data: [] });
      });

      it('Should call delete service on confirmDelete', async () => {
        // GIVEN
        dataApiServiceStub.delete.resolves({});

        // WHEN
        comp.prepareRemove({ id: 123 });
        comp.removeDataApi();
        await comp.$nextTick();

        // THEN
        expect(dataApiServiceStub.delete.called).toBeTruthy();
      });

      it('should pass the selected directory when filtering services', async () => {
        // GIVEN
        dataApiServiceStub.retrieve.resolves({ headers: {}, data: [] });

        // WHEN
        comp.selectedDirectoryId = 'dir-1';
        await flushPromises();
        await comp.$nextTick();

        // THEN
        expect(dataApiServiceStub.retrieve.lastCall.firstArg).toMatchObject({
          directoryId: 'dir-1',
        });
      });
    });
  });
});
