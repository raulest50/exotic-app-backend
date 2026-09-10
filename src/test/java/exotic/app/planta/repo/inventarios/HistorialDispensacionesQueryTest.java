package exotic.app.planta.repo.inventarios;

import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistorialDispensacionesQueryTest {

    private final HistorialDispensacionesQuery query =
            new HistorialDispensacionesQuery(null);

    @Test
    void sinFiltrosConstruyeBusquedaDeTodasLasDispensaciones() {
        HistorialDispensacionesQuery.ConsultaJpql consulta = query.construirConsulta(
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals(
                Set.of("tipoEntidadCausante"),
                consulta.parametros().keySet()
        );
        assertEquals(
                TransaccionAlmacen.TipoEntidadCausante.OD,
                consulta.parametros().get("tipoEntidadCausante")
        );
        assertFalse(consulta.fromAndWhere().contains("EXISTS"));
        assertFalse(consulta.fromAndWhere().contains("IS NULL OR"));
    }

    @Test
    void combinaLoteProductoYFechasSinParametrosNulos() {
        LocalDateTime inicio = LocalDateTime.of(2026, 9, 10, 0, 0);
        LocalDateTime fin = LocalDateTime.of(2026, 9, 10, 23, 59, 59, 999_999_999);

        HistorialDispensacionesQuery.ConsultaJpql consulta = query.construirConsulta(
                null,
                null,
                "TCO-0000011-26",
                "403003",
                inicio,
                fin
        );

        assertEquals(
                Set.of(
                        "tipoEntidadCausante",
                        "loteAsignado",
                        "productoTerminadoId",
                        "fechaInicio",
                        "fechaFin"
                ),
                consulta.parametros().keySet()
        );
        assertTrue(consulta.fromAndWhere().contains("EXISTS"));
        assertTrue(consulta.fromAndWhere().contains("LOWER(op.loteAsignado) LIKE"));
        assertTrue(consulta.fromAndWhere().contains("op.producto.productoId = :productoTerminadoId"));
        assertTrue(consulta.fromAndWhere().contains("t.fechaTransaccion >= :fechaInicio"));
        assertTrue(consulta.fromAndWhere().contains("t.fechaTransaccion <= :fechaFin"));
        assertTrue(consulta.parametros().values().stream().noneMatch(valor -> valor == null));
        assertFalse(consulta.fromAndWhere().contains("IS NULL OR"));
    }

    @Test
    void cubreTodasLasCombinacionesAdmitidasSinAgregarParametrosAusentes() {
        LocalDateTime inicio = LocalDateTime.of(2026, 9, 10, 0, 0);
        LocalDateTime fin = LocalDateTime.of(2026, 9, 10, 23, 59, 59, 999_999_999);

        for (int tipoFiltroId = 0; tipoFiltroId <= 3; tipoFiltroId++) {
            for (boolean conProducto : new boolean[]{false, true}) {
                for (boolean conFecha : new boolean[]{false, true}) {
                    Integer transaccionId = tipoFiltroId == 1 ? 1709 : null;
                    Integer ordenProduccionId = tipoFiltroId == 2 ? 746 : null;
                    String loteAsignado = tipoFiltroId == 3 ? "TCO-0000011-26" : null;
                    String productoId = conProducto ? "403003" : null;

                    HistorialDispensacionesQuery.ConsultaJpql consulta = query.construirConsulta(
                            transaccionId,
                            ordenProduccionId,
                            loteAsignado,
                            productoId,
                            conFecha ? inicio : null,
                            conFecha ? fin : null
                    );

                    Set<String> parametrosEsperados = new HashSet<>();
                    parametrosEsperados.add("tipoEntidadCausante");
                    if (transaccionId != null) {
                        parametrosEsperados.add("transaccionId");
                    }
                    if (ordenProduccionId != null) {
                        parametrosEsperados.add("ordenProduccionId");
                    }
                    if (loteAsignado != null) {
                        parametrosEsperados.add("loteAsignado");
                    }
                    if (productoId != null) {
                        parametrosEsperados.add("productoTerminadoId");
                    }
                    if (conFecha) {
                        parametrosEsperados.add("fechaInicio");
                        parametrosEsperados.add("fechaFin");
                    }

                    assertEquals(parametrosEsperados, consulta.parametros().keySet());
                    assertTrue(consulta.parametros().values().stream().noneMatch(valor -> valor == null));
                    assertFalse(consulta.fromAndWhere().contains("IS NULL OR"));
                }
            }
        }
    }

    @Test
    void aplicaPaginacionConteoYOrdenEstableCuandoNoHayFiltros() {
        EntityManager entityManager = mock(EntityManager.class);
        @SuppressWarnings("unchecked")
        TypedQuery<TransaccionAlmacen> consultaContenido = mock(TypedQuery.class);
        @SuppressWarnings("unchecked")
        TypedQuery<Long> consultaConteo = mock(TypedQuery.class);
        HistorialDispensacionesQuery consulta =
                new HistorialDispensacionesQuery(entityManager);

        TransaccionAlmacen transaccion = new TransaccionAlmacen();
        transaccion.setTransaccionId(1709);

        when(entityManager.createQuery(anyString(), eq(TransaccionAlmacen.class)))
                .thenReturn(consultaContenido);
        when(entityManager.createQuery(anyString(), eq(Long.class)))
                .thenReturn(consultaConteo);
        when(consultaContenido.setParameter(anyString(), any()))
                .thenReturn(consultaContenido);
        when(consultaContenido.setFirstResult(anyInt()))
                .thenReturn(consultaContenido);
        when(consultaContenido.setMaxResults(anyInt()))
                .thenReturn(consultaContenido);
        when(consultaContenido.getResultList()).thenReturn(List.of(transaccion));
        when(consultaConteo.setParameter(anyString(), any()))
                .thenReturn(consultaConteo);
        when(consultaConteo.getSingleResult()).thenReturn(5L);

        Page<TransaccionAlmacen> pagina = consulta.buscar(
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(1, 2)
        );

        assertEquals(1, pagina.getNumber());
        assertEquals(2, pagina.getSize());
        assertEquals(5, pagina.getTotalElements());
        assertEquals(3, pagina.getTotalPages());
        assertEquals(List.of(transaccion), pagina.getContent());
        verify(consultaContenido).setFirstResult(2);
        verify(consultaContenido).setMaxResults(2);

        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(jpql.capture(), eq(TransaccionAlmacen.class));
        assertTrue(jpql.getValue().endsWith(
                "ORDER BY t.fechaTransaccion DESC, t.transaccionId DESC"
        ));
    }
}
