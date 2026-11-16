import { describe, expect, it } from 'vitest';
import { shallowMount } from '@vue/test-utils';

import TaskDependencyEditor from './TaskDependencyEditor.vue';

const jobs = [
  { id: 'job-1', jobName: '任务一', type: 'SQL', status: 'ONLINE' },
  { id: 'job-2', jobName: '任务二', type: 'ETL', status: 'OFFLINE' },
];

const mountOptions = {
  global: {
    stubs: {
      'el-button': {
        template: '<button class="test-button" @click="$emit(\'click\')"><slot /></button>',
      },
      'el-select': true,
      'el-option': true,
      'el-input-number': true,
      'font-awesome-icon': true,
    },
  },
};

describe('TaskDependencyEditor', () => {
  it('adds an empty dependency row with the default window', async () => {
    const wrapper = shallowMount(TaskDependencyEditor, {
      ...mountOptions,
      props: { modelValue: [], jobs, excludedJobIds: ['job-1'] },
    });

    await wrapper.find('button.test-button').trigger('click');

    expect(wrapper.emitted('update:modelValue')?.[0]?.[0]).toEqual([{ parentJobCode: '', lastIntervalMinutes: 1440 }]);
  });

  it('removes the selected dependency row', async () => {
    const wrapper = shallowMount(TaskDependencyEditor, {
      ...mountOptions,
      props: {
        modelValue: [{ parentJobCode: 'job-2', lastIntervalMinutes: 30 }],
        jobs,
      },
    });

    const buttons = wrapper.findAll('button.test-button');
    await buttons[buttons.length - 1].trigger('click');

    expect(wrapper.emitted('update:modelValue')?.[0]?.[0]).toEqual([]);
  });
});
