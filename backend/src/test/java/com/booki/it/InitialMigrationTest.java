package com.booki.it;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InitialMigrationTest {

    @Test
    void v1CreatesBothShippedReaderProfiles() throws Exception {
        String database = "booki_migration_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + database + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var rows = statement.executeQuery(
                     "select name, context, is_default, read_only from reader_profiles order by id")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("name")).isEqualTo("General reader");
            assertThat(rows.getString("context"))
                    .contains("Calibrate your support from evidence in the conversation")
                    .contains("ask one focused question at a time");
            assertThat(rows.getBoolean("is_default")).isTrue();
            assertThat(rows.getBoolean("read_only")).isTrue();

            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("name")).isEqualTo("Language-support reader");
            assertThat(rows.getString("context"))
                    .contains("support understanding or expressing spoken or written language")
                    .contains("not a diagnosis or a measure of intelligence");
            assertThat(rows.getBoolean("is_default")).isFalse();
            assertThat(rows.getBoolean("read_only")).isTrue();
            assertThat(rows.next()).isFalse();
        }
    }
}
