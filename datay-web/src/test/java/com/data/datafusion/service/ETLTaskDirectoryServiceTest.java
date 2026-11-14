package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.DataApi;
import com.data.datafusion.domain.ETLTask;
import com.data.datafusion.domain.ETLTaskDirectory;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.DataApiRepository;
import com.data.datafusion.repository.ETLTaskDirectoryRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.service.dto.ETLTaskDirectoryDTO;
import com.data.datafusion.service.mapper.ETLTaskDirectoryMapper;
import com.data.datafusion.service.mapper.ETLTaskDirectoryMapperImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ETLTaskDirectoryServiceTest {

    private ETLTaskDirectoryRepository directoryRepository;
    private ETLTaskRepository etlTaskRepository;
    private JobRepository jobRepository;
    private DataApiRepository dataApiRepository;
    private ETLTaskDirectoryMapper mapper;
    private ETLTaskDirectoryService service;

    @BeforeEach
    void setUp() {
        directoryRepository = mock(ETLTaskDirectoryRepository.class);
        etlTaskRepository = mock(ETLTaskRepository.class);
        jobRepository = mock(JobRepository.class);
        dataApiRepository = mock(DataApiRepository.class);
        mapper = new ETLTaskDirectoryMapperImpl();
        service = new ETLTaskDirectoryService(directoryRepository, etlTaskRepository, jobRepository, dataApiRepository, mapper);
    }

    @Test
    void shouldNormalizeTaskTypeAndTrimNameWhenCreating() {
        when(directoryRepository.save(any(ETLTaskDirectory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ETLTaskDirectoryDTO dto = new ETLTaskDirectoryDTO();
        dto.setName("  生产任务  ");
        dto.setTaskType(null);

        ETLTaskDirectoryDTO saved = service.save(dto);

        assertThat(saved.getName()).isEqualTo("生产任务");
        assertThat(saved.getTaskType()).isEqualTo(TaskConstants.ETL_TASK_KIND_BATCH);
        assertThat(saved.getSortOrder()).isZero();
    }

    @Test
    void shouldMoveChildrenAndTasksToRootWhenDeleting() {
        ETLTaskDirectory child = new ETLTaskDirectory().id("child");
        ETLTask task = new ETLTask().id("task-1").directoryId("dir-1");
        DataApi dataApi = new DataApi().id("api-1").directoryId("dir-1");
        when(directoryRepository.findByParentIdOrderBySortOrderAsc("dir-1")).thenReturn(List.of(child));
        when(etlTaskRepository.findByDirectoryId("dir-1")).thenReturn(List.of(task));
        when(dataApiRepository.findByDirectoryId("dir-1")).thenReturn(List.of(dataApi));

        service.delete("dir-1");

        assertThat(child.getParentId()).isNull();
        assertThat(task.getDirectoryId()).isNull();
        assertThat(dataApi.getDirectoryId()).isNull();
        verify(directoryRepository).deleteById("dir-1");
    }

    @Test
    void shouldAcceptDataApiDirectoryType() {
        when(directoryRepository.save(any(ETLTaskDirectory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ETLTaskDirectoryDTO dto = new ETLTaskDirectoryDTO();
        dto.setName("服务目录");
        dto.setTaskType(TaskConstants.TASK_TYPE_DATA_API);

        ETLTaskDirectoryDTO saved = service.save(dto);

        assertThat(saved.getTaskType()).isEqualTo(TaskConstants.TASK_TYPE_DATA_API);
    }

    @Test
    void shouldCollectSubtreeDirectoryIds() {
        ETLTaskDirectory child = new ETLTaskDirectory().id("child");
        ETLTaskDirectory grandChild = new ETLTaskDirectory().id("grand-child");
        when(directoryRepository.findByParentIdOrderBySortOrderAsc("root")).thenReturn(List.of(child));
        when(directoryRepository.findByParentIdOrderBySortOrderAsc("child")).thenReturn(List.of(grandChild));
        when(directoryRepository.findByParentIdOrderBySortOrderAsc("grand-child")).thenReturn(List.of());

        assertThat(service.collectSubtreeIds("root")).containsExactly("root", "child", "grand-child");
    }

    @Test
    void shouldRejectMovingDirectoryUnderItsDescendant() {
        ETLTaskDirectory existing = new ETLTaskDirectory().id("dir-1").name("旧名称").taskType(TaskConstants.ETL_TASK_KIND_BATCH);
        ETLTaskDirectory child = new ETLTaskDirectory().id("dir-2").taskType(TaskConstants.ETL_TASK_KIND_BATCH);
        when(directoryRepository.findById("dir-1")).thenReturn(Optional.of(existing));
        when(directoryRepository.findById("dir-2")).thenReturn(Optional.of(child));
        when(directoryRepository.findByParentIdOrderBySortOrderAsc("dir-1")).thenReturn(List.of(child));
        when(directoryRepository.findByParentIdOrderBySortOrderAsc("dir-2")).thenReturn(List.of());

        ETLTaskDirectoryDTO dto = new ETLTaskDirectoryDTO();
        dto.setId("dir-1");
        dto.setName("新名称");
        dto.setParentId("dir-2");

        assertThatThrownBy(() -> service.update(dto)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("子孙目录");
    }
}
