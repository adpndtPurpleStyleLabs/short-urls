package com.preonsurl.coreconfig;

import com.preonsurl.coreconfig.entity.CoreConfig;
import com.preonsurl.coreconfig.enums.ConfigValueType;
import com.preonsurl.coreconfig.repository.CoreConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CoreConfigRepositoryIntegrationTest {

    @Autowired
    private CoreConfigRepository repository;

    private static final String ACTIVE_KEY = "custom.repo.active.key";
    private static final String INACTIVE_KEY = "custom.repo.inactive.key";

    @BeforeEach
    void setUp() {
        LocalDateTime baseTime = LocalDateTime.of(2026, 1, 1, 12, 0);

        // Active config
        CoreConfig active = CoreConfig.builder()
                .configKey(ACTIVE_KEY)
                .configValue("active-val")
                .valueType(ConfigValueType.STRING)
                .active(true)
                .version(1L)
                .createdAt(baseTime)
                .updatedAt(baseTime.plusDays(10))
                .build();

        // Inactive config
        CoreConfig inactive = CoreConfig.builder()
                .configKey(INACTIVE_KEY)
                .configValue("inactive-val")
                .valueType(ConfigValueType.STRING)
                .active(false)
                .version(1L)
                .createdAt(baseTime)
                .updatedAt(baseTime.plusDays(10))
                .build();

        repository.findByConfigKeyAndActiveTrue(ACTIVE_KEY).ifPresent(c -> repository.delete(c));
        repository.findAll().stream()
                .filter(c -> INACTIVE_KEY.equals(c.getConfigKey()))
                .findFirst()
                .ifPresent(c -> repository.delete(c));

        repository.save(active);
        inactive = repository.saveAndFlush(inactive);
        inactive.setActive(false);
        repository.saveAndFlush(inactive);
    }

    @Test
    @DisplayName("findByConfigKeyAndActiveTrue returns config when active, empty when inactive or missing")
    void findByConfigKeyAndActiveTrue() {
        Optional<CoreConfig> active = repository.findByConfigKeyAndActiveTrue(ACTIVE_KEY);
        assertTrue(active.isPresent());
        assertEquals("active-val", active.get().getConfigValue());

        Optional<CoreConfig> inactive = repository.findByConfigKeyAndActiveTrue(INACTIVE_KEY);
        assertTrue(inactive.isEmpty());

        Optional<CoreConfig> nonExistent = repository.findByConfigKeyAndActiveTrue("definitely.does.not.exist");
        assertTrue(nonExistent.isEmpty());
    }

    @Test
    @DisplayName("findAllByActiveTrue returns only configs that have active=true")
    void findAllByActiveTrue() {
        List<CoreConfig> allActive = repository.findAllByActiveTrue();
        assertFalse(allActive.isEmpty());

        assertTrue(allActive.stream().anyMatch(c -> ACTIVE_KEY.equals(c.getConfigKey())));
        assertFalse(allActive.stream().anyMatch(c -> INACTIVE_KEY.equals(c.getConfigKey())));
        assertTrue(allActive.stream().allMatch(CoreConfig::isActive));
    }

    @Test
    @DisplayName("findAllByUpdatedAtAfterAndActiveTrue filters configs by timestamp and active status")
    void findAllByUpdatedAtAfterAndActiveTrue() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 1, 5, 0, 0);

        List<CoreConfig> results = repository.findAllByUpdatedAtAfterAndActiveTrue(cutoff);
        assertTrue(results.stream().anyMatch(c -> ACTIVE_KEY.equals(c.getConfigKey())));
        assertFalse(results.stream().anyMatch(c -> INACTIVE_KEY.equals(c.getConfigKey())));
    }
}
