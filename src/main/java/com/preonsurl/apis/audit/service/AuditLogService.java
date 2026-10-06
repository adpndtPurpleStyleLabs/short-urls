package com.preonsurl.apis.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.preonsurl.apis.audit.dto.AuditLogResponse;
import com.preonsurl.apis.audit.entity.AuditLog;
import com.preonsurl.apis.audit.enums.AuditAction;
import com.preonsurl.apis.audit.enums.AuditResourceType;
import com.preonsurl.apis.audit.repository.AuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Service
@Transactional(readOnly = true)
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public record DownloadResult(String fileName, String contentType, Resource resource, long contentLength) {}

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public Page<AuditLogResponse> getLogs(Long userId, String actionStr, String resourceTypeStr,
                                         String startDateStr, String endDateStr, Pageable pageable) {
        AuditAction action = parseAction(actionStr);
        AuditResourceType resourceType = parseResourceType(resourceTypeStr);
        LocalDateTime startDate = parseDateTime(startDateStr, true);
        LocalDateTime endDate = parseDateTime(endDateStr, false);

        Page<AuditLog> page = auditLogRepository.findFiltered(userId, action, resourceType, startDate, endDate, pageable);
        return page.map(AuditLogResponse::fromEntity);
    }

    public DownloadResult exportLogs(Long userId, String actionStr, String resourceTypeStr,
                                     String startDateStr, String endDateStr, String formatStr) {
        AuditAction action = parseAction(actionStr);
        AuditResourceType resourceType = parseResourceType(resourceTypeStr);
        LocalDateTime startDate = parseDateTime(startDateStr, true);
        LocalDateTime endDate = parseDateTime(endDateStr, false);

        List<AuditLog> logs = auditLogRepository.findAllFiltered(userId, action, resourceType, startDate, endDate);
        boolean isJson = "JSON".equalsIgnoreCase(formatStr);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = String.format("audit_logs_%s.%s", timestamp, isJson ? "json" : "csv");

        byte[] contentBytes;
        String contentType;

        if (isJson) {
            contentType = "application/json";
            List<Map<String, Object>> rows = new ArrayList<>();
            for (AuditLog logItem : logs) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("timestamp", logItem.getCreatedAt() != null ? logItem.getCreatedAt().toString() : null);
                map.put("action", logItem.getAction() != null ? logItem.getAction().name() : null);
                map.put("resourceType", logItem.getResourceType() != null ? logItem.getResourceType().name() : null);
                map.put("resourceId", logItem.getResourceId());
                map.put("details", logItem.getDetails());
                map.put("status", logItem.getStatus());
                map.put("ipAddress", logItem.getIpAddress());
                map.put("userAgent", logItem.getUserAgent());
                rows.add(map);
            }
            try {
                contentBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(rows);
            } catch (Exception e) {
                log.error("Failed to serialize audit logs to JSON: {}", e.getMessage(), e);
                contentBytes = "[]".getBytes(StandardCharsets.UTF_8);
            }
        } else {
            contentType = "text/csv; charset=UTF-8";
            StringBuilder sb = new StringBuilder();
            sb.append("timestamp,action,resource_type,resource_id,status,details,ip_address,user_agent\n");
            for (AuditLog logItem : logs) {
                sb.append(escapeCsv(logItem.getCreatedAt())).append(",");
                sb.append(escapeCsv(logItem.getAction())).append(",");
                sb.append(escapeCsv(logItem.getResourceType())).append(",");
                sb.append(escapeCsv(logItem.getResourceId())).append(",");
                sb.append(escapeCsv(logItem.getStatus())).append(",");
                sb.append(escapeCsv(logItem.getDetails())).append(",");
                sb.append(escapeCsv(logItem.getIpAddress())).append(",");
                sb.append(escapeCsv(logItem.getUserAgent())).append("\n");
            }
            contentBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        }

        return new DownloadResult(fileName, contentType, new ByteArrayResource(contentBytes), contentBytes.length);
    }

    private AuditAction parseAction(String str) {
        if (str == null || str.isBlank() || "ALL".equalsIgnoreCase(str)) return null;
        try {
            return AuditAction.valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private AuditResourceType parseResourceType(String str) {
        if (str == null || str.isBlank() || "ALL".equalsIgnoreCase(str)) return null;
        try {
            return AuditResourceType.valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String input, boolean isStart) {
        if (input == null || input.isBlank()) return null;
        String trimmed = input.trim();
        try {
            if (trimmed.length() == 10) {
                LocalDate date = LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
                return isStart ? date.atStartOfDay() : date.atTime(23, 59, 59);
            }
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
            } catch (Exception ex) {
                log.warn("Could not parse audit log date parameter: {}", input);
                return null;
            }
        }
    }

    private String escapeCsv(Object val) {
        if (val == null) return "";
        String s = String.valueOf(val);
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
