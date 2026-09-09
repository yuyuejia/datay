package com.data.datafusion.web.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.datafusion.service.TaskManagementService;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class WorkerResourceTest {

    private WorkerResource workerResource;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() {
        workerResource = new WorkerResource(new TaskManagementService());
    }

    private Path writeLog(String dir, String fileName, String content) throws Exception {
        Path dirPath = Files.createDirectories(tempDir.resolve(dir));
        Path file = dirPath.resolve(fileName);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private String buildHeader(String jobCode) {
        return "\n---------- jobCode: " + jobCode + " ----------\n";
    }

    private String invokeReadAggregatedLog(List<Path> paths, long offset, long maxRead, long[] meta) throws Exception {
        Method method = WorkerResource.class.getDeclaredMethod("readAggregatedLog", List.class, long.class, long.class, long[].class);
        method.setAccessible(true);
        List<File> files = paths.stream().map(Path::toFile).collect(Collectors.toList());
        return (String) method.invoke(workerResource, files, offset, maxRead, meta);
    }

    @Test
    public void testAggregateReadsAllChildLogsWithHeaders() throws Exception {
        Path fileA = writeLog("1001", "2024-01-01-100.log", "line A1\nline A2\n");
        Path fileB = writeLog("1002", "2024-01-01-100.log", "line B1\n");

        List<Path> files = Arrays.asList(fileA, fileB);
        long[] meta = new long[3];
        String content = invokeReadAggregatedLog(files, 0, 10000, meta);

        String expected =
            buildHeader("1001") + "line A1\nline A2\n" + buildHeader("1002") + "line B1\n";
        assertEquals(expected, content);
        assertEquals(expected.getBytes(StandardCharsets.UTF_8).length, meta[0]);
        assertEquals(0, meta[1]);
        assertEquals(meta[0], meta[2]);
    }

    @Test
    public void testIncrementalSlicesReassembleFullAggregate() throws Exception {
        Path fileA = writeLog("1001", "2024-01-01-100.log", "aaaa bbbb\ncccc dddd\n");
        Path fileB = writeLog("1002", "2024-01-01-100.log", "eeee ffff\n");
        Path fileC = writeLog("1003", "2024-01-01-100.log", "gggg\n");

        List<Path> files = Arrays.asList(fileA, fileB, fileC);
        StringBuilder rebuilt = new StringBuilder();
        long offset = 0;
        int chunks = 0;
        while (true) {
            long[] meta = new long[3];
            String chunk = invokeReadAggregatedLog(files, offset, 7, meta);
            rebuilt.append(chunk);
            if (meta[2] == 0) {
                break;
            }
            offset = meta[1] + meta[2];
            chunks++;
        }

        String expected =
            buildHeader("1001") +
            "aaaa bbbb\ncccc dddd\n" +
            buildHeader("1002") +
            "eeee ffff\n" +
            buildHeader("1003") +
            "gggg\n";
        assertEquals(expected, rebuilt.toString());
        assertTrue(chunks > 1, "expected multiple incremental reads");
    }

    @Test
    public void testOffsetBeyondTotalRestartsFromBeginning() throws Exception {
        Path fileA = writeLog("1001", "2024-01-01-100.log", "hello world\n");

        List<Path> files = Arrays.asList(fileA);
        long[] meta = new long[3];
        String content = invokeReadAggregatedLog(files, 99999, 10000, meta);

        assertEquals(buildHeader("1001") + "hello world\n", content);
        assertEquals(0, meta[1]);
        assertEquals(buildHeader("1001").length() + "hello world\n".length(), meta[2]);
    }
}
