package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccion;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import exotic.app.planta.model.produccion.ActorTipoEventoSeguimiento;
import exotic.app.planta.model.produccion.EstadoSeguimientoOrdenArea;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.SeguimientoOrdenAreaEvento;
import exotic.app.planta.model.produccion.TipoEventoSeguimiento;
import exotic.app.planta.model.produccion.dto.ReporteProduccionEtapaDTOs.*;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacionEvento;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCatVersion;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.manufacturing.snapshots.ManufacturingVersionRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaEventoRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionOperacionEventoRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Reglas del reporte informativo; no acredita persistencia JSONB ni atomicidad de completar. */
class ReporteProduccionEtapaServiceTest {
    private final ProductoRepo productos = mock(ProductoRepo.class);
    private final ManufacturingVersionRepo versiones = mock(ManufacturingVersionRepo.class);
    private final SeguimientoOrdenAreaEventoRepo eventosOp = mock(SeguimientoOrdenAreaEventoRepo.class);
    private final OrdenFabricacionOperacionEventoRepo eventosOf = mock(OrdenFabricacionOperacionEventoRepo.class);
    private final ReporteProduccionEtapaService service = new ReporteProduccionEtapaService(
            productos, versiones, eventosOp, eventosOf, new ObjectMapper());

    @Test
    void opIdentificaPrincipalYSemisIntegradosSinAtribuirLaFabricacionDeUnaOfIndependiente() {
        Terminado principal = principal();
        ManufacturingVersions versionPrincipal = version(principal, 101L, grafo("envasado", 30),
                "[{\"productoId\":\"S1\"},{\"productoId\":\"SOF\"}]");
        SemiTerminado semi = semi("S1", "Base integrada", "KG", false);
        SemiTerminado previo = semi("S2", "Premezcla integrada", "L", false);
        SemiTerminado independiente = semi("SOF", "Base de OF", "KG", true);
        Material material = new Material();
        material.setProductoId("M1");
        ManufacturingVersions versionSemi = version(semi, 102L, """
                {"nodes":[{"frontendId":"mezcla-a","nodeType":"PROCESO","areaOperativaId":20},
                          {"frontendId":"mezcla-b","nodeType":"PROCESO","areaOperativaId":20},
                          {"frontendId":"destino","nodeType":"TARGET"}],
                 "edges":[{"sourceFrontendId":"mezcla-a","targetFrontendId":"mezcla-b"},
                          {"sourceFrontendId":"mezcla-b","targetFrontendId":"destino"}]}
                """, "[{\"productoId\":\"S2\"},{\"productoId\":\"M1\"}]");
        ManufacturingVersions versionPrevia = version(previo, 103L, grafo("premezcla", 20), "[]");
        when(productos.findById("S1")).thenReturn(Optional.of(semi));
        when(productos.findById("S2")).thenReturn(Optional.of(previo));
        when(productos.findById("SOF")).thenReturn(Optional.of(independiente));
        when(productos.findById("M1")).thenReturn(Optional.of(material));
        when(versiones.findTopByProductoOrderByVersionNumberDesc(semi)).thenReturn(Optional.of(versionSemi));
        when(versiones.findTopByProductoOrderByVersionNumberDesc(previo)).thenReturn(Optional.of(versionPrevia));
        AreaOperativa soloOf = area(40, true);
        soloOf.setAlcanceEjecucion(AreaOperativa.AlcanceEjecucion.SOLO_OF);
        RutaProcesoCatVersion ruta = ruta(area(30, true), area(20, true), soloOf, area(-1, true));

        var configs = service.congelarOp(op(principal, versionPrincipal), ruta);

        assertThat(configs.get(1L).productos()).containsExactly(
                new Producto("PT", "Producto terminado", "UND", 101L, List.of("envasado"), true));
        assertThat(configs.get(2L).productos()).containsExactly(
                new Producto("S1", "Base integrada", "KG", 102L, List.of("mezcla-a", "mezcla-b"), false),
                new Producto("S2", "Premezcla integrada", "L", 103L, List.of("premezcla"), false));
        assertThat(configs.get(3L).requerido()).isFalse();
        assertThat(configs.get(4L).requerido()).isFalse();
        verify(versiones, never()).findTopByProductoOrderByVersionNumberDesc(independiente);
        // Cambios posteriores del catalogo o de la receta no reescriben el snapshot ya emitido.
        semi.setNombre("Nombre nuevo");
        semi.setTipoUnidades("G");
        versionSemi.setProcesoProduccionJson("{}");
        assertThat(configs.get(2L).productos().getFirst().productoNombre()).isEqualTo("Base integrada");
        assertThat(configs.get(2L).productos().getFirst().unidadMedida()).isEqualTo("KG");
        verifyNoInteractions(eventosOp, eventosOf);
    }

