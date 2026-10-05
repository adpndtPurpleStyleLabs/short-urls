package com.preonsurl.apis.dataExtraction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.preonsurl.apis.dataExtraction.dto.CreateExtractionRequest;
import com.preonsurl.apis.dataExtraction.dto.ExtractionJobResponse;
import com.preonsurl.apis.dataExtraction.entity.DataExtractionJob;
import com.preonsurl.apis.dataExtraction.enums.ExtractionDataType;
import com.preonsurl.apis.dataExtraction.enums.ExtractionReportType;
import com.preonsurl.apis.dataExtraction.enums.ExtractionStatus;
import com.preonsurl.apis.dataExtraction.repository.DataExtractionJobRepository;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.entity.NewUrlAccessLog;
import com.preonsurl.apis.link.repository.NewUrlAccessLogRepository;
import com.preonsurl.apis.link.repository.NewUrlRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Service
public class DataExtractionService {

    private final DataExtractionJobRepository jobRepository;
    private final NewUrlRepository newUrlRepository;
    private final NewUrlAccessLogRepository accessLogRepository;
    private final ObjectMapper objectMapper;
    private final Path exportStorageDir;

    public DataExtractionService(
            DataExtractionJobRepository jobRepository,
            NewUrlRepository newUrlRepository,
            NewUrlAccessLogRepository accessLogRepository
    ) {
        this.jobRepository = jobRepository;
        this.newUrlRepository = newUrlRepository;
        this.accessLogRepository = accessLogRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        this.exportStorageDir = Paths.get("data-exports").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.exportStorageDir);
        } catch (IOException e) {
            log.error("Failed to create data-exports directory: {}", e.getMessage());
        }
    }

    @Transactional
    public ExtractionJobResponse createJob(Long userId, CreateExtractionRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (request.dataType() == null) {
            throw new IllegalArgumentException("Data type is required (LINK or LINK_ANALYTICS)");
        }
        if (request.reportType() == null) {
            throw new IllegalArgumentException("Report type is required (CSV or JSON)");
        }

        String publicId = UUID.randomUUID().toString();
        DataExtractionJob job = new DataExtractionJob(publicId, userId, request.dataType(), request.reportType());

        LocalDateTime startDt = parseDateTime(request.startDateTime());
        LocalDateTime endDt = parseDateTime(request.endDateTime());
        job.setStartDateTime(startDt);
        job.setEndDateTime(endDt);

        if (request.dataType() == ExtractionDataType.LINK_ANALYTICS) {
            if (request.shortUrlIds() != null && !request.shortUrlIds().isEmpty()) {
                List<NewUrl> targets = newUrlRepository.findAllById(request.shortUrlIds()).stream()
                        .filter(u -> u.getUserId().equals(userId))
                        .toList();
                if (targets.size() == 1) {
                    job.setTargetShortUrlId(targets.get(0).getId());
                    job.setTargetShortUrl(targets.get(0).getNewUrl());
                } else if (targets.size() > 1) {
                    job.setTargetShortUrlId(null);
                    String idList = targets.stream().map(u -> String.valueOf(u.getId())).collect(java.util.stream.Collectors.joining(","));
                    job.setTargetShortUrl("IDS:" + idList);
                }
            } else if (request.shortUrlId() != null) {
                NewUrl target = newUrlRepository.findById(request.shortUrlId()).orElse(null);
                if (target != null && target.getUserId().equals(userId)) {
                    job.setTargetShortUrlId(target.getId());
                    job.setTargetShortUrl(target.getNewUrl());
                }
            } else if (request.shortUrl() != null && !request.shortUrl().isBlank()) {
                String searchStr = request.shortUrl().trim();
                Optional<NewUrl> target = newUrlRepository.findByNewUrlAndUserId(searchStr, userId);
                if (target.isEmpty()) {
                    target = newUrlRepository.findByShortCodeAndUserId(searchStr, userId);
                }
                target.ifPresent(u -> {
                    job.setTargetShortUrlId(u.getId());
                    job.setTargetShortUrl(u.getNewUrl());
                });
            }
        }

        DataExtractionJob saved = jobRepository.save(job);
        log.info("Created data extraction job: id={}, publicId={}, user={}, type={}, format={}",
                saved.getId(), saved.getPublicId(), userId, saved.getDataType(), saved.getReportType());

        // Process in background asynchronously
        processJobAsync(saved.getId());

        return ExtractionJobResponse.fromEntity(saved);
    }

    @Async
    public void processJobAsync(Long jobId) {
        DataExtractionJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Extraction job with id={} not found for async processing", jobId);
            return;
        }

        log.info("Starting extraction processing for job id={}, type={}, format={}",
                job.getId(), job.getDataType(), job.getReportType());

        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String ext = job.getReportType() == ExtractionReportType.JSON ? "json" : "csv";
            String category = (job.getDataType() == ExtractionDataType.LINK) ? "links" : "analytics";
            String fileName = String.format("%s_export_%s_%s.%s", category, timestamp, job.getPublicId().substring(0, 8), ext);

            String content;
            int recordCount;

            if (job.getDataType() == ExtractionDataType.LINK) {
                Instant startInstant = job.getStartDateTime() != null ? job.getStartDateTime().atZone(ZoneOffset.UTC).toInstant() : null;
                Instant endInstant = job.getEndDateTime() != null ? job.getEndDateTime().atZone(ZoneOffset.UTC).toInstant() : null;
                List<NewUrl> links = newUrlRepository.findAllByUserIdAndOptionalCreatedAtRange(job.getUserId(), startInstant, endInstant);
                recordCount = links.size();

                if (job.getReportType() == ExtractionReportType.CSV) {
                    content = generateLinksCsv(links);
                } else {
                    content = generateLinksJson(links);
                }
            } else {
                LocalDateTime startLocal = job.getStartDateTime();
                LocalDateTime endLocal = job.getEndDateTime();
                List<NewUrlAccessLog> logs;
                if (job.getTargetShortUrlId() != null) {
                    logs = accessLogRepository.findByShortUrlIdAndOptionalAccessedAtRange(job.getTargetShortUrlId(), startLocal, endLocal);
                } else if (job.getTargetShortUrl() != null && job.getTargetShortUrl().startsWith("IDS:")) {
                    String idsStr = job.getTargetShortUrl().substring(4);
                    List<Long> ids = Arrays.stream(idsStr.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(Long::parseLong)
                            .toList();
                    if (!ids.isEmpty()) {
                        logs = accessLogRepository.findByShortUrlIdInAndOptionalAccessedAtRange(ids, startLocal, endLocal);
                    } else {
                        logs = accessLogRepository.findAllByUserIdAndOptionalAccessedAtRange(job.getUserId(), startLocal, endLocal);
                    }
                } else {
                    logs = accessLogRepository.findAllByUserIdAndOptionalAccessedAtRange(job.getUserId(), startLocal, endLocal);
                }
                recordCount = logs.size();

                if (job.getReportType() == ExtractionReportType.CSV) {
                    content = generateAnalyticsCsv(logs);
                } else {
                    content = generateAnalyticsJson(logs);
                }
            }

            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            Path filePath = this.exportStorageDir.resolve(fileName);
            Files.write(filePath, bytes);

            job.setFileName(fileName);
            job.setFilePath(filePath.toString());
            job.setFileSizeBytes((long) bytes.length);
            job.setRecordCount(recordCount);
            if (bytes.length <= 8 * 1024 * 1024) {
                job.setFileContent(content);
            }
            job.setStatus(ExtractionStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);

            log.info("Completed extraction job id={}, records={}, size={} bytes, path={}",
                    job.getId(), recordCount, bytes.length, filePath);

        } catch (Exception ex) {
            log.error("Failed to process extraction job id={}: {}", job.getId(), ex.getMessage(), ex);
            job.setStatus(ExtractionStatus.FAILED);
            job.setErrorMessage(ex.getMessage() != null ? ex.getMessage() : "Unknown extraction error");
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
        }
    }

    public Page<ExtractionJobResponse> listJobs(Long userId, Pageable pageable) {
        Page<DataExtractionJob> page = jobRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return page.map(ExtractionJobResponse::fromEntity);
    }

    public List<ExtractionJobResponse> listRecentJobs(Long userId) {
        List<DataExtractionJob> jobs = jobRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
        return jobs.stream().map(ExtractionJobResponse::fromEntity).toList();
    }

    public ExtractionJobResponse getJob(String publicId, Long userId) {
        DataExtractionJob job = jobRepository.findByPublicIdAndUserId(publicId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Extraction job not found: " + publicId));
        return ExtractionJobResponse.fromEntity(job);
    }

    public DownloadResult getDownloadFile(String publicId, Long userId) {
        DataExtractionJob job = jobRepository.findByPublicIdAndUserId(publicId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Extraction job not found: " + publicId));

        if (job.getStatus() != ExtractionStatus.COMPLETED) {
            throw new IllegalStateException("Extraction report is not ready yet. Status: " + job.getStatus());
        }

        byte[] fileBytes = null;
        if (job.getFilePath() != null) {
            Path path = Paths.get(job.getFilePath());
            if (Files.exists(path)) {
                try {
                    fileBytes = Files.readAllBytes(path);
                } catch (IOException e) {
                    log.warn("Failed reading file from disk, falling back to db content: {}", e.getMessage());
                }
            }
        }

        if (fileBytes == null && job.getFileContent() != null) {
            fileBytes = job.getFileContent().getBytes(StandardCharsets.UTF_8);
        }

        if (fileBytes == null) {
            throw new IllegalStateException("Extracted file content could not be found.");
        }

        String contentType = (job.getReportType() == ExtractionReportType.JSON)
                ? "application/json"
                : "text/csv; charset=UTF-8";

        return new DownloadResult(job.getFileName(), contentType, new ByteArrayResource(fileBytes), fileBytes.length);
    }

    private String generateLinksCsv(List<NewUrl> links) {
        StringBuilder sb = new StringBuilder();
        sb.append("public_id,short_url,short_code,original_url,link_mode,domain,custom_path,click_count,usage_limit,is_enable,created_at,expire_at,note\n");
        for (NewUrl link : links) {
            sb.append(escapeCsv(link.getPublicId())).append(",");
            sb.append(escapeCsv(link.getNewUrl())).append(",");
            sb.append(escapeCsv(link.getShortCode())).append(",");
            sb.append(escapeCsv(link.getOriginalUrl())).append(",");
            sb.append(escapeCsv(link.getLinkMode())).append(",");
            sb.append(escapeCsv(link.getDomain())).append(",");
            sb.append(escapeCsv(link.getCustomPath())).append(",");
            sb.append(escapeCsv(link.getClickCount())).append(",");
            sb.append(escapeCsv(link.getUsageLimit())).append(",");
            sb.append(escapeCsv(link.isEnable())).append(",");
            sb.append(escapeCsv(link.getCreatedAt())).append(",");
            sb.append(escapeCsv(link.getExpireAt())).append(",");
            sb.append(escapeCsv(link.getNote())).append("\n");
        }
        return sb.toString();
    }

    private String generateLinksJson(List<NewUrl> links) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (NewUrl link : links) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("publicId", link.getPublicId());
            map.put("shortUrl", link.getNewUrl());
            map.put("shortCode", link.getShortCode());
            map.put("originalUrl", link.getOriginalUrl());
            map.put("linkMode", link.getLinkMode() != null ? link.getLinkMode().name() : "REDIRECT");
            map.put("domain", link.getDomain());
            map.put("customPath", link.getCustomPath());
            map.put("clickCount", link.getClickCount());
            map.put("usageLimit", link.getUsageLimit());
            map.put("isEnable", link.isEnable());
            map.put("createdAt", link.getCreatedAt() != null ? link.getCreatedAt().toString() : null);
            map.put("expireAt", link.getExpireAt() != null ? link.getExpireAt().toString() : null);
            map.put("note", link.getNote());
            rows.add(map);
        }
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (Exception e) {
            throw new RuntimeException("Error converting links to JSON", e);
        }
    }

    private String generateAnalyticsCsv(List<NewUrlAccessLog> logs) {
        StringBuilder sb = new StringBuilder();
        sb.append("short_url_id,short_code,accessed_at,ip_address,country,city,latitude,longitude,accuracy,device,browser,os,user_agent,referer\n");
        for (NewUrlAccessLog log : logs) {
            sb.append(escapeCsv(log.getShortUrlId())).append(",");
            sb.append(escapeCsv(log.getShortCode())).append(",");
            sb.append(escapeCsv(log.getAccessedAt())).append(",");
            sb.append(escapeCsv(log.getIpAddress())).append(",");
            sb.append(escapeCsv(log.getCountry())).append(",");
            sb.append(escapeCsv(log.getCity())).append(",");
            sb.append(escapeCsv(log.getLatitude())).append(",");
            sb.append(escapeCsv(log.getLongitude())).append(",");
            sb.append(escapeCsv(log.getAccuracy())).append(",");
            sb.append(escapeCsv(log.getDevice())).append(",");
            sb.append(escapeCsv(log.getBrowser())).append(",");
            sb.append(escapeCsv(log.getOs())).append(",");
            sb.append(escapeCsv(log.getUserAgent())).append(",");
            sb.append(escapeCsv(log.getReferer())).append("\n");
        }
        return sb.toString();
    }

    private String generateAnalyticsJson(List<NewUrlAccessLog> logs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (NewUrlAccessLog log : logs) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("shortUrlId", log.getShortUrlId());
            map.put("shortCode", log.getShortCode());
            map.put("accessedAt", log.getAccessedAt() != null ? log.getAccessedAt().toString() : null);
            map.put("ipAddress", log.getIpAddress());
            map.put("country", log.getCountry());
            map.put("city", log.getCity());
            map.put("latitude", log.getLatitude());
            map.put("longitude", log.getLongitude());
            map.put("accuracy", log.getAccuracy());
            map.put("device", log.getDevice());
            map.put("browser", log.getBrowser());
            map.put("os", log.getOs());
            map.put("userAgent", log.getUserAgent());
            map.put("referer", log.getReferer());
            rows.add(map);
        }
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (Exception e) {
            throw new RuntimeException("Error converting analytics logs to JSON", e);
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

    private LocalDateTime parseDateTime(String input) {
        if (input == null || input.isBlank()) return null;
        String trimmed = input.trim();
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
        } catch (DateTimeParseException ignored) {
        }
        try {
            LocalDate date = LocalDate.parse(trimmed);
            return date.atStartOfDay();
        } catch (DateTimeParseException ignored) {
        }
        try {
            Instant instant = Instant.parse(trimmed);
            return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    public record DownloadResult(String fileName, String contentType, Resource resource, long contentLength) {
    }
}
