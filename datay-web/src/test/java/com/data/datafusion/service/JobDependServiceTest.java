package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.Job;
import com.data.datafusion.domain.JobDepend;
import com.data.datafusion.repository.JobDependRepository;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.service.dto.JobDependDTO;
import com.data.datafusion.service.mapper.JobDependMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JobDependServiceTest {

    private final JobDependRepository jobDependRepository = mock(JobDependRepository.class);
    private final JobDependMapper jobDependMapper = mock(JobDependMapper.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobDependService service = new JobDependService(jobDependRepository, jobDependMapper, jobRepository);

    @Test
    void replacesExistingDependenciesAndSetsDerivedFields() {
        Job childJob = new Job();
        childJob.setId("child-1");
        childJob.setTenantId("tenant-1");
        when(jobRepository.findById("child-1")).thenReturn(Optional.of(childJob));
        when(jobRepository.existsById("parent-1")).thenReturn(true);
        when(jobRepository.existsById("parent-2")).thenReturn(true);
        when(jobDependRepository.findByChildJobCode(anyString())).thenReturn(List.of());

        service.replaceChildDependencies("child-1", List.of(dependency("parent-1", 60000L), dependency("parent-2", 120000L)));

        verify(jobDependRepository).deleteByChildJobCode("child-1");
        ArgumentCaptor<List<JobDepend>> captor = ArgumentCaptor.forClass(List.class);
        verify(jobDependRepository).saveAll(captor.capture());
        List<JobDepend> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved)
            .allSatisfy(item -> {
                assertThat(item.getChildJobCode()).isEqualTo("child-1");
                assertThat(item.getJobCode()).isEqualTo("child-1");
                assertThat(item.getCreateTime()).isNotNull();
                assertThat(item.getTenantId()).isEqualTo("tenant-1");
            });
        assertThat(saved).extracting(JobDepend::getParentJobCode).containsExactly("parent-1", "parent-2");
        assertThat(saved).extracting(JobDepend::getLastInterval).containsExactly(60000L, 120000L);
    }

    @Test
    void emptyDependenciesClearExistingOnes() {
        service.replaceChildDependencies("child-1", List.of());

        verify(jobDependRepository).deleteByChildJobCode("child-1");
        ArgumentCaptor<List<JobDepend>> captor = ArgumentCaptor.forClass(List.class);
        verify(jobDependRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void nullDependenciesLeaveExistingOnesUntouched() {
        service.replaceChildDependencies("child-1", null);

        verify(jobDependRepository, never()).deleteByChildJobCode(anyString());
        verify(jobDependRepository, never()).saveAll(any());
    }

    @Test
    void rejectsSelfDependency() {
        assertThatThrownBy(() -> service.replaceChildDependencies("child-1", List.of(dependency("child-1", 60000L))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能依赖自身");
    }

    @Test
    void rejectsDuplicateParents() {
        when(jobRepository.existsById("parent-1")).thenReturn(true);
        when(jobDependRepository.findByChildJobCode(anyString())).thenReturn(List.of());

        assertThatThrownBy(() ->
            service.replaceChildDependencies("child-1", List.of(dependency("parent-1", 60000L), dependency("parent-1", 60000L)))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("上游任务重复");
    }

    @Test
    void rejectsMissingParent() {
        when(jobRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> service.replaceChildDependencies("child-1", List.of(dependency("missing", 60000L))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("上游任务不存在");
    }

    @Test
    void rejectsInvalidLastInterval() {
        when(jobRepository.existsById("parent-1")).thenReturn(true);

        assertThatThrownBy(() -> service.replaceChildDependencies("child-1", List.of(dependency("parent-1", 0L))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("依赖有效窗口");
    }

    @Test
    void rejectsCircularDependency() {
        when(jobRepository.existsById("parent-1")).thenReturn(true);
        JobDepend parentDependsOnChild = new JobDepend();
        parentDependsOnChild.setParentJobCode("child-1");
        parentDependsOnChild.setChildJobCode("parent-1");
        when(jobDependRepository.findByChildJobCode("parent-1")).thenReturn(List.of(parentDependsOnChild));

        assertThatThrownBy(() -> service.replaceChildDependencies("child-1", List.of(dependency("parent-1", 60000L))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("循环");
    }

    @Test
    void findsDependenciesAsDtos() {
        JobDepend entity = new JobDepend();
        entity.setId("depend-1");
        entity.setParentJobCode("parent-1");
        entity.setChildJobCode("child-1");
        when(jobDependRepository.findByChildJobCode("child-1")).thenReturn(List.of(entity));
        JobDependDTO dto = dependency("parent-1", 60000L);
        when(jobDependMapper.toDto(entity)).thenReturn(dto);

        assertThat(service.findDtosByChildJobCode("child-1")).containsExactly(dto);
    }

    private static JobDependDTO dependency(String parentJobCode, Long lastInterval) {
        JobDependDTO dto = new JobDependDTO();
        dto.setParentJobCode(parentJobCode);
        dto.setLastInterval(lastInterval);
        return dto;
    }
}
