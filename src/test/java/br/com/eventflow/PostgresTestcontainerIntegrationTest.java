package br.com.eventflow;

import br.com.eventflow.testinfra.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresTestcontainerIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private PostgreSQLContainer postgresContainer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldStartPostgresContainer() {
        assertTrue(
                postgresContainer.isRunning()
        );
    }

    @Test
    void shouldRunFlywayMigrationsAgainstPostgresContainer() {
        Integer migrationCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE success = true
                        """,
                        Integer.class
                );

        assertTrue(
                migrationCount != null
                        && migrationCount > 0
        );
    }

    @Test
    void shouldCreateProcessedMessagesTable() {
        Integer tableCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name = 'processed_messages'
                        """,
                        Integer.class
                );

        assertEquals(
                1,
                tableCount
        );
    }
}