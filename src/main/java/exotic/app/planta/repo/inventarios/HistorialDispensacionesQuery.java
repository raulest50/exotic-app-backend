package exotic.app.planta.repo.inventarios;

import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consulta de lectura exclusiva para el historial paginado de dispensaciones.
 *
 * <p>Los predicados opcionales solo se incorporan cuando su valor está presente.
 * Esto evita enviar a PostgreSQL parámetros nulos usados únicamente en expresiones
 * {@code IS NULL}, cuyo tipo no puede determinarse en algunas combinaciones.</p>
 */
@Repository
@RequiredArgsConstructor
public class HistorialDispensacionesQuery {

    private static final String SELECT = "SELECT t";
    private static final String COUNT = "SELECT COUNT(t)";
    private static final String ORDER_BY =
            " ORDER BY t.fechaTransaccion DESC, t.transaccionId DESC";

    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Page<TransaccionAlmacen> buscar(
            Integer transaccionId,
            Integer ordenProduccionId,
            String loteAsignado,
            String productoTerminadoId,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin,
            Pageable pageable
    ) {
        ConsultaJpql consulta = construirConsulta(
                transaccionId,
                ordenProduccionId,
                loteAsignado,
                productoTerminadoId,
                fechaInicio,
                fechaFin
        );

        TypedQuery<TransaccionAlmacen> consultaContenido = entityManager.createQuery(
                SELECT + consulta.fromAndWhere() + ORDER_BY,
                TransaccionAlmacen.class
        );
        aplicarParametros(consultaContenido, consulta.parametros());
        consultaContenido.setFirstResult(Math.toIntExact(pageable.getOffset()));
        consultaContenido.setMaxResults(pageable.getPageSize());
        List<TransaccionAlmacen> contenido = consultaContenido.getResultList();

        TypedQuery<Long> consultaConteo = entityManager.createQuery(
                COUNT + consulta.fromAndWhere(),
                Long.class
        );
        aplicarParametros(consultaConteo, consulta.parametros());
        long total = consultaConteo.getSingleResult();

        return new PageImpl<>(contenido, pageable, total);
    }

    ConsultaJpql construirConsulta(
            Integer transaccionId,
            Integer ordenProduccionId,
            String loteAsignado,
            String productoTerminadoId,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin
    ) {
        StringBuilder fromAndWhere = new StringBuilder(
                " FROM TransaccionAlmacen t " +
                "WHERE t.tipoEntidadCausante = :tipoEntidadCausante"
        );
        Map<String, Object> parametros = new LinkedHashMap<>();
        parametros.put(
                "tipoEntidadCausante",
                TransaccionAlmacen.TipoEntidadCausante.OD
        );

        if (transaccionId != null) {
            fromAndWhere.append(" AND t.transaccionId = :transaccionId");
            parametros.put("transaccionId", transaccionId);
        }
        if (ordenProduccionId != null) {
            fromAndWhere.append(" AND t.idEntidadCausante = :ordenProduccionId");
            parametros.put("ordenProduccionId", ordenProduccionId);
        }
        if (fechaInicio != null) {
            fromAndWhere.append(" AND t.fechaTransaccion >= :fechaInicio");
            parametros.put("fechaInicio", fechaInicio);
        }
        if (fechaFin != null) {
            fromAndWhere.append(" AND t.fechaTransaccion <= :fechaFin");
            parametros.put("fechaFin", fechaFin);
        }

        if (loteAsignado != null || productoTerminadoId != null) {
            fromAndWhere.append(
                    " AND EXISTS (SELECT op FROM OrdenProduccion op " +
                    "WHERE op.ordenId = t.idEntidadCausante"
            );
            if (loteAsignado != null) {
                fromAndWhere.append(
                        " AND op.loteAsignado IS NOT NULL " +
                        "AND LOWER(op.loteAsignado) LIKE " +
                        "LOWER(CONCAT('%', :loteAsignado, '%'))"
                );
                parametros.put("loteAsignado", loteAsignado);
            }
            if (productoTerminadoId != null) {
                fromAndWhere.append(
                        " AND op.producto.productoId = :productoTerminadoId"
                );
                parametros.put("productoTerminadoId", productoTerminadoId);
            }
            fromAndWhere.append(')');
        }

        return new ConsultaJpql(fromAndWhere.toString(), Map.copyOf(parametros));
    }

    private void aplicarParametros(TypedQuery<?> consulta, Map<String, Object> parametros) {
        parametros.forEach(consulta::setParameter);
    }

    record ConsultaJpql(String fromAndWhere, Map<String, Object> parametros) {
    }
}
