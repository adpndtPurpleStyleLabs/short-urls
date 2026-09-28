package com.preonsurl.apis.config;

import com.maxmind.geoip2.DatabaseReader;
import com.preonsurl.coreconfig.service.CoreConfigService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class GeoIpConfig {
    @Bean
    public DatabaseReader geoIpDatabaseReader(CoreConfigService coreConfigService) throws Exception {
        File databaseFile = new File("GeoLite2-Country.mmdb");
        if (!databaseFile.exists()) {
            throw new IllegalStateException("GeoLite2 Country database not found: " + databaseFile.getAbsolutePath());
        }

        return new DatabaseReader.Builder(databaseFile).build();
    }
}
