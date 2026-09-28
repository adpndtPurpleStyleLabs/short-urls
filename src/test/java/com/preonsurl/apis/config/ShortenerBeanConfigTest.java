package com.preonsurl.apis.config;

import com.preonsurl.core.ShortCodePool;
import com.preonsurl.coreconfig.constants.CoreConfigKeys;
import com.preonsurl.coreconfig.service.CoreConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShortenerBeanConfigTest {

    private CoreConfigService coreConfigService;

    @BeforeEach
    void setUp() {
        coreConfigService = mock(CoreConfigService.class);
        when(coreConfigService.get(CoreConfigKeys.App.SECURE_DOMAIN)).thenReturn("http://localhost:8081");
        when(coreConfigService.get(CoreConfigKeys.Shortener.SECRET)).thenReturn("TEST-SECRET-1234567890");
        when(coreConfigService.getInt(CoreConfigKeys.Shortener.WORKER_COUNT)).thenReturn(2);
        when(coreConfigService.getInt(CoreConfigKeys.Shortener.BUCKET_CAPACITY)).thenReturn(10);
    }

    @Test
    void shortCodePool_runsAndBuffersCodes_whenAppProfileIsActive() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("app");

        ShortenerBeanConfig config = new ShortenerBeanConfig(env, coreConfigService);

        try (ShortCodePool pool = config.shortCodePool()) {
            assertNotNull(pool);
            assertEquals(2, pool.workerCount());
            assertTrue(pool.isAllWorkersAlive());
            assertTrue(pool.totalAvailableCodes() > 0);
            assertNotNull(pool.nextCode(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void shortCodePool_isEmpty_whenApp1ProfileIsActive() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("app-1");

        ShortenerBeanConfig config = new ShortenerBeanConfig(env, coreConfigService);

        try (ShortCodePool pool = config.shortCodePool()) {
            assertNotNull(pool);
            assertEquals(0, pool.workerCount());
            assertEquals(0, pool.totalAvailableCodes());
            assertFalse(pool.isAllWorkersAlive());
            assertThrows(IllegalStateException.class, () -> pool.nextCode(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void shortCodePool_isEmpty_whenServeProfileIsActive() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("serve");

        ShortenerBeanConfig config = new ShortenerBeanConfig(env, coreConfigService);

        try (ShortCodePool pool = config.shortCodePool()) {
            assertNotNull(pool);
            assertEquals(0, pool.workerCount());
            assertEquals(0, pool.totalAvailableCodes());
            assertFalse(pool.isAllWorkersAlive());
            assertThrows(IllegalStateException.class, () -> pool.nextCode(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void shortCodePool_isEmpty_whenPsecureServeProfileIsActive() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("psecureServe");

        ShortenerBeanConfig config = new ShortenerBeanConfig(env, coreConfigService);

        try (ShortCodePool pool = config.shortCodePool()) {
            assertNotNull(pool);
            assertEquals(0, pool.workerCount());
            assertEquals(0, pool.totalAvailableCodes());
            assertFalse(pool.isAllWorkersAlive());
            assertThrows(IllegalStateException.class, () -> pool.nextCode(1, TimeUnit.SECONDS));
        }
    }
}
