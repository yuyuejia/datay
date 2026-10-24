import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import dayjs from 'dayjs';
import DataSourceUpdate from './data-source-update.vue';
import DataSourceService from './data-source.service';
import { DATE_TIME_LONG_FORMAT } from '@/shared/composables/date-format';
import AlertService from '@/shared/alert/alert.service';

type DataSourceUpdateComponentType = InstanceType<typeof DataSourceUpdate>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const dataSourceSample = { id: 123 };

describe('Component Tests', () => {
  let mountOptions: MountingOptions<DataSourceUpdateComponentType>['global'];
  let alertService: AlertService;

  describe('DataSource Management Update Component', () => {
    let comp: DataSourceUpdateComponentType;
    let dataSourceServiceStub: SinonStubbedInstance<DataSourceService>;

    beforeEach(() => {
      route = {};
      dataSourceServiceStub = sinon.createStubInstance<DataSourceService>(DataSourceService);
      dataSourceServiceStub.retrieve.onFirstCall().resolves(Promise.resolve([]));

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
          dataSourceService: () => dataSourceServiceStub,
        },
      };
    });

    afterEach(() => {
      vitest.resetAllMocks();
    });

    describe('load', () => {
      beforeEach(() => {
        const wrapper = shallowMount(DataSourceUpdate, { global: mountOptions });
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
        const wrapper = shallowMount(DataSourceUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.dataSource = dataSourceSample;
        dataSourceServiceStub.update.resolves(dataSourceSample);

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(dataSourceServiceStub.update.calledWith(dataSourceSample)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });

      it('Should call create service on save for new entity', async () => {
        // GIVEN
        const entity = {};
        dataSourceServiceStub.create.resolves(entity);
        const wrapper = shallowMount(DataSourceUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.dataSource = entity;

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(dataSourceServiceStub.create.calledWith(entity)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });
    });

    describe('Before route enter', () => {
      it('Should retrieve data', async () => {
        // GIVEN
        dataSourceServiceStub.find.resolves(dataSourceSample);
        dataSourceServiceStub.retrieve.resolves([dataSourceSample]);

        // WHEN
        route = {
          params: {
            dataSourceId: `${dataSourceSample.id}`,
          },
        };
        const wrapper = shallowMount(DataSourceUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(comp.dataSource).toMatchObject(dataSourceSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        dataSourceServiceStub.find.resolves(dataSourceSample);
        const wrapper = shallowMount(DataSourceUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });

    describe('Test connection', () => {
      it('Should show detailed reason when test fails', async () => {
        const wrapper = shallowMount(DataSourceUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.dataSource = {
          type: 'MYSQL',
          url: 'jdbc:mysql://127.0.0.1:3306/demo',
          hostname: '127.0.0.1',
          port: '3306',
          database: 'demo',
          username: 'root',
        };
        const message = '连接测试失败：Connection refused（请检查端口是否开放、数据库服务是否已启动）';
        dataSourceServiceStub.testConnection.resolves({ success: false, message });
        const showErrorDetail = vitest.spyOn(alertService, 'showErrorDetail');

        await comp.testConnection();

        expect(showErrorDetail).toHaveBeenCalledWith('连接测试失败', message);
        expect(comp.isTestingConnection).toEqual(false);
      });
    });

    describe('Url mode switch', () => {
      it('Should preserve the custom url when switching to simple and back to custom', async () => {
        // GIVEN
        const wrapper = shallowMount(DataSourceUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.dataSource = {
          type: 'MYSQL',
          url: 'jdbc:mysql://custom-host:3307/customdb?useSSL=false',
          hostname: '127.0.0.1',
          port: '3306',
          database: 'demo',
        };
        await comp.$nextTick();
        const customUrl = comp.dataSource.url;

        // WHEN switch to simple mode
        comp.onUrlModeChange('simple');
        await comp.$nextTick();
        // THEN the url is regenerated from the simple fields
        expect(comp.dataSource.url).not.toEqual(customUrl);

        // WHEN switch back to custom mode
        comp.onUrlModeChange('custom');
        await comp.$nextTick();
        // THEN the custom url is restored
        expect(comp.dataSource.url).toEqual(customUrl);
      });
    });
  });
});
