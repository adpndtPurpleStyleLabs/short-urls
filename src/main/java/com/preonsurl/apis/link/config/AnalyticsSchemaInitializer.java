package com.preonsurl.apis.link.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

/**
 * Initializes and verifies analytics schema columns on application startup, ensuring
 * database tables support granular tracking preferences and rich location/telemetry logging.
 */
@Component
public class AnalyticsSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsSchemaInitializer.class);

    private final DataSource dataSource;

    public AnalyticsSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            // 1. Verify and update short_urls table
            ensureColumns(connection, metaData, "short_urls",
                    new ColumnDef("track_location", "BOOLEAN NOT NULL DEFAULT TRUE"),
                    new ColumnDef("track_user_agent", "BOOLEAN NOT NULL DEFAULT TRUE"),
                    new ColumnDef("track_ip", "BOOLEAN NOT NULL DEFAULT TRUE"),
                    new ColumnDef("track_referrer", "BOOLEAN NOT NULL DEFAULT TRUE")
            );

            // 2. Verify and update short_urls_access_log table
            ensureColumns(connection, metaData, "short_urls_access_log",
                    new ColumnDef("country", "VARCHAR(64) NULL"),
                    new ColumnDef("city", "VARCHAR(128) NULL"),
                    new ColumnDef("latitude", "DOUBLE NULL"),
                    new ColumnDef("longitude", "DOUBLE NULL"),
                    new ColumnDef("accuracy", "DOUBLE NULL"),
                    new ColumnDef("device", "VARCHAR(64) NULL"),
                    new ColumnDef("browser", "VARCHAR(64) NULL"),
                    new ColumnDef("os", "VARCHAR(64) NULL")
            );

            // 3. Verify and update link_recipients table for 1px email open IP & location tracking
            ensureColumns(connection, metaData, "link_recipients",
                    new ColumnDef("opened_ip", "VARCHAR(64) NULL"),
                    new ColumnDef("opened_country", "VARCHAR(128) NULL"),
                    new ColumnDef("opened_city", "VARCHAR(128) NULL"),
                    new ColumnDef("opened_user_agent", "VARCHAR(512) NULL")
            );

            log.info("Analytics schema verification completed successfully.");
        } catch (Exception e) {
            log.warn("Analytics schema verification warning (tables may already be current): {}", e.getMessage());
        }
    }

    private record ColumnDef(String name, String typeDefinition) {}

    private void ensureColumns(Connection conn, DatabaseMetaData meta, String tableName, ColumnDef... columns) {
        try {
            Set<String> existingColumns = new HashSet<>();
            try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, tableName, null)) {
                while (rs.next()) {
                    existingColumns.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
            }
            if (existingColumns.isEmpty()) {
                // Try uppercase table name (for some DB dialects)
                try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, tableName.toUpperCase(), null)) {
                    while (rs.next()) {
                        existingColumns.add(rs.getString("COLUMN_NAME").toLowerCase());
                    }
                }
            }

            try (Statement stmt = conn.createStatement()) {
                for (ColumnDef col : columns) {
                    if (!existingColumns.contains(col.name().toLowerCase())) {
                        String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + col.name() + " " + col.typeDefinition();
                        try {
                            stmt.executeUpdate(sql);
                            log.info("Added missing column '{}' to table '{}'", col.name(), tableName);
                        } catch (Exception alterEx) {
                            log.debug("Column addition '{}' on '{}' skipped: {}", col.name(), tableName, alterEx.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Schema inspection on table '{}' encountered: {}", tableName, e.getMessage());
        }
    }
}
