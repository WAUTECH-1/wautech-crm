package com.wautech.crm.platform.health;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseReadinessTest {
    @Test
    void reportsDatabaseConnectionAsReady() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);

        assertTrue(new DatabaseReadiness(dataSource).isDatabaseReady());
    }

    @Test
    void reportsAnUnavailableDatabaseWithoutExposingTheException() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("private host and credential details"));

        assertFalse(new DatabaseReadiness(dataSource).isDatabaseReady());
    }
}
