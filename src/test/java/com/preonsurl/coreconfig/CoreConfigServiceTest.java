package com.preonsurl.coreconfig;

import com.preonsurl.coreconfig.entity.CoreConfig;
import com.preonsurl.coreconfig.enums.ConfigValueType;
import com.preonsurl.coreconfig.exception.CoreConfigNotFoundException;
import com.preonsurl.coreconfig.repository.CoreConfigRepository;
import com.preonsurl.coreconfig.service.CoreConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoreConfigServiceTest {

    @Mock
    private CoreConfigRepository repository;

    @InjectMocks
    private CoreConfigService service;

    private CoreConfig stringConfig;
    private CoreConfig intConfig;
    private CoreConfig longConfig;
    private CoreConfig doubleConfig;
    private CoreConfig boolConfig;
    private CoreConfig decimalConfig;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        stringConfig = CoreConfig.builder()
                .id(1L)
                .configKey("app.domain")
                .configValue("https://example.com")
                .valueType(ConfigValueType.STRING)
                .active(true)
                .version(1L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        intConfig = CoreConfig.builder()
                .id(2L)
                .configKey("worker.count")
                .configValue("8")
                .valueType(ConfigValueType.INTEGER)
                .active(true)
                .version(1L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        longConfig = CoreConfig.builder()
                .id(3L)
                .configKey("token.expiry")
                .configValue("86400")
                .valueType(ConfigValueType.LONG)
                .active(true)
                .version(1L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        doubleConfig = CoreConfig.builder()
                .id(4L)
                .configKey("rate.ratio")
                .configValue("0.75")
                .valueType(ConfigValueType.DOUBLE)
                .active(true)
                .version(1L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        boolConfig = CoreConfig.builder()
                .id(5L)
                .configKey("feature.enabled")
                .configValue("true")
                .valueType(ConfigValueType.BOOLEAN)
                .active(true)
                .version(1L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        decimalConfig = CoreConfig.builder()
                .id(6L)
                .configKey("plan.price")
                .configValue("49.99")
                .valueType(ConfigValueType.DECIMAL)
                .active(true)
                .version(1L)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private void mockActiveConfigs(List<CoreConfig> configs) {
        when(repository.findAllByActiveTrue()).thenReturn(configs);
    }

    @Test
    @DisplayName("first request populates memory cache on demand from active database configurations")
    void firstRequest_populatesCache() {
        mockActiveConfigs(List.of(stringConfig, intConfig));

        assertEquals("https://example.com", service.get("app.domain"));
        assertEquals(8, service.getInt("worker.count"));
        assertTrue(service.contains("app.domain"));
        assertTrue(service.contains("worker.count"));
        assertNotNull(service.getLastRefresh());
        assertEquals(2, service.getAll().size());
        verify(repository, times(1)).findAllByActiveTrue();
    }

    @Test
    @DisplayName("cache is queried only on first request and subsequent requests use memory cache")
    void cache_loadedOnFirstRequestOnly() {
        when(repository.findAllByActiveTrue()).thenReturn(List.of(stringConfig));

        verify(repository, never()).findAllByActiveTrue();

        assertEquals("https://example.com", service.get("app.domain"));
        verify(repository, times(1)).findAllByActiveTrue();

        assertEquals("https://example.com", service.get("app.domain"));
        assertTrue(service.contains("app.domain"));
        verify(repository, times(1)).findAllByActiveTrue();
    }

    @Test
    @DisplayName("scheduledRefresh calls refresh and updates cache")
    void scheduledRefresh_updatesCache() {
        mockActiveConfigs(List.of(stringConfig));
        assertEquals(1, service.getAll().size());

        when(repository.findAllByActiveTrue()).thenReturn(List.of(stringConfig, intConfig, longConfig));
        service.scheduledRefresh();

        assertEquals(3, service.getAll().size());
        assertEquals(86400L, service.getLong("token.expiry"));
    }

    @Test
    @DisplayName("get returns value when key exists")
    void get_whenKeyExists_returnsValue() {
        mockActiveConfigs(List.of(stringConfig));
        assertEquals("https://example.com", service.get("app.domain"));
    }

    @Test
    @DisplayName("get throws CoreConfigNotFoundException when key is missing")
    void get_whenKeyNotFound_throwsException() {
        mockActiveConfigs(List.of(stringConfig));
        CoreConfigNotFoundException ex = assertThrows(CoreConfigNotFoundException.class,
                () -> service.get("non.existent.key"));
        assertTrue(ex.getMessage().contains("non.existent.key"));
    }

    @Test
    @DisplayName("getOrDefault returns value if present, default value if missing")
    void getOrDefault_behavior() {
        mockActiveConfigs(List.of(stringConfig));

        assertEquals("https://example.com", service.getOrDefault("app.domain", "https://default.com"));
        assertEquals("https://default.com", service.getOrDefault("missing.key", "https://default.com"));
    }

    @Test
    @DisplayName("getInt parses integer value correctly")
    void getInt_parsesCorrectly() {
        mockActiveConfigs(List.of(intConfig));
        assertEquals(8, service.getInt("worker.count"));
    }

    @Test
    @DisplayName("getLong parses long value correctly")
    void getLong_parsesCorrectly() {
        mockActiveConfigs(List.of(longConfig));
        assertEquals(86400L, service.getLong("token.expiry"));
    }

    @Test
    @DisplayName("getDouble parses double value correctly")
    void getDouble_parsesCorrectly() {
        mockActiveConfigs(List.of(doubleConfig));
        assertEquals(0.75, service.getDouble("rate.ratio"), 0.0001);
    }

    @Test
    @DisplayName("getBoolean parses boolean value correctly")
    void getBoolean_parsesCorrectly() {
        mockActiveConfigs(List.of(boolConfig));
        assertTrue(service.getBoolean("feature.enabled"));
    }

    @Test
    @DisplayName("getDecimal parses BigDecimal value correctly")
    void getDecimal_parsesCorrectly() {
        mockActiveConfigs(List.of(decimalConfig));
        assertEquals(new BigDecimal("49.99"), service.getDecimal("plan.price"));
    }

    @Test
    @DisplayName("contains checks cache accurately")
    void contains_checksKey() {
        mockActiveConfigs(List.of(stringConfig));
        assertTrue(service.contains("app.domain"));
        assertFalse(service.contains("missing.key"));
    }

    @Test
    @DisplayName("getAll returns unmodifiable map representing current cache")
    void getAll_returnsUnmodifiableMap() {
        mockActiveConfigs(List.of(stringConfig));
        Map<String, CoreConfig> all = service.getAll();
        assertEquals(1, all.size());
        assertTrue(all.containsKey("app.domain"));
        assertThrows(UnsupportedOperationException.class, () -> all.put("new.key", stringConfig));
    }
}
