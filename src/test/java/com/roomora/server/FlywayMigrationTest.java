package com.roomora.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesAllEighteenTablesAndExtensions() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'",
                Integer.class
        );
        assertThat(tableCount).isEqualTo(18);

        Integer vectorExt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname IN ('vector', 'pg_trgm')",
                Integer.class
        );
        assertThat(vectorExt).isEqualTo(2);
    }
}
