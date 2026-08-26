import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import dayjs from 'dayjs';
import ETLNodeUpdate from './etl-node-update.vue';
import ETLNodeService from './etl-node.service';
import { DATE_TIME_LONG_FORMAT } from '@/shared/composables/date-format';
import AlertService from '@/shared/alert/alert.service';

type ETLNodeUpdateComponentType = InstanceType<typeof ETLNodeUpdate>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const eTLNodeSample = { id: 123 };

describe('Component Tests', () => {
  let mountOptions: MountingOptions<ETLNodeUpdateComponentType>['global'];
  let alertService: AlertService;

  describe('ETLNode Management Update Component', () => {
    let comp: ETLNodeUpdateComponentType;
    let eTLNodeServiceStub: SinonStubbedInstance<ETLNodeService>;

    beforeEach(() => {
      route = {};
      eTLNodeServiceStub = sinon.createStubInstance<ETLNodeService>(ETLNodeService);
      eTLNodeServiceStub.retrieve.onFirstCall().resolves(Promise.resolve([]));

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
          eTLNodeService: () => eTLNodeServiceStub,
        },
      };
    });

    afterEach(() => {
      vitest.resetAllMocks();
    });

    describe('load', () => {
      beforeEach(() => {
        const wrapper = shallowMount(ETLNodeUpdate, { global: mountOptions });
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
        const wrapper = shallowMount(ETLNodeUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.eTLNode = eTLNodeSample;
        eTLNodeServiceStub.update.resolves(eTLNodeSample);

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(eTLNodeServiceStub.update.calledWith(eTLNodeSample)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });

      it('Should call create service on save for new entity', async () => {
        // GIVEN
        const entity = {};
        eTLNodeServiceStub.create.resolves(entity);
        const wrapper = shallowMount(ETLNodeUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.eTLNode = entity;

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(eTLNodeServiceStub.create.calledWith(entity)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });
    });

    describe('Before route enter', () => {
      it('Should retrieve data', async () => {
        // GIVEN
        eTLNodeServiceStub.find.resolves(eTLNodeSample);
        eTLNodeServiceStub.retrieve.resolves([eTLNodeSample]);

        // WHEN
        route = {
          params: {
            eTLNodeId: `${eTLNodeSample.id}`,
          },
        };
        const wrapper = shallowMount(ETLNodeUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(comp.eTLNode).toMatchObject(eTLNodeSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        eTLNodeServiceStub.find.resolves(eTLNodeSample);
        const wrapper = shallowMount(ETLNodeUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
