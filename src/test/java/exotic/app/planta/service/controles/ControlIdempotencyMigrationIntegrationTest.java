package exotic.app.planta.service.controles;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class ControlIdempotencyMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("control_idempotency_test")
            .withUsername("test")
            .withPassword("test");

    @Test
    void migracionCreaUnicidadPorActorAccionRecursoYClave() throws Exception {
        try (Connection connection = POSTGRES.createConnection("")) {
            connection.createStatement().execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .baselineOnMigrate(true)
                .baselineVersion("102")
                .load()
                .migrate();

        try (Connection connection = POSTGRES.createConnection("")) {
            connection.createStatement().execute("INSERT INTO users(id) VALUES (7)");
            String insert = """
                    INSERT INTO control_idempotencia
                        (actor_id, accion, recurso, clave, huella_payload, creada_en)
                    VALUES
                        (7, 'EJECUTAR', 'control/31', 'key-1',
                         'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', CURRENT_TIMESTAMP)
                    """;
            assertEquals(1, connection.createStatement().executeUpdate(insert));
            SQLException duplicate = assertThrows(SQLException.class,
                    () -> connection.createStatement().executeUpdate(insert));
            assertEquals("23505", duplicate.getSQLState());
        }
    }
}