    @Test
    void deshabilitarReportesNoExigeRepararGrafosNiRecetasDeUnaOrdenLegada() {
        Terminado principal = principal();
        ManufacturingVersions version = version(principal, 101L, "JSON legado ausente", null);

        var config = service.congelarOp(op(principal, version), ruta(area(20, false))).get(1L);

        assertThat(config.requerido()).isFalse();
        assertThat(config.productos()).isEmpty();
        verifyNoInteractions(productos, versiones, eventosOp, eventosOf);
    }

    @Test
    void dosEtapasDeLaMismaAreaReportanSoloElProductoDelProcesoConfiguradoEnSuNodoDeRuta() {
        Terminado principal = principal();
        ManufacturingVersions versionPrincipal = version(principal, 101L,
                grafo("envasado", 20).replace("\"areaOperativaId\":20", "\"areaOperativaId\":20,\"procesoId\":22"),
                "[{\"productoId\":\"S1\"}]");
        SemiTerminado semi = semi("S1", "Base integrada", "KG", false);
        ManufacturingVersions versionSemi = version(semi, 102L,
                grafo("mezcla", 20).replace("\"areaOperativaId\":20", "\"areaOperativaId\":20,\"procesoId\":11"), "[]");
        when(productos.findById("S1")).thenReturn(Optional.of(semi));
        when(versiones.findTopByProductoOrderByVersionNumberDesc(semi)).thenReturn(Optional.of(versionSemi));
        RutaProcesoCatVersion ruta = ruta(area(20, true), area(20, true));
        ProcesoProduccion mezcla = new ProcesoProduccion();
        mezcla.setProcesoId(11);
        ProcesoProduccion envasado = new ProcesoProduccion();
        envasado.setProcesoId(22);
        ruta.getNodes().get(0).setProcesoProduccion(mezcla);
        ruta.getNodes().get(1).setProcesoProduccion(envasado);

        var configs = service.congelarOp(op(principal, versionPrincipal), ruta);

        assertThat(configs.get(1L).productos()).extracting(Producto::productoId).containsExactly("S1");
        assertThat(configs.get(2L).productos()).extracting(Producto::productoId).containsExactly("PT");
    }

    @Test
    void areaObligatoriaSinSalidaIdentificableNoRecibeUnProductoArbitrario() {
        Terminado principal = principal();
        ManufacturingVersions version = version(principal, 101L, grafo("envasado", 30), "[]");

        assertThatIllegalStateException().isThrownBy(
                () -> service.congelarOp(op(principal, version), ruta(area(20, true))))
                .withMessageContaining("Area 20").withMessageContaining("no identifican su producto y unidad");
    }

    @Test
    void ofDistingueNodosDeUnaMismaAreaYNoExplotaSusSemiterminadosDeEntrada() {
        SemiTerminado semi = semi("S1", "Base OF", "KG", true);
        ManufacturingVersions version = version(semi, 102L, """
                {"nodes":[{"frontendId":"calentar","nodeType":"PROCESO","areaOperativaId":20},
                          {"frontendId":"mezclar","nodeType":"PROCESO","areaOperativaId":20},
                          {"frontendId":"desconectado","nodeType":"PROCESO","areaOperativaId":20},
                          {"frontendId":"destino","nodeType":"TARGET"}],
                 "edges":[{"sourceFrontendId":"calentar","targetFrontendId":"mezclar"},
                          {"sourceFrontendId":"mezclar","targetFrontendId":"destino"}]}
                """, "[{\"productoId\":\"SEMI-ENTRADA\"}]");
        OrdenFabricacion orden = new OrdenFabricacion();
        orden.setSemiTerminado(semi);
        orden.setManufacturingVersion(version);

        var previstas = service.congelarOf(orden);
        Configuracion calentar = service.paraOperacionOf(area(20, true), "calentar", previstas);
        Configuracion mezclar = service.paraOperacionOf(area(20, true), "mezclar", previstas);

        assertThat(calentar.productos()).containsExactly(
                new Producto("S1", "Base OF", "KG", 102L, List.of("calentar"), true));
        assertThat(mezclar.productos().getFirst().nodosOrigen()).containsExactly("mezclar");
        assertThat(previstas).doesNotContainKey("desconectado");
        assertThat(service.paraOperacionOf(area(20, false), "nodo-legado", previstas).requerido()).isFalse();
        assertThatIllegalStateException().isThrownBy(
                () -> service.paraOperacionOf(area(20, true), "nodo-ajeno", previstas));
        verifyNoInteractions(productos, versiones, eventosOp, eventosOf);
    }

