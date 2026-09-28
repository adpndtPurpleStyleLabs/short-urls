package com.preonsurl.coreconfig.service;

import com.preonsurl.coreconfig.entity.CoreConfig;
import com.preonsurl.coreconfig.exception.CoreConfigNotFoundException;
import com.preonsurl.coreconfig.repository.CoreConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoreConfigService {
    private final CoreConfigRepository repository;
    private final AtomicReference<Map<String, CoreConfig>> cache = new AtomicReference<>(Collections.emptyMap());
    private volatile LocalDateTime lastRefresh;
    @PostConstruct
    public void initialize() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${preonsurl.coreconfig.refresh-ms:60000}")
    public void scheduledRefresh() {
        refresh();
    }

    public void refresh() {
        Map<String, CoreConfig> newCache = repository.findAllByActiveTrue()
                        .stream()
                        .collect(Collectors.toUnmodifiableMap(CoreConfig::getConfigKey, config -> config));

        cache.set(newCache);
        lastRefresh = LocalDateTime.now();
    }
    public String get(String key) {
        CoreConfig config = cache.get().get(key);
        if (config == null) {
            throw new CoreConfigNotFoundException("Core config not found: " + key);
        }
        return config.getConfigValue();
    }
    public String getOrDefault(String key, String defaultValue) {
        CoreConfig config = cache.get().get(key);
        return config != null ? config.getConfigValue() : defaultValue;
    }

    public int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public long getLong(String key) {
        return Long.parseLong(get(key));
    }

    public double getDouble(String key) {
        return Double.parseDouble(get(key));
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    public BigDecimal getDecimal(String key) {
        return new BigDecimal(get(key));
    }

    public boolean contains(String key) {
        return cache.get().containsKey(key);
    }

    public Map<String, CoreConfig> getAll() {
        return cache.get();
    }

    public LocalDateTime getLastRefresh() {
        return lastRefresh;
    }
}
