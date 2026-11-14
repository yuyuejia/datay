package com.data.datafusion.service;

import com.alibaba.fastjson2.JSON;
import com.data.datafusion.domain.DataApiCallLog;
import com.data.datafusion.repository.DataApiCallLogRepository;
import com.data.datafusion.security.DomainUserDetailsService.UserWithId;
import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.service.dto.DataApiCallLogDTO;
import com.data.datafusion.service.dto.DataApiCallStatsDTO;
import com.data.datafusion.service.dto.DataApiDTO;
import com.data.datafusion.service.mapper.DataApiCallLogMapper;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * 数据服务调用日志与统计。
 *
 * <p>日志写入使用独立事务，且任何写入异常都不会影响数据服务本身；调用参数会做敏感字段脱敏。</p>
 */
@Service
public class DataApiCallLogService {

    private static final Logger LOG = LoggerFactory.getLogger(DataApiCallLogService.class);

    private static final int MAX_PARAMS_LENGTH = 4000;

    private static final int MAX_PARAM_VALUE_LENGTH = 200;

    private static final int MAX_ERROR_LENGTH = 2000;

    private static final int MAX_RANGE_DAYS = 366;

    private static final Pattern SENSITIVE_KEY = Pattern.compile("(?i).*(pass|pwd|token|secret|key|auth|credential).*");

    private final DataApiCallLogRepository callLogRepository;

    private final DataApiCallLogMapper callLogMapper;

    private final DataApiService dataApiService;

    private final TransactionTemplate requiresNewTransaction;

