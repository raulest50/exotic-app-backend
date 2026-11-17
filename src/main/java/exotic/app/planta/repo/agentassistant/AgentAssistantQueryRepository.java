package exotic.app.planta.repo.agentassistant;

import exotic.app.planta.service.agentassistant.ReadOnlyAssistantSql.CompiledQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

/** Accepts only server-compiled queries; does not expose a raw SQL executor. */
@Repository
@RequiredArgsConstructor
public class AgentAssistantQueryRepository {
    private final DataSource dataSource;

    public QueryResult execute(CompiledQuery plan) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            connection.setNetworkTimeout(ForkJoinPool.commonPool(), 10_000);
            try {
                try (Statement controls = connection.createStatement()) {
                    controls.setQueryTimeout(5);
                    controls.execute("SET TRANSACTION READ ONLY");
                    controls.execute("SET LOCAL statement_timeout = '5s'");
                    controls.execute("SET LOCAL lock_timeout = '500ms'");
                    controls.execute("SET LOCAL search_path = pg_catalog");
                }
                try (PreparedStatement query = connection.prepareStatement(plan.sql())) {
                    query.setQueryTimeout(5);
                    query.setFetchSize(50);
                    query.setMaxRows(plan.rowLimit() + 1);
                    for (int i = 0; i < plan.parameters().size(); i++) query.setObject(i + 1, plan.parameters().get(i));
                    try (ResultSet results = query.executeQuery()) { return collect(results, plan.rowLimit()); }
                }
            } finally {
                connection.rollback();
            }
        } catch (SQLException ex) {
            // Database errors can contain names, values or credentials. Never expose their text.
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No se pudo completar la consulta de lectura. Puede ser incompatible o haber superado el tiempo permitido.");
        }
    }

    private QueryResult collect(ResultSet results, int limit) throws SQLException {
        int count = results.getMetaData().getColumnCount();
        if (count < 1 || count > 16) throw new SQLException("Unexpected projection width");
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= count; i++) columns.add(results.getMetaData().getColumnLabel(i));
        List<List<Object>> rows = new ArrayList<>();
        int bytes = 0;
        boolean truncated = false;
        while (results.next()) {
            if (rows.size() >= limit) { truncated = true; break; }
            List<Object> row = new ArrayList<>();
            for (int i = 1; i <= count; i++) {
                Object value = results.getObject(i);
                if (value != null && !(value instanceof Number) && !(value instanceof Boolean)) {
                    String text = value.toString();
                    if (text.length() > 2000) { text = text.substring(0, 2000) + "…"; truncated = true; }
                    value = text;
                }
                bytes += value == null ? 4 : value.toString().getBytes(StandardCharsets.UTF_8).length + 8;
                row.add(value);
            }
            if (bytes > 200_000) { truncated = true; break; }
            rows.add(Collections.unmodifiableList(row));
        }
        return new QueryResult(List.copyOf(columns), List.copyOf(rows), truncated);
    }

    public record QueryResult(List<String> columns, List<List<Object>> rows, boolean truncated) { }
}
