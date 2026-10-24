package com.fast.load_test.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Connections for read-only work: from the replica when it's reachable, otherwise from the
 * primary. After a failure the replica is skipped for {@link #RETRY_AFTER_MS}, so an outage
 * doesn't add a connection attempt to every read.
 */
@Slf4j
public class ReplicaFallbackDataSource extends AbstractDataSource {
    private static final long RETRY_AFTER_MS = 5_000;

    private final DataSource replica;
    private final DataSource primary;
    private volatile long skipReplicaUntil;

    public ReplicaFallbackDataSource(DataSource replica, DataSource primary) {
        this.replica = replica;
        this.primary = primary;
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (System.currentTimeMillis() >= skipReplicaUntil) {
            try {
                return replica.getConnection();
            } catch (SQLException | RuntimeException e) {
                // Hikari reports a pool that can't start as a RuntimeException
                skipReplicaUntil = System.currentTimeMillis() + RETRY_AFTER_MS;
                log.warn("Replica unavailable, reading from the primary for the next {} ms: {}", RETRY_AFTER_MS, e.getMessage());
            }
        }
        return primary.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new UnsupportedOperationException("Credentials come from each pool's configuration");
    }
}
