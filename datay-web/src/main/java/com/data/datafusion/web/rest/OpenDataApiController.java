package com.data.datafusion.web.rest;

import com.data.datafusion.service.DataApiAccessException;
import com.data.datafusion.service.DataApiExecuteService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 数据服务对外公开接口。
 *
 * <p>第三方通过 {@code mcp_token}（HTTP Header {@code Authorization: Bearer <token>}，或查询参数
 * {@code access_token=<token>}）鉴权后调用，复用用户在“数据服务”模块发布的服务。</p>
 */
@RestController
@RequestMapping("/open-api/data")
public class OpenDataApiController {

    private static final Logger LOG = LoggerFactory.getLogger(OpenDataApiController.class);

    private final DataApiExecuteService dataApiExecuteService;

    public OpenDataApiController(DataApiExecuteService dataApiExecuteService) {
        this.dataApiExecuteService = dataApiExecuteService;
    }

    @GetMapping("/{code}")
    public ResponseEntity<Map<String, Object>> invokeByGet(@PathVariable("code") String code, HttpServletRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            if (values != null && values.length > 0) {
                params.put(name, values.length == 1 ? values[0] : values);
            }
        });
        return doInvoke(request, code, params);
    }

    @PostMapping("/{code}")
    public ResponseEntity<Map<String, Object>> invokeByPost(
        @PathVariable("code") String code,
        @RequestBody(required = false) Map<String, Object> body,
        HttpServletRequest request
    ) {
        Map<String, Object> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            if (values != null && values.length > 0) {
                params.put(name, values.length == 1 ? values[0] : values);
            }
        });
        if (body != null) {
            body.forEach(params::put);
        }
        return doInvoke(request, code, params);
    }

    private ResponseEntity<Map<String, Object>> doInvoke(HttpServletRequest request, String code, Map<String, Object> params) {
        String token = resolveToken(request);
        try {
            Map<String, Object> result = dataApiExecuteService.invokeApi(token, code, params);
            return ResponseEntity.ok(result);
        } catch (DataApiAccessException e) {
            LOG.warn("Data api invoke failed: code={}, status={}, message={}", code, e.getStatus(), e.getMessage());
            return ResponseEntity.status(HttpStatus.resolve(e.getStatus())).body(errorBody(e.getStatus(), e.getMessage()));
        } catch (Exception e) {
            LOG.error("Data api invoke error: code={}", code, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(500, "服务执行失败"));
        }
    }

    private static String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring("Bearer ".length()).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        return request.getParameter("access_token");
    }

    private static Map<String, Object> errorBody(int code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        return body;
    }
}
