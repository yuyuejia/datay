package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 数据服务 API 前置处理脚本执行器测试。
 */
class DataApiScriptPreProcessServiceTest {

    private final DataApiScriptPreProcessService service = new DataApiScriptPreProcessService(3000);

    @Test
    void shouldExposeParamsAndUtils() {
        Map<String, Object> output = service.execute(
            "return { token: 'Bearer ' + params.appId, ts: utils.now() };",
            Map.of("appId", "A1")
        );

        assertThat(output).containsEntry("token", "Bearer A1");
        assertThat(output.get("ts")).isInstanceOf(Number.class);
    }

    @Test
    void shouldSupportMutatingParamsAndReturningIt() {
        Map<String, Object> output = service.execute(
            "params.token = 'T-' + params.appId; return params;",
            Map.of("appId", "A1", "status", "PAID")
        );

        assertThat(output).containsEntry("token", "T-A1").containsEntry("appId", "A1").containsEntry("status", "PAID");
    }

    @Test
    void shouldProvideCommonCryptoAndEncodingUtils() {
        Map<String, Object> output = service.execute(
            "return {" +
            "  md5: utils.md5('abc')," +
            "  sha256: utils.sha256('abc')," +
            "  base64: utils.base64Encode('abc')," +
            "  url: utils.urlEncode('a b')," +
            "  uuid: utils.uuid()" +
            "};",
            Map.of()
        );

        assertThat(output)
            .containsEntry("md5", "900150983cd24fb0d6963f7d28e17f72")
            .containsEntry("sha256", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
            .containsEntry("base64", "YWJj")
            .containsEntry("url", "a+b");
        assertThat(String.valueOf(output.get("uuid"))).hasSize(36);
    }

    @Test
    void shouldRejectNonObjectResult() {
        assertThatThrownBy(() -> service.execute("return 1;", Map.of()))
            .isInstanceOf(DataApiAccessException.class)
            .hasMessageContaining("必须返回一个对象");
    }

    @Test
    void shouldReportSyntaxErrorWithScriptLineNumber() {
        assertThatThrownBy(() -> service.execute("return {", Map.of()))
            .isInstanceOf(DataApiAccessException.class)
            .hasMessageContaining("前置脚本执行失败")
            .hasMessageContaining("第 1 行");
    }

    @Test
    void shouldInterruptLongRunningScript() {
        DataApiScriptPreProcessService shortTimeoutService = new DataApiScriptPreProcessService(100);

        assertThatThrownBy(() -> shortTimeoutService.execute("while (true) {}", Map.of()))
            .isInstanceOf(DataApiAccessException.class)
            .hasMessageContaining("超时");
    }

    @Test
    void shouldNotExposeJavaClasses() {
        Map<String, Object> output = service.execute(
            "return { javaVisible: typeof java !== 'undefined', packagesVisible: typeof Packages !== 'undefined' };",
            Map.of()
        );

        assertThat(output).containsEntry("javaVisible", false).containsEntry("packagesVisible", false);
    }
}
