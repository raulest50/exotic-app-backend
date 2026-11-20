package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.activos.fijos.ActivoFijo;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.manufacturing.procesos.RecursoProduccion;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion;
import exotic.app.planta.repo.activos.fijos.ActivoFijoRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.master.configs.MasterDirectiveRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoteFabricacionEquipoServiceTest {
    private static final LocalDateTime TERMINACION = LocalDateTime.of(2026, 11, 19, 11, 30);
    private final ActivoFijoRepo activos = mock(ActivoFijoRepo.class);
    private final LoteRepo lotes = mock(LoteRepo.class);
    private final LoteFabricacionEquipoService service = new LoteFabricacionEquipoService(
            activos, lotes, mock(MasterDirectiveRepo.class), new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-11-19T16:30:00Z"), ZoneId.of("America/Bogota")));
    private OrdenFabricacion orden;
    private OrdenFabricacionOperacion salida;
    private Lote lote;
    private ActivoFijo equipo;

    @BeforeEach
    void recetaConSalidaRealYRecursosRestringidos() {
        ManufacturingVersions version = new ManufacturingVersions();
        // El material intermedio no convierte la primera operacion en terminal.
        version.setProcesoProduccionJson("""
                {"nodes":[
                  {"frontendId":"mezcla","nodeType":"PROCESO"},
                  {"frontendId":"granel","nodeType":"MATERIAL"},
                  {"frontendId":"salida","nodeType":"PROCESO","recursosProduccionIds":[7]},
                  {"frontendId":"resultado","nodeType":"MATERIAL"}],
                 "edges":[
                  {"sourceFrontendId":"mezcla","targetFrontendId":"granel"},
                  {"sourceFrontendId":"granel","targetFrontendId":"salida"},
                  {"sourceFrontendId":"salida","targetFrontendId":"resultado"}]}
                """);
        orden = new OrdenFabricacion();
        orden.setOrdenFabricacionId(42L);
        orden.setManufacturingVersion(version);
        orden.setPrefijoLoteReservado("AAA");
        orden.setConsecutivoLoteReservado(42L);
        orden.setFechaLoteOrigen("TERMINACION");
        orden.setFechaInicio(LocalDateTime.of(2026, 11, 18, 8, 0));
        AreaOperativa area = new AreaOperativa();
        area.setAreaId(9);
        area.setRecursosProduccion(new LinkedHashSet<>(List.of(recurso(7L), recurso(8L))));
        salida = new OrdenFabricacionOperacion();
        salida.setOrdenFabricacion(orden);
        salida.setAreaOperativa(area);
        salida.setFrontendNodeId("salida");
        salida.setRequiereEquipoLoteOf(true);
        lote = new Lote();
        lote.setId(10L);
        lote.setBatchNumber("AAA-0000042-26");
        equipo = new ActivoFijo();
        equipo.setId("EQUIPO-7");
        equipo.setEstado(0);
        equipo.setTipoActivo(ActivoFijo.TipoActivo.PRODUCCION);
        equipo.setTipoRecurso(recurso(7L));
        equipo.setCodigoLoteFabricacion("07");
    }

    @ParameterizedTest
    @CsvSource({"INICIO,181126,2026-11-18", "TERMINACION,191126,2026-11-19"})
    void codigoDefinitivoUsaLaReservaElEquipoYLaPoliticaCongelada(
            String politica, String fechaCodigo, LocalDate fechaEsperada) {
        orden.setFechaLoteOrigen(politica);
        when(activos.findByIdForLote("EQUIPO-7")).thenReturn(Optional.of(equipo));

        service.completar(salida, lote, " EQUIPO-7 ", TERMINACION);

        assertThat(lote.getBatchNumber()).isEqualTo("AAA-00042-07-" + fechaCodigo);
        assertThat(lote.getProductionDate()).isEqualTo(fechaEsperada);
        assertThat(lote.getId()).isEqualTo(10L);
        assertThat(orden.getActivoLote()).isSameAs(equipo);
        assertThat(orden.getCodigoEquipoLote()).isEqualTo("07");
        assertThat(orden.getConsecutivoLoteReservado()).isEqualTo(42L);
    }

    @Test
    void requisitoCongeladoExigeEquipoSinAlterarElLote() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> service.completar(salida, lote, null, TERMINACION))
                .withMessageContaining("Seleccione el equipo");

        assertThat(lote.getBatchNumber()).isEqualTo("AAA-0000042-26");
        assertThat(lote.getProductionDate()).isNull();
        verifyNoInteractions(activos, lotes);
    }

    @Test
    void ultimaOperacionReportadaNoEquivaleAUnaSalidaDelGrafo() {
        salida.setFrontendNodeId("mezcla");

        assertThat(service.operacionesSalida(orden)).containsExactly("salida");
        assertThatIllegalArgumentException().isThrownBy(
                () -> service.completar(salida, lote, "EQUIPO-7", TERMINACION))
                .withMessageContaining("unica operacion de salida");
        verifyNoInteractions(activos, lotes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"inactivo", "tipo", "recurso", "codigo", "inexistente"})
    void rechazaUnActivoInvalidoSinRenumerarNiAsignarFecha(String causa) {
        switch (causa) {
            case "inactivo" -> equipo.setEstado(1);
            case "tipo" -> equipo.setTipoActivo(null);
            case "recurso" -> equipo.setTipoRecurso(recurso(8L));
            case "codigo" -> equipo.setCodigoLoteFabricacion("7");
        }
        when(activos.findByIdForLote("EQUIPO-7"))
                .thenReturn("inexistente".equals(causa) ? Optional.empty() : Optional.of(equipo));

        assertThatIllegalArgumentException().isThrownBy(
                () -> service.completar(salida, lote, "EQUIPO-7", TERMINACION));

        assertThat(lote.getBatchNumber()).isEqualTo("AAA-0000042-26");
        assertThat(lote.getProductionDate()).isNull();
        assertThat(orden.getActivoLote()).isNull();
        verifyNoInteractions(lotes);
    }

    @Test
    void noRenombraReferenciasManualesNiHistoricasAunqueSeReporteEquipo() {
        orden.setConsecutivoLoteReservado(null);
        orden.setPrefijoLoteReservado(null);
        orden.setFechaLoteOrigen(null);
        lote.setBatchNumber("REFERENCIA-MANUAL-2025");
        when(activos.findByIdForLote("EQUIPO-7")).thenReturn(Optional.of(equipo));

        service.completar(salida, lote, "EQUIPO-7", TERMINACION);

        assertThat(lote.getBatchNumber()).isEqualTo("REFERENCIA-MANUAL-2025");
        assertThat(lote.getProductionDate()).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(orden.getCodigoEquipoLote()).isEqualTo("07");
    }

    @Test
    void adopcionGradualPermiteSalidaSinEquipoCuandoElSnapshotNoLoExige() {
        salida.setRequiereEquipoLoteOf(false);

        service.completar(salida, lote, null, TERMINACION);

        assertThat(lote.getBatchNumber()).isEqualTo("AAA-0000042-26");
        assertThat(lote.getProductionDate()).isEqualTo(LocalDate.of(2026, 11, 19));
        verifyNoInteractions(activos, lotes);
    }

    @Test
    void unCodigoDefinitivoDeOtroLoteNoPuedeReutilizarse() {
        Lote otro = new Lote();
        otro.setId(99L);
        when(activos.findByIdForLote("EQUIPO-7")).thenReturn(Optional.of(equipo));
        when(lotes.findByBatchNumber("AAA-00042-07-191126")).thenReturn(otro);

        assertThatIllegalArgumentException().isThrownBy(
                () -> service.completar(salida, lote, "EQUIPO-7", TERMINACION))
                .withMessageContaining("ya existe");

        assertThat(lote.getBatchNumber()).isEqualTo("AAA-0000042-26");
        assertThat(orden.getActivoLote()).isNull();
    }

    private RecursoProduccion recurso(long id) {
        RecursoProduccion recurso = new RecursoProduccion();
        recurso.setId(id);
        recurso.setNombre("Recurso " + id);
        return recurso;
    }
}
