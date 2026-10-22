package com.data.datafusion.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskConstantsPriorityTest {

    @Test
    void acceptsOnlySupportedPriorityValues() {
        assertThat(TaskConstants.isValidPriority(TaskConstants.PRIORITY_LOW)).isTrue();
        assertThat(TaskConstants.isValidPriority(TaskConstants.PRIORITY_NORMAL)).isTrue();
        assertThat(TaskConstants.isValidPriority(TaskConstants.PRIORITY_HIGH)).isTrue();

        assertThat(TaskConstants.isValidPriority(null)).isFalse();
        assertThat(TaskConstants.isValidPriority(TaskConstants.MIN_PRIORITY - 1)).isFalse();
        assertThat(TaskConstants.isValidPriority(TaskConstants.MAX_PRIORITY + 1)).isFalse();
    }

    @Test
    void defaultsToNormalPriority() {
        assertThat(TaskConstants.DEFAULT_PRIORITY).isEqualTo(TaskConstants.PRIORITY_NORMAL);
        assertThat(TaskConstants.MIN_PRIORITY).isEqualTo(TaskConstants.PRIORITY_LOW);
        assertThat(TaskConstants.MAX_PRIORITY).isEqualTo(TaskConstants.PRIORITY_HIGH);
    }
}
