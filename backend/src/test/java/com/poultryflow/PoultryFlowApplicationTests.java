package com.poultryflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.poultryflow.testing.PostgreSqlIntegrationTest;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PoultryFlowApplicationTests extends PostgreSqlIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Test
    void contextLoadsWithMigratedPostgreSql() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
    }
}
