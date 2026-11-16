package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.config.StatusStorageConfig;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.JobInstanceRepository;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.jobevent.EventServiceFactory;
import com.data.datafusion.service.jobevent.IEventService;
import com.data.datafusion.service.jobevent.JobStatusEvent;
import com.data.datafusion.service.mapper.JobInstanceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class JobInstanceServiceStatusConfigTest {

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void startInstanceAttachesStatusStorageConfigToStartEvent() {
        JobInstanceRepository repository = mock(JobInstanceRepository.class);
        when(repository.save(any(JobInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        JobInstanceService service = new JobInstanceService(repository, mock(JobRepository.class), mock(JobInstanceMapper.class));

        StatusStorageConfig config = new StatusStorageConfig();
        config.setType("minio");
        config.setMinioEndpoint("http://minio:9000");
        StatusStorageConfigService configService = mock(StatusStorageConfigService.class);
        when(configService.getConfigForTenant("1")).thenReturn(config);
        service.statusStorageConfigService = configService;

        JobInstance instance = new JobInstance();
        instance.setInstanceCode("instance-1");
        instance.setType(TaskConstants.TASK_TYPE_SHELL);
        instance.setTenantId("1");

        try (MockedStatic<EventServiceFactory> eventFactory = Mockito.mockStatic(EventServiceFactory.class)) {
            IEventService eventService = mock(IEventService.class);
            eventFactory.when(EventServiceFactory::getEventService).thenReturn(eventService);

            service.startInstance(instance);

            ArgumentCaptor<JobStatusEvent> captor = ArgumentCaptor.forClass(JobStatusEvent.class);
            verify(eventService).pushStartJobEvent(captor.capture());
            JobStatusEvent event = captor.getValue();
            assertThat(event.getJobInstance().getStatusStorageConfig()).isSameAs(config);
            assertThat(event.getJobInstance().getStatus()).isEqualTo(TaskConstants.TASK_STATUS_APPENDING);
        }
    }

    @Test
    void startInstanceStillDispatchesWhenConfigResolutionFails() {
        JobInstanceRepository repository = mock(JobInstanceRepository.class);
        when(repository.save(any(JobInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        JobInstanceService service = new JobInstanceService(repository, mock(JobRepository.class), mock(JobInstanceMapper.class));

        StatusStorageConfigService configService = mock(StatusStorageConfigService.class);
        when(configService.getConfigForTenant("1")).thenThrow(new IllegalStateException("db unavailable"));
        service.statusStorageConfigService = configService;

        JobInstance instance = new JobInstance();
        instance.setInstanceCode("instance-2");
        instance.setType(TaskConstants.TASK_TYPE_SHELL);
        instance.setTenantId("1");

        try (MockedStatic<EventServiceFactory> eventFactory = Mockito.mockStatic(EventServiceFactory.class)) {
            IEventService eventService = mock(IEventService.class);
            eventFactory.when(EventServiceFactory::getEventService).thenReturn(eventService);

            service.startInstance(instance);

            ArgumentCaptor<JobStatusEvent> captor = ArgumentCaptor.forClass(JobStatusEvent.class);
            verify(eventService).pushStartJobEvent(captor.capture());
            assertThat(captor.getValue().getJobInstance().getStatusStorageConfig()).isNull();
        }
    }
}
