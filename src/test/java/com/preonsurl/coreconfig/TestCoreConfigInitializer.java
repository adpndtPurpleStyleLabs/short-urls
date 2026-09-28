package com.preonsurl.coreconfig;

import com.preonsurl.coreconfig.constants.CoreConfigKeys;
import com.preonsurl.coreconfig.entity.CoreConfig;
import com.preonsurl.coreconfig.enums.ConfigValueType;
import com.preonsurl.coreconfig.repository.CoreConfigRepository;
import com.preonsurl.coreconfig.service.CoreConfigService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

/**
 * Initializes default in-memory configurations for tests so that Spring Boot application context
 * can start successfully with H2 database.
 */
@Component
public class TestCoreConfigInitializer implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof CoreConfigService coreConfigService) {
            CoreConfigRepository repo = (CoreConfigRepository) ReflectionTestUtils.getField(coreConfigService, "repository");
            if (repo != null && repo.count() == 0) {
                seedConfigs(repo);
            }
        }
        return bean;
    }

    private void seedConfigs(CoreConfigRepository repo) {
        LocalDateTime now = LocalDateTime.now();

        // App
        saveConfig(repo, CoreConfigKeys.App.SECURE_DOMAIN, "go.domain.com", ConfigValueType.STRING, now);
        saveConfig(repo, CoreConfigKeys.App.APP_URL, "http://localhost:8081", ConfigValueType.STRING, now);
        saveConfig(repo, CoreConfigKeys.App.SECURE_URL, "http://localhost:8081", ConfigValueType.STRING, now);
        saveConfig(repo, CoreConfigKeys.App.PSECURE_URL, "http://localhost:8081/p/", ConfigValueType.STRING, now);

        // Shortener
        saveConfig(repo, CoreConfigKeys.Shortener.WORKER_COUNT, "4", ConfigValueType.INTEGER, now);
        saveConfig(repo, CoreConfigKeys.Shortener.BUCKET_CAPACITY, "100", ConfigValueType.INTEGER, now);
        saveConfig(repo, CoreConfigKeys.Shortener.SECRET, "PREONS-URL-SECRET-KEY-123456789", ConfigValueType.STRING, now);

        // Security
        saveConfig(repo, CoreConfigKeys.Security.JWT_EXPIRATION_SECONDS, "3600", ConfigValueType.LONG, now);
        saveConfig(repo, CoreConfigKeys.Security.JWT_SECRET, "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", ConfigValueType.STRING, now);

        // Email
        saveConfig(repo, CoreConfigKeys.Email.API_KEY, "test-mailtrap-api-key", ConfigValueType.STRING, now);
        saveConfig(repo, CoreConfigKeys.Email.EMAIL, "hello@madxglobaltech.com", ConfigValueType.STRING, now);
        saveConfig(repo, CoreConfigKeys.Email.NAME, "PruneUrl", ConfigValueType.STRING, now);
        saveConfig(repo, CoreConfigKeys.Email.SUPPORT_EMAIL, "support@indexrender.io", ConfigValueType.STRING, now);

        // Endpoint
        saveConfig(repo, CoreConfigKeys.Endpoint.CONSOLE, "/console", ConfigValueType.STRING, now);
        saveConfig(repo, CoreConfigKeys.Endpoint.HELP, "/help", ConfigValueType.STRING, now);
        saveConfig(repo, CoreConfigKeys.Endpoint.DOC, "/docs", ConfigValueType.STRING, now);

        // Razorpay
        saveConfig(repo, CoreConfigKeys.Razorpay.ID, "rzp_test_dummy", ConfigValueType.STRING, now);
        saveConfig(repo, CoreConfigKeys.Razorpay.SECRET, "rzp_secret_dummy", ConfigValueType.STRING, now);
        saveConfig(repo, CoreConfigKeys.Razorpay.CURRENCY, "USD", ConfigValueType.STRING, now);
        saveConfig(repo, CoreConfigKeys.Razorpay.PRO_PLAN_AMOUNT, "5000", ConfigValueType.LONG, now);
    }

    private void saveConfig(CoreConfigRepository repo, String key, String value, ConfigValueType type, LocalDateTime time) {
        CoreConfig entity = CoreConfig.builder()
                .configKey(key)
                .configValue(value)
                .valueType(type)
                .description("Test configuration for " + key)
                .active(true)
                .version(1L)
                .createdAt(time)
                .updatedAt(time)
                .build();
        repo.save(entity);
    }
}
