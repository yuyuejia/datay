import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import ETLEdgeUpdate from './etl-edge-update.vue';
import ETLEdgeService from './etl-edge.service';
import AlertService from '@/shared/alert/alert.service';

type ETLEdgeUpdateComponentType = InstanceType<typeof ETLEdgeUpdate>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const eTLEdgeSample = { id: 123 };

describe('Component Tests', () => {
  let mountOptions: MountingOptions<ETLEdgeUpdateComponentType>['global'];
  let alertService: AlertService;

  describe('ETLEdge Management Update Component', () => {
    let comp: ETLEdgeUpdateComponentType;
    let eTLEdgeServiceStub: SinonStubbedInstance<ETLEdgeService>;

    beforeEach(() => {
      route = {};
      eTLEdgeServiceStub = sinon.createStubInstance<ETLEdgeService>(ETLEdgeService);
      eTLEdgeServiceStub.retrieve.onFirstCall().resolves(Promise.resolve([]));

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
          eTLEdgeService: () => eTLEdgeServiceStub,
        },
      };
    });

    afterEach(() => {
      vitest.resetAllMocks();
    });

    describe('save', () => {
      it('Should call update service on save for existing entity', async () => {
        // GIVEN
        const wrapper = shallowMount(ETLEdgeUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.eTLEdge = eTLEdgeSample;
        eTLEdgeServiceStub.update.resolves(eTLEdgeSample);

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(eTLEdgeServiceStub.update.calledWith(eTLEdgeSample)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });

      it('Should call create service on save for new entity', async () => {
        // GIVEN
        const entity = {};
        eTLEdgeServiceStub.create.resolves(entity);
        const wrapper = shallowMount(ETLEdgeUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.eTLEdge = entity;

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(eTLEdgeServiceStub.create.calledWith(entity)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });
    });

    describe('Before route enter', () => {
      it('Should retrieve data', async () => {
        // GIVEN
        eTLEdgeServiceStub.find.resolves(eTLEdgeSample);
        eTLEdgeServiceStub.retrieve.resolves([eTLEdgeSample]);

        // WHEN
        route = {
          params: {
            eTLEdgeId: `${eTLEdgeSample.id}`,
          },
        };
        const wrapper = shallowMount(ETLEdgeUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(comp.eTLEdge).toMatchObject(eTLEdgeSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        eTLEdgeServiceStub.find.resolves(eTLEdgeSample);
        const wrapper = shallowMount(ETLEdgeUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