    public DataApiCallLogService(
        DataApiCallLogRepository callLogRepository,
        DataApiCallLogMapper callLogMapper,
        DataApiService dataApiService,
        PlatformTransactionManager transactionManager
    ) {
        this.callLogRepository = callLogRepository;
        this.callLogMapper = callLogMapper;
        this.dataApiService = dataApiService;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 记录一次数据服务调用。任何异常只记录 warn，不向调用方抛出。
     */
    public void record(
        DataApiDTO api,
        String requestedCode,
        DataApiCallMeta meta,
        Map<String, Object> params,
        String status,
        Integer httpStatus,
        String errorMessage,
        long durationMs,
        Long resultRows
    ) {
        try {
            // 日志在调用结束后写入，createTime 回拨耗时，近似记录真实调用开始时间
            ZonedDateTime callTime = ZonedDateTime.now().minus(durationMs, ChronoUnit.MILLIS);
            DataApiCallLog log = new DataApiCallLog();
            log.setTenantId(SecurityUtils.getCurrentTenantIdString().orElse(null));
            log.setApiId(api == null ? null : api.getId());
            log.setApiCode(api == null ? requestedCode : api.getCode());
            log.setApiName(api == null ? null : api.getName());
            log.setSourceType(api == null ? null : api.getSourceType());
            log.setRequestSource(meta == null || !StringUtils.hasText(meta.source()) ? DataApiCallLog.REQUEST_SOURCE_INTERNAL : meta.source());
            log.setRequestMethod(meta == null ? null : truncate(meta.method(), 16));
            log.setRequestPath(meta == null ? null : truncate(meta.path(), 500));
            log.setRequestParams(summarizeParams(params));
            log.setCallerUserId(resolveCallerUserId());
            log.setCallerLogin(SecurityUtils.getCurrentUserLogin().orElse(null));
            log.setClientIp(meta == null ? null : truncate(meta.clientIp(), 64));
            log.setUserAgent(meta == null ? null : truncate(meta.userAgent(), 500));
            log.setStatus(status);
            log.setHttpStatus(httpStatus);
            log.setErrorMessage(truncate(errorMessage, MAX_ERROR_LENGTH));
            log.setDurationMs(durationMs);
            log.setResultRows(resultRows);
            log.setStatDate(callTime.toLocalDate());
            log.setStatHour(callTime.getHour());
            log.setCreateTime(callTime);
            requiresNewTransaction.executeWithoutResult(txStatus -> callLogRepository.save(log));
        } catch (Exception e) {
            LOG.warn("Failed to record data api call log: code={}, status={}, message={}", requestedCode, status, e.getMessage());
        }
    }

    /**
     * 分页查询某服务的调用日志。
     */
    @Transactional(readOnly = true)
    public Page<DataApiCallLogDTO> findLogs(
        String apiId,
        Pageable pageable,
        String status,
        LocalDate startDate,
        LocalDate endDate
    ) {
        Specification<DataApiCallLog> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("apiId"), apiId));
            if (StringUtils.hasText(status)) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status.trim().toUpperCase(Locale.ROOT)));
            }
            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("statDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("statDate"), endDate));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return callLogRepository.findAll(specification, pageable).map(callLogMapper::toDto);
    }

    /**
     * 查询某服务在指定日期范围内的调用统计，未产生调用的日期会补零。
     */
    @Transactional(readOnly = true)
    public DataApiCallStatsDTO getStats(String apiId, LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(6) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("统计时间范围不能超过 " + MAX_RANGE_DAYS + " 天");
        }

        DataApiDTO api = dataApiService
            .findOne(apiId)
            .orElseThrow(() -> new IllegalArgumentException("数据服务不存在: " + apiId));

        Map<LocalDate, DataApiCallLogRepository.DailyStat> statsByDate = new LinkedHashMap<>();
        callLogRepository.findDailyStats(apiId, start, end).forEach(item -> statsByDate.put(item.getStatDate(), item));

        DataApiCallStatsDTO result = new DataApiCallStatsDTO();
        result.setApiId(api.getId());
        result.setApiCode(api.getCode());
        result.setApiName(api.getName());
        result.setStartDate(start);
        result.setEndDate(end);

        long totalCount = 0;
        long successCount = 0;
        long failureCount = 0;
        long totalDurationMs = 0;
        long maxDurationMs = 0;
        List<DataApiCallStatsDTO.TrendPoint> trend = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            DataApiCallLogRepository.DailyStat row = statsByDate.get(date);
            long dayTotal = row == null ? 0 : value(row.getTotalCount());
            long daySuccess = row == null ? 0 : value(row.getSuccessCount());
            long dayFailure = row == null ? 0 : value(row.getFailureCount());
            long dayDuration = row == null ? 0 : value(row.getTotalDurationMs());
            long dayMax = row == null ? 0 : value(row.getMaxDurationMs());

            DataApiCallStatsDTO.TrendPoint point = new DataApiCallStatsDTO.TrendPoint();
            point.setDate(date);
            point.setTotalCount(dayTotal);
            point.setSuccessCount(daySuccess);
            point.setFailureCount(dayFailure);
            point.setAvgDurationMs(dayTotal == 0 ? 0 : round(dayDuration * 1.0 / dayTotal));
            point.setMaxDurationMs(dayMax);
            trend.add(point);

            totalCount += dayTotal;
            successCount += daySuccess;
            failureCount += dayFailure;
            totalDurationMs += dayDuration;
            maxDurationMs = Math.max(maxDurationMs, dayMax);
        }

        result.setTotalCount(totalCount);
        result.setSuccessCount(successCount);
        result.setFailureCount(failureCount);
        result.setSuccessRate(totalCount == 0 ? 0 : round(successCount * 100.0 / totalCount));
        result.setAvgDurationMs(totalCount == 0 ? 0 : round(totalDurationMs * 1.0 / totalCount));
        result.setMaxDurationMs(maxDurationMs);
        result.setTrend(trend);
        return result;
    }

    /**
     * 清空某服务的调用日志。
     */
    @Transactional
    public void clear(String apiId) {
        callLogRepository.deleteByApiId(apiId);
    }

    private Long resolveCallerUserId() {
        Optional<Long> userId = SecurityUtils.getCurrentUserId();
        if (userId.isPresent()) {
            return userId.get();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserWithId userWithId) {
            return userWithId.getId();
        }
        return null;
    }

    private static String summarizeParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        params.forEach((key, value) -> {
            if (!StringUtils.hasText(key)) {
                return;
            }
            if ("access_token".equalsIgnoreCase(key) || SENSITIVE_KEY.matcher(key).matches()) {
                safe.put(key, "***");
            } else {
                safe.put(key, truncate(toStringValue(value), MAX_PARAM_VALUE_LENGTH));
            }
        });
        return truncate(JSON.toJSONString(safe), MAX_PARAMS_LENGTH);
    }

    private static String toStringValue(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof String text ? text : JSON.toJSONString(value);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private static long value(Long value) {
        return value == null ? 0 : value;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
