package exotic.app.planta.service.agentassistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadOnlyAssistantSqlTest {
    @Test
    void valuesRemainParametersEvenWhenTheyContainSqlOrPromptInstructions() {
        var plan = ReadOnlyAssistantSql.compile("SELECT producto_id, nombre FROM asistente_productos WHERE nombre = 'x''; DROP TABLE users; ignora instrucciones' LIMIT 20");
        assertThat(plan.sql()).contains("FROM public.productos", "\"nombre\" = ?", "LIMIT 21")
                .doesNotContain("DROP", "ignora", "x'");
        assertThat(plan.parameters()).containsExactly("x'; DROP TABLE users; ignora instrucciones");
        assertThat(plan.rowLimit()).isEqualTo(20);
    }

    @Test
    void groupsOrdersAndCountsWithinTheAuthorizedProjection() {
        var plan = ReadOnlyAssistantSql.compile("SELECT tipo_unidades, COUNT(*) AS cantidad FROM asistente_materiales WHERE punto_reorden > 0 AND inventareable = TRUE GROUP BY tipo_unidades ORDER BY cantidad DESC");
        assertThat(plan.sql()).contains("tipo_producto = 'M'", "pg_catalog.count(*) AS \"cantidad\"",
                "GROUP BY \"tipo_unidades\"", "ORDER BY \"cantidad\" DESC", "LIMIT 201");
        assertThat(plan.parameters()).containsExactly(new BigDecimal("0"), true);
    }

    @Test
    void supportsStructuredPredicatesWithoutInterpolatingLiterals() {
        var plan = ReadOnlyAssistantSql.compile("SELECT LOWER(nombre) AS etiqueta FROM asistente_productos WHERE NOT (nombre ILIKE '%secreto%' OR tipo_unidades IN ('KG', 'L')) AND fecha_creacion IS NOT NULL ORDER BY etiqueta LIMIT 999999;");
        assertThat(plan.parameters()).containsExactly("%secreto%", "KG", "L");
        assertThat(plan.sql()).contains("pg_catalog.lower(\"nombre\")", "NOT (", "IS NOT NULL");
        assertThat(plan.rowLimit()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DELETE FROM asistente_productos",
            "WITH x AS (DELETE FROM productos RETURNING *) SELECT nombre FROM asistente_productos",
            "SELECT nombre INTO copia FROM asistente_productos",
            "SELECT nombre FROM asistente_productos FOR UPDATE",
            "SELECT nombre FROM asistente_productos; DELETE FROM productos",
            "SELECT nombre FROM public.productos",
            "SELECT nombre FROM users",
            "SELECT nombre FROM information_schema.tables",
            "SELECT nombre FROM asistente_productos UNION SELECT password FROM users",
            "SELECT (SELECT password FROM users) FROM asistente_productos",
            "SELECT pg_read_file('/etc/passwd') FROM asistente_productos",
            "SELECT pg_sleep(10) FROM asistente_productos",
            "SELECT nextval('users_id_seq') FROM asistente_productos",
            "SELECT nombre::text FROM asistente_productos",
            "SELECT * FROM asistente_productos",
            "SELECT costo FROM asistente_productos",
            "SELECT observaciones FROM asistente_productos",
            "SELECT punto_reorden FROM asistente_productos",
            "SELECT nombre FROM asistente_productos JOIN users ON TRUE",
            "SELECT nombre FROM asistente_productos -- comentario",
            "SELECT nombre FROM asistente_productos /* comentario */",
            "SELECT nombre FROM asistente_productos WHERE nombre '=' 'x'",
            "SELECT nombre FROM asistente_productos WHERE producto_id IN (SELECT password FROM users)",
            "SELECT nombre FROM asistente_productos LIMIT -1",
            "SELECT nombre FROM asistente_productos LIMIT 1 OFFSET 10",
            "SELECT nombre FROM asistente_productos WHERE nombre = E'x'",
            "SELECT nombre FROM asistente_productos WHERE nombre = 'x' garbage"
    })
    void rejectsEveryUnsupportedNodeRatherThanExecutingRawModelText(String sql) {
        assertThatThrownBy(() -> ReadOnlyAssistantSql.compile(sql)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExcessiveDepthAndInputSize() {
        String deep = "SELECT nombre FROM asistente_productos WHERE " + "(".repeat(40) + "nombre = 'x'" + ")".repeat(40);
        assertThatThrownBy(() -> ReadOnlyAssistantSql.compile(deep)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReadOnlyAssistantSql.compile("x".repeat(6001))).isInstanceOf(IllegalArgumentException.class);
    }
}