    @Test
    void unGrafoConCicloNoPermiteCongelarUnReporteAunqueExistaCaminoAlDestino() {
        SemiTerminado semi = semi("S1", "Base OF", "KG", true);
        ManufacturingVersions version = version(semi, 102L, """
                {"nodes":[{"frontendId":"a","nodeType":"PROCESO","areaOperativaId":20},
                          {"frontendId":"b","nodeType":"PROCESO","areaOperativaId":20},
                          {"frontendId":"destino","nodeType":"TARGET"}],
                 "edges":[{"sourceFrontendId":"a","targetFrontendId":"b"},
                          {"sourceFrontendId":"b","targetFrontendId":"a"},
                          {"sourceFrontendId":"b","targetFrontendId":"destino"}]}
                """, "[]");
        OrdenFabricacion orden = new OrdenFabricacion();
        orden.setSemiTerminado(semi);
        orden.setManufacturingVersion(version);

        assertThatIllegalStateException().isThrownBy(() -> service.congelarOf(orden))
                .withMessageContaining("ciclo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0001", "12.3400", "1.23000", "99999999999999.9999"})
    void aceptaCantidadRepresentableSinRedondearYConservaMetadatosCongelados(String cantidad) {
        BigDecimal medida = new BigDecimal(cantidad);

        var reportes = service.validar(configuracion(), List.of(new Solicitud("S1", medida, null)), null);

        assertThat(reportes).containsExactly(new Reporte("S1", "Base congelada", "KG", medida, null));
        assertThat(reportes.getFirst().cantidad()).isEqualTo(medida);
        verifyNoInteractions(productos, versiones, eventosOp, eventosOf);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-0.0001", "0.00001", "1.23456", "100000000000000"})
    void rechazaCantidadesAusentesCeroNegativasFueraDePrecisionORango(String cantidad) {
        BigDecimal medida = cantidad == null ? null : new BigDecimal(cantidad);

        assertThatIllegalArgumentException().isThrownBy(() -> service.validar(configuracion(),
                List.of(new Solicitud("S1", medida, "Un motivo no autoriza reportar cero")), null))
                .withMessageContaining("cantidad positiva");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("capturasIncompletasOContaminadas")
    void exigeCadaProductoDelSnapshotYRechazaDuplicadosYAjenos(String caso, List<Solicitud> solicitudes) {
        Configuracion dosProductos = new Configuracion(true, List.of(
                productoEsperado("S1", false), productoEsperado("S2", false)));

        assertThatIllegalArgumentException().as(caso).isThrownBy(
                () -> service.validar(dosProductos, solicitudes, null));
    }

    private static Stream<Arguments> capturasIncompletasOContaminadas() {
        Solicitud s1 = new Solicitud("S1", BigDecimal.ONE, null);
        Solicitud s2 = new Solicitud("S2", BigDecimal.TEN, null);
        return Stream.of(
                Arguments.of("sin capturas", List.of()),
                Arguments.of("falta un producto", List.of(s1)),
                Arguments.of("producto ajeno", List.of(s1, s2, new Solicitud("AJENO", BigDecimal.ONE, null))),
                Arguments.of("producto duplicado", List.of(s1, s2, s1)),
                Arguments.of("captura nula", Arrays.asList(s1, s2, null)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cierreDerivaSoloElProductoFinalYAdmiteUnaCapturaExplicitaCoincidente(boolean explicita) {
        Configuracion config = new Configuracion(true, List.of(
                productoEsperado("S1", false), productoEsperado("PT", true)));
        BigDecimal finalReal = new BigDecimal("8.5000");
        List<Solicitud> solicitudes = new ArrayList<>();
        solicitudes.add(new Solicitud("S1", new BigDecimal("9.1250"), null));
        if (explicita) solicitudes.add(new Solicitud("PT", new BigDecimal("8.5"), null));

        var reportes = service.validar(config, solicitudes, finalReal);

        assertThat(reportes).extracting(Reporte::cantidad)
                .containsExactly(new BigDecimal("9.1250"), finalReal);
    }

    @Test
    void cierreFinalNoCompletaUnaMedicionIntermediaFaltanteNiAceptaFinalContradictorio() {
        Configuracion config = new Configuracion(true, List.of(
                productoEsperado("S1", false), productoEsperado("PT", true)));

        assertThatIllegalArgumentException().isThrownBy(
                () -> service.validar(config, List.of(), BigDecimal.TEN));
        assertThatIllegalArgumentException().isThrownBy(() -> service.validar(config, List.of(
                new Solicitud("S1", BigDecimal.TEN, null),
                new Solicitud("PT", BigDecimal.ONE, null)), BigDecimal.TEN))
                .withMessageContaining("coincidir con el cierre");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void configuracionDeshabilitadaONulaNoImponeCamposLegadosPeroRechazaReportesExtra(boolean legadoNull) {
        Configuracion config = legadoNull ? null : new Configuracion(false, List.of());

        assertThat(service.validar(config, null, BigDecimal.TEN)).isNull();
        assertThatIllegalArgumentException().isThrownBy(() -> service.validar(config,
                List.of(new Solicitud("S1", BigDecimal.ONE, null)), null))
                .withMessageContaining("no tiene habilitado");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reaperturaConservaEvidenciaHistoricaYSoloElUltimoCompletadoPuedeSerVigente(boolean fabricacion) {
        List<Reporte> primero = List.of(new Reporte("S1", "Base original", "KG", new BigDecimal("5.0000"), null));
        List<Reporte> segundo = List.of(new Reporte("S1", "Base original", "KG", new BigDecimal("6.0000"), null));
        List<Registro> cerrada;
        List<Registro> reabierta;
        if (fabricacion) {
            OrdenFabricacionOperacion etapa = new OrdenFabricacionOperacion();
            etapa.setId(7L);
            etapa.setEstadoEnum(EstadoSeguimientoOrdenArea.COMPLETADO);
            var cierre1 = eventoOf(1L, primero);
            var correccion1 = eventoOf(2L, null);
            correccion1.setTipoEvento(TipoEventoSeguimiento.CORRECCION_ADMINISTRATIVA);
            correccion1.setEventoRevertido(cierre1);
            var cierre2 = eventoOf(3L, segundo);
            when(eventosOf.findByOperacion_IdOrderByFechaEventoAscIdAsc(7L))
                    .thenReturn(List.of(cierre1, correccion1, cierre2));
            cerrada = service.historial(etapa);
            var correccion2 = eventoOf(4L, null);
            correccion2.setTipoEvento(TipoEventoSeguimiento.CORRECCION_ADMINISTRATIVA);
            correccion2.setEventoRevertido(cierre2);
            etapa.setEstadoEnum(EstadoSeguimientoOrdenArea.ESPERA);
            when(eventosOf.findByOperacion_IdOrderByFechaEventoAscIdAsc(7L))
                    .thenReturn(List.of(cierre1, correccion1, cierre2, correccion2));
            reabierta = service.historial(etapa);
            assertThat(cierre1.getReportesProduccion()).isEqualTo(primero);
            assertThat(cierre2.getReportesProduccion()).isEqualTo(segundo);
        } else {
            SeguimientoOrdenArea etapa = new SeguimientoOrdenArea();
            etapa.setId(7L);
            etapa.setEstadoEnum(EstadoSeguimientoOrdenArea.COMPLETADO);
            var cierre1 = eventoOp(1L, primero);
            var correccion1 = eventoOp(2L, null);
            correccion1.setTipoEvento(TipoEventoSeguimiento.CORRECCION_ADMINISTRATIVA);
            correccion1.setEventoRevertido(cierre1);
            var cierre2 = eventoOp(3L, segundo);
            when(eventosOp.findBySeguimientoOrdenArea_IdOrderByFechaEventoAscIdAsc(7L))
                    .thenReturn(List.of(cierre1, correccion1, cierre2));
            cerrada = service.historial(etapa);
            var correccion2 = eventoOp(4L, null);
            correccion2.setTipoEvento(TipoEventoSeguimiento.CORRECCION_ADMINISTRATIVA);
            correccion2.setEventoRevertido(cierre2);
            etapa.setEstadoEnum(EstadoSeguimientoOrdenArea.ESPERA);
            when(eventosOp.findBySeguimientoOrdenArea_IdOrderByFechaEventoAscIdAsc(7L))
                    .thenReturn(List.of(cierre1, correccion1, cierre2, correccion2));
            reabierta = service.historial(etapa);
            assertThat(cierre1.getReportesProduccion()).isEqualTo(primero);
            assertThat(cierre2.getReportesProduccion()).isEqualTo(segundo);
        }

        assertThat(cerrada).extracting(Registro::eventoId).containsExactly(1L, 3L);
        assertThat(cerrada).extracting(Registro::vigente).containsExactly(false, true);
        assertThat(service.vigentes(cerrada)).isEqualTo(segundo);
        assertThat(reabierta).hasSize(2).allMatch(r -> !r.vigente());
        assertThat(service.vigentes(reabierta)).isEmpty();
        verify(eventosOp, never()).save(any());
        verify(eventosOf, never()).save(any());
        verifyNoInteractions(productos, versiones);
    }

    private Configuracion configuracion() {
        return new Configuracion(true, List.of(productoEsperado("S1", false)));
    }

    private static Producto productoEsperado(String id, boolean finalOrden) {
        return new Producto(id, "Base congelada", "KG", 102L, List.of("mezcla"), finalOrden);
    }

    private Terminado principal() {
        Terminado producto = new Terminado();
        producto.setProductoId("PT");
        producto.setNombre("Producto terminado");
        producto.setTipoUnidades("UND");
        return producto;
    }

    private SemiTerminado semi(String id, String nombre, String unidad, boolean requiereOf) {
        SemiTerminado semi = new SemiTerminado();
        semi.setProductoId(id);
        semi.setNombre(nombre);
        semi.setTipoUnidades(unidad);
        semi.setRequiereOrdenFabricacion(requiereOf);
        return semi;
    }

    private ManufacturingVersions version(exotic.app.planta.model.producto.Producto producto,
                                           long id, String grafo, String insumos) {
        ManufacturingVersions version = new ManufacturingVersions();
        version.setId(id);
        version.setProducto(producto);
        version.setProcesoProduccionJson(grafo);
        version.setInsumosJson(insumos);
        return version;
    }

    private String grafo(String nodo, int area) {
        return """
                {"nodes":[{"frontendId":"%s","nodeType":"PROCESO","areaOperativaId":%d},
                          {"frontendId":"destino","nodeType":"TARGET"}],
                 "edges":[{"sourceFrontendId":"%s","targetFrontendId":"destino"}]}
                """.formatted(nodo, area, nodo);
    }

    private AreaOperativa area(int id, boolean reporte) {
        AreaOperativa area = new AreaOperativa();
        area.setAreaId(id);
        area.setNombre("Area " + id);
        area.setRequiereReporteProduccionEtapa(reporte);
        return area;
    }

    private RutaProcesoCatVersion ruta(AreaOperativa... areas) {
        RutaProcesoCatVersion ruta = new RutaProcesoCatVersion();
        for (int i = 0; i < areas.length; i++) {
            RutaProcesoNode nodo = new RutaProcesoNode();
            nodo.setId((long) i + 1);
            nodo.setAreaOperativa(areas[i]);
            ruta.getNodes().add(nodo);
        }
        return ruta;
    }

    private OrdenProduccion op(Terminado producto, ManufacturingVersions version) {
        OrdenProduccion orden = new OrdenProduccion();
        orden.setProducto(producto);
        orden.setManufacturingVersion(version);
        return orden;
    }

    private User responsable() {
        User usuario = new User();
        usuario.setId(8L);
        usuario.setUsername("responsable-reporte");
        return usuario;
    }

    private SeguimientoOrdenAreaEvento eventoOp(long id, List<Reporte> reportes) {
        SeguimientoOrdenAreaEvento evento = new SeguimientoOrdenAreaEvento();
        evento.setId(id);
        evento.setFechaEvento(LocalDateTime.of(2026, 11, 20, 8, 0).plusHours(id));
        evento.setEstadoDestino(reportes == null ? SeguimientoOrdenArea.ESTADO_ESPERA : SeguimientoOrdenArea.ESTADO_COMPLETADO);
        evento.setActorTipo(ActorTipoEventoSeguimiento.USER);
        evento.setTipoEvento(TipoEventoSeguimiento.OPERATIVO);
        evento.setUsuario(responsable());
        evento.setReportesProduccion(reportes);
        return evento;
    }

    private OrdenFabricacionOperacionEvento eventoOf(long id, List<Reporte> reportes) {
        OrdenFabricacionOperacionEvento evento = new OrdenFabricacionOperacionEvento();
        evento.setId(id);
        evento.setFechaEvento(LocalDateTime.of(2026, 11, 20, 8, 0).plusHours(id));
        evento.setEstadoDestino(reportes == null ? SeguimientoOrdenArea.ESTADO_ESPERA : SeguimientoOrdenArea.ESTADO_COMPLETADO);
        evento.setActorTipo(ActorTipoEventoSeguimiento.USER);
        evento.setTipoEvento(TipoEventoSeguimiento.OPERATIVO);
        evento.setUsuario(responsable());
        evento.setReportesProduccion(reportes);
        return evento;
    }
}
