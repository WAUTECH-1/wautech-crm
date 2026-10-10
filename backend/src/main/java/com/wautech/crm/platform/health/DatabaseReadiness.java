package com.wautech.crm.platform.health;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;

/** Performs a detail-free database connectivity check for readiness probes. */
@Component
public class DatabaseReadiness {
    private final DataSource dataSource;

    public DatabaseReadiness(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean isDatabaseReady() {
        try (var connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException | RuntimeException unavailable) {
            return false;
        }
    }
}
