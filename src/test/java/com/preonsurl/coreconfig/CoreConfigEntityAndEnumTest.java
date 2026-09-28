package com.preonsurl.coreconfig;

import com.preonsurl.coreconfig.constants.CoreConfigKeys;
import com.preonsurl.coreconfig.entity.CoreConfig;
import com.preonsurl.coreconfig.enums.ConfigValueType;
import com.preonsurl.coreconfig.exception.CoreConfigNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CoreConfigEntityAndEnumTest {

    @Test
    @DisplayName("CoreConfig prePersist populates default values when fields are not preset")
    void prePersist_setsDefaults() {
        CoreConfig config = new CoreConfig();
        config.setConfigKey("test.key");
        config.setConfigValue("test.value");
        config.setValueType(ConfigValueType.STRING);
        config.setActive(false);
        config.setVersion(null);
        config.setCreatedAt(null);
        config.setUpdatedAt(null);

        config.prePersist();

        assertTrue(config.isActive());
        assertEquals(1L, config.getVersion());
        assertNotNull(config.getCreatedAt());
        assertNotNull(config.getUpdatedAt());
    }

    @Test
    @DisplayName("CoreConfig prePersist preserves preset values if already provided")
    void prePersist_preservesExistingValues() {
        LocalDateTime customTime = LocalDateTime.of(2025, 1, 1, 10, 0);
        CoreConfig config = CoreConfig.builder()
                .configKey("test.key")
                .configValue("test.value")
                .valueType(ConfigValueType.INTEGER)
                .active(true)
                .version(5L)
                .createdAt(customTime)
                .updatedAt(customTime)
                .build();

        config.prePersist();

        assertTrue(config.isActive());
        assertEquals(5L, config.getVersion());
        assertEquals(customTime, config.getCreatedAt());
        assertEquals(customTime, config.getUpdatedAt());
    }

    @Test
    @DisplayName("CoreConfig getters, setters, and constructors operate properly")
    void entity_gettersAndSetters() {
        LocalDateTime now = LocalDateTime.now();
        CoreConfig config = new CoreConfig(
                100L,
                "key.one",
                "val.one",
                ConfigValueType.BOOLEAN,
                "Test description",
                true,
                2L,
                now,
                now
        );

        assertEquals(100L, config.getId());
        assertEquals("key.one", config.getConfigKey());
        assertEquals("val.one", config.getConfigValue());
        assertEquals(ConfigValueType.BOOLEAN, config.getValueType());
        assertEquals("Test description", config.getDescription());
        assertTrue(config.isActive());
        assertEquals(2L, config.getVersion());
        assertEquals(now, config.getCreatedAt());
        assertEquals(now, config.getUpdatedAt());

        config.setId(200L);
        config.setDescription("New description");
        assertEquals(200L, config.getId());
        assertEquals("New description", config.getDescription());
    }

    @Test
    @DisplayName("ConfigValueType enum contains expected constants")
    void configValueType_constants() {
        ConfigValueType[] values = ConfigValueType.values();
        assertTrue(values.length >= 7);
        assertEquals(ConfigValueType.STRING, ConfigValueType.valueOf("STRING"));
        assertEquals(ConfigValueType.INTEGER, ConfigValueType.valueOf("INTEGER"));
        assertEquals(ConfigValueType.LONG, ConfigValueType.valueOf("LONG"));
        assertEquals(ConfigValueType.DOUBLE, ConfigValueType.valueOf("DOUBLE"));
        assertEquals(ConfigValueType.BOOLEAN, ConfigValueType.valueOf("BOOLEAN"));
        assertEquals(ConfigValueType.DECIMAL, ConfigValueType.valueOf("DECIMAL"));
        assertEquals(ConfigValueType.JSON, ConfigValueType.valueOf("JSON"));
    }

    @Test
    @DisplayName("CoreConfigNotFoundException supports single message and cause constructors")
    void exception_constructors() {
        CoreConfigNotFoundException ex1 = new CoreConfigNotFoundException("my.key");
        assertTrue(ex1.getMessage().contains("my.key"));

        IllegalArgumentException cause = new IllegalArgumentException("Root cause");
        CoreConfigNotFoundException ex2 = new CoreConfigNotFoundException("my.key", cause);
        assertTrue(ex2.getMessage().contains("my.key"));
        assertEquals(cause, ex2.getCause());
    }

    @Test
    @DisplayName("CoreConfigKeys contains expected key names across groups")
    void coreConfigKeys_constants() {
        assertEquals("sercure.domain", CoreConfigKeys.App.SECURE_DOMAIN);
        assertEquals("app.url", CoreConfigKeys.App.APP_URL);
        assertEquals("secure.url", CoreConfigKeys.App.SECURE_URL);
        assertEquals("psecure.url", CoreConfigKeys.App.PSECURE_URL);

        assertEquals("worker.count", CoreConfigKeys.Shortener.WORKER_COUNT);
        assertEquals("worker.bucket.capacity", CoreConfigKeys.Shortener.BUCKET_CAPACITY);
        assertEquals("worker.secret", CoreConfigKeys.Shortener.SECRET);

        assertEquals("security.jwt.expiry.seconds", CoreConfigKeys.Security.JWT_EXPIRATION_SECONDS);
        assertEquals("security.jwt.secret", CoreConfigKeys.Security.JWT_SECRET);

        assertEquals("mailtrap.api.key", CoreConfigKeys.Email.API_KEY);
        assertEquals("mailtrap.email", CoreConfigKeys.Email.EMAIL);
        assertEquals("mailtrap.name", CoreConfigKeys.Email.NAME);
        assertEquals("email.support", CoreConfigKeys.Email.SUPPORT_EMAIL);

        assertEquals("endpoint.console", CoreConfigKeys.Endpoint.CONSOLE);
        assertEquals("endpoint.help", CoreConfigKeys.Endpoint.HELP);
        assertEquals("endpoint.doc", CoreConfigKeys.Endpoint.DOC);

        assertEquals("razorpay.id", CoreConfigKeys.Razorpay.ID);
        assertEquals("razorpay.secret", CoreConfigKeys.Razorpay.SECRET);
        assertEquals("razorpay.currency", CoreConfigKeys.Razorpay.CURRENCY);
        assertEquals("razorpay.pro-plan-amount", CoreConfigKeys.Razorpay.PRO_PLAN_AMOUNT);
    }
}
