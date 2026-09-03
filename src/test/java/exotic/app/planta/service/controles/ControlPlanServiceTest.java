package exotic.app.planta.service.controles;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.calidad.ControlProcesoPlantilla;
import exotic.app.planta.model.controles.*;
import exotic.app.planta.model.controles.dto.ControlDTOs.*;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.controles.*;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ControlPlanServiceTest {
    private PlanControlRepo planRepo;
    private VersionPlanControlRepo versionRepo;
    private MagnitudControlRepo magnitudRepo;
    private UnidadControlRepo unidadRepo;
    private ProductoRepo productoRepo;
    private ControlPlanService service;
    private MagnitudControl peso;
    private UnidadControl gramo;

    @BeforeEach
    void setUp() {
        planRepo = mock(PlanControlRepo.class);
        versionRepo = mock(VersionPlanControlRepo.class);
        magnitudRepo = mock(MagnitudControlRepo.class);
        unidadRepo = mock(UnidadControlRepo.class);
        productoRepo = mock(ProductoRepo.class);
        service = new ControlPlanService(planRepo, versionRepo, magnitudRepo, unidadRepo,
                productoRepo, mock(CategoriaRepo.class), mock(AreaProduccionRepo.class),
                mock(ProcesoProduccionRepo.class));
        peso = new MagnitudControl();
        peso.setId(1L); peso.setCodigo("PESO"); peso.setNombre("Peso");
        peso.setSimbolo("m"); peso.setDimension("MASA"); peso.setActivo(true);
        gramo = new UnidadControl();
        gramo.setId(2L); gramo.setCodigo("G"); gramo.setNombre("Gramo");
        gramo.setSimbolo("g"); gramo.setDimension("MASA"); gramo.setActivo(true);
        when(magnitudRepo.findById(1L)).thenReturn(Optional.of(peso));
        when(unidadRepo.findById(2L)).thenReturn(Optional.of(gramo));
        Terminado producto = new Terminado();
        producto.setProductoId("PT-1"); producto.setNombre("Producto");
        when(productoRepo.findById("PT-1")).thenReturn(Optional.of(producto));
    }

    @Test
    void crear_imponeAmbitoYResponsablesDeProceso() {
        AtomicReference<PlanControl> guardado = new AtomicReference<>();
        when(planRepo.saveAndFlush(any())).thenAnswer(invocation -> {
            PlanControl plan = invocation.getArgument(0);
            plan.setId(10L); guardado.set(plan); return plan;
        });
        when(versionRepo.saveAndFlush(any())).thenAnswer(invocation -> {
            VersionPlanControl version = invocation.getArgument(0);
            version.setId(20L); return version;
        });
        when(planRepo.findByIdAndAmbito(10L, AmbitoControl.PROCESO))
                .thenAnswer(invocation -> Optional.of(guardado.get()));

        PlanResponse response = service.crear(AmbitoControl.PROCESO, usuario(), request(
                new BigDecimal("10.00000000"), new BigDecimal("9.50000000"),
                new BigDecimal("10.50000000")));

        assertEquals(AmbitoControl.PROCESO, response.ambito());
        assertEquals("DIRECCION_TECNICA_Y_PLANTA",
                response.versiones().getFirst().responsableEjecucion());
        assertEquals("DIRECCION_TECNICA_Y_PLANTA",
                response.versiones().getFirst().responsableDisposicion());
        assertEquals("m", response.versiones().getFirst().caracteristicas().getFirst()
                .magnitud().simbolo());
    }

    @Test
    void crear_rechazaNumericaSinLimites() {
        prepararGuardadoMinimo();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.crear(AmbitoControl.CALIDAD, usuario(), request(null, null, null)));
        assertTrue(error.getMessage().contains("al menos un limite"));
    }

    @Test
    void crear_rechazaNumeroQueExcedeNumeric20_8() {
        prepararGuardadoMinimo();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.crear(AmbitoControl.CALIDAD, usuario(), request(null,
                        new BigDecimal("1000000000000.00000000"), null)));
        assertTrue(error.getMessage().contains("NUMERIC(20,8)"));
    }

    @Test
    void crear_rechazaRevisionFinalEnControlDeProceso() {
        prepararGuardadoMinimo();
        PlanWriteRequest base = request(null, BigDecimal.ZERO, BigDecimal.ONE);
        AplicabilidadWriteRequest invalida = new AplicabilidadWriteRequest(
                "PT-1", null, TipoOrdenControl.OP, PuntoAplicacionControl.LOTE_FINAL,
                null, null, MomentoControl.REVISION_FINAL, PuntoExigenciaControl.LIBERACION, List.of());
        PlanWriteRequest request = new PlanWriteRequest(base.codigo(), base.nombre(), base.proposito(),
                base.motivoCambio(), List.of(invalida), base.caracteristicas());
        assertThrows(IllegalArgumentException.class,
                () -> service.crear(AmbitoControl.PROCESO, usuario(), request));
    }

    @Test
    void crear_aceptaSalidaIntermediaYRechazaMateriaPrima() {
        SemiTerminado salidaIntermedia = new SemiTerminado();
        salidaIntermedia.setProductoId("SEMI-1");
        salidaIntermedia.setNombre("Base intermedia");
        Material materiaPrima = new Material();
        materiaPrima.setProductoId("MP-1");
        materiaPrima.setNombre("Materia prima");
        when(productoRepo.findById("SEMI-1")).thenReturn(Optional.of(salidaIntermedia));
        when(productoRepo.findById("MP-1")).thenReturn(Optional.of(materiaPrima));
        AtomicReference<PlanControl> guardado = new AtomicReference<>();
        when(planRepo.saveAndFlush(any())).thenAnswer(invocation -> {
            PlanControl plan = invocation.getArgument(0);
            plan.setId(10L);
            guardado.set(plan);
            return plan;
        });
        when(versionRepo.saveAndFlush(any())).thenAnswer(invocation -> {
            VersionPlanControl version = invocation.getArgument(0);
            version.setId(20L);
            return version;
        });
        when(planRepo.findByIdAndAmbito(10L, AmbitoControl.PROCESO))
                .thenAnswer(invocation -> Optional.of(guardado.get()));

        PlanResponse aceptado = service.crear(AmbitoControl.PROCESO, usuario(),
                requestParaProducto("SEMI-1"));

        assertEquals("SEMI-1", aceptado.versiones().getFirst()
                .aplicabilidades().getFirst().productoId());
        assertThrows(IllegalArgumentException.class, () -> service.crear(
                AmbitoControl.PROCESO, usuario(), requestParaProducto("MP-1")));
    }

    @Test
    void crear_rechazaCombinacionesIncompatiblesDeMomentoPuntoYExigencia() {
        PlanWriteRequest base = request(null, BigDecimal.ZERO, BigDecimal.ONE);
        AplicabilidadWriteRequest cierreSobreLote = new AplicabilidadWriteRequest(
                "PT-1", null, TipoOrdenControl.OP, PuntoAplicacionControl.LOTE_FINAL,
                null, null, MomentoControl.DURANTE_FABRICACION,
                PuntoExigenciaControl.CIERRE_ETAPA, List.of());
        AplicabilidadWriteRequest revisionQueBloqueaEnvio = new AplicabilidadWriteRequest(
                "PT-1", null, TipoOrdenControl.OP, PuntoAplicacionControl.LOTE_FINAL,
                null, null, MomentoControl.REVISION_FINAL,
                PuntoExigenciaControl.ENVIO_CALIDAD, List.of());
        AplicabilidadWriteRequest salidaSinContexto = new AplicabilidadWriteRequest(
                "PT-1", null, TipoOrdenControl.OP, PuntoAplicacionControl.SALIDA_OPERACION,
                null, null, MomentoControl.DURANTE_FABRICACION,
                PuntoExigenciaControl.INFORMATIVO, List.of());
        AplicabilidadWriteRequest loteConContextoOperativo = new AplicabilidadWriteRequest(
                "PT-1", null, TipoOrdenControl.OP, PuntoAplicacionControl.LOTE_FINAL,
                10, 20, MomentoControl.DURANTE_FABRICACION,
                PuntoExigenciaControl.INFORMATIVO, List.of());

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> service.crear(AmbitoControl.CALIDAD, usuario(),
                                conAplicabilidad(base, cierreSobreLote))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> service.crear(AmbitoControl.CALIDAD, usuario(),
                                conAplicabilidad(base, revisionQueBloqueaEnvio))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> service.crear(AmbitoControl.CALIDAD, usuario(),
                                conAplicabilidad(base, salidaSinContexto))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> service.crear(AmbitoControl.CALIDAD, usuario(),
                                conAplicabilidad(base, loteConContextoOperativo))));
    }

    @Test
    void guardarBorrador_rechazaEdicionDirectaDeVersionLegada() {
        PlanControl plan = planExistente();
        VersionPlanControl legacyDraft = new VersionPlanControl();
        legacyDraft.setId(20L);
        legacyDraft.setPlan(plan);
        legacyDraft.setNumero(1);
        legacyDraft.setEstado(EstadoVersionPlanControl.BORRADOR);
        ControlProcesoPlantilla legacy = new ControlProcesoPlantilla();
        legacy.setId(30L);
        legacyDraft.setLegacyPlantilla(legacy);
        when(planRepo.findByIdAndAmbitoForUpdate(10L, AmbitoControl.PROCESO))
                .thenReturn(Optional.of(plan));
        when(versionRepo.findFirstByPlan_IdAndEstado(10L, EstadoVersionPlanControl.BORRADOR))
                .thenReturn(Optional.of(legacyDraft));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.guardarBorrador(
                        AmbitoControl.PROCESO, usuario(), 10L,
                        request(BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("20"))));

        assertTrue(error.getMessage().contains("adaptador temporal"));
        verify(versionRepo, never()).saveAndFlush(any());
    }

    @Test
    void publicarNativa_requiereRetirarPrimeroLaVigenteLegada() {
        PlanControl plan = planExistente();
        VersionPlanControl nativeDraft = new VersionPlanControl();
        nativeDraft.setId(21L);
        nativeDraft.setPlan(plan);
        nativeDraft.setNumero(2);
        nativeDraft.setEstado(EstadoVersionPlanControl.BORRADOR);
        VersionPlanControl legacyCurrent = new VersionPlanControl();
        legacyCurrent.setId(20L);
        legacyCurrent.setPlan(plan);
        legacyCurrent.setNumero(1);
        legacyCurrent.setEstado(EstadoVersionPlanControl.VIGENTE);
        ControlProcesoPlantilla legacy = new ControlProcesoPlantilla();
        legacy.setId(30L);
        legacyCurrent.setLegacyPlantilla(legacy);
        when(planRepo.findByIdAndAmbitoForUpdate(10L, AmbitoControl.PROCESO))
                .thenReturn(Optional.of(plan));
        when(versionRepo.findByIdAndPlan_Ambito(21L, AmbitoControl.PROCESO))
                .thenReturn(Optional.of(nativeDraft));
        when(versionRepo.findFirstByPlan_IdAndEstado(10L, EstadoVersionPlanControl.VIGENTE))
                .thenReturn(Optional.of(legacyCurrent));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.publicar(AmbitoControl.PROCESO, usuario(), 10L, 21L));

        assertTrue(error.getMessage().contains("Retire primero"));
        verify(versionRepo, never()).saveAndFlush(any());
    }

    @Test
    void publicarUsaElMismoInstanteParaRetiroYEntradaEnVigencia() {
        PlanControl plan = planExistente();
        VersionPlanControl vigente = new VersionPlanControl();
        vigente.setId(20L);
        vigente.setPlan(plan);
        vigente.setNumero(1);
        vigente.setEstado(EstadoVersionPlanControl.VIGENTE);
        VersionPlanControl borrador = new VersionPlanControl();
        borrador.setId(21L);
        borrador.setPlan(plan);
        borrador.setNumero(2);
        borrador.setEstado(EstadoVersionPlanControl.BORRADOR);
        AplicabilidadPlanControl aplicabilidad = new AplicabilidadPlanControl();
        aplicabilidad.setVersion(borrador);
        aplicabilidad.setMomento(MomentoControl.DURANTE_FABRICACION);
        aplicabilidad.setPuntoAplicacion(PuntoAplicacionControl.LOTE_FINAL);
        borrador.getAplicabilidades().add(aplicabilidad);
        CaracteristicaPlanControl caracteristica = new CaracteristicaPlanControl();
        caracteristica.setVersion(borrador);
        caracteristica.setMagnitud(peso);
        caracteristica.setUnidad(gramo);
        caracteristica.setMagnitudCodigoSnapshot(peso.getCodigo());
        caracteristica.setMagnitudNombreSnapshot(peso.getNombre());
        caracteristica.setMagnitudSimboloSnapshot(peso.getSimbolo());
        caracteristica.setUnidadCodigoSnapshot(gramo.getCodigo());
        caracteristica.setUnidadNombreSnapshot(gramo.getNombre());
        caracteristica.setUnidadSimboloSnapshot(gramo.getSimbolo());
        borrador.getCaracteristicas().add(caracteristica);
        plan.getVersiones().add(vigente);
        plan.getVersiones().add(borrador);
        when(planRepo.findByIdAndAmbitoForUpdate(10L, AmbitoControl.PROCESO))
                .thenReturn(Optional.of(plan));
        when(versionRepo.findByIdAndPlan_Ambito(21L, AmbitoControl.PROCESO))
                .thenReturn(Optional.of(borrador));
        when(versionRepo.findFirstByPlan_IdAndEstado(10L, EstadoVersionPlanControl.VIGENTE))
                .thenReturn(Optional.of(vigente));
        when(versionRepo.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Clock anterior = AppTime.clock();
        AtomicLong llamadas = new AtomicLong();
        AppTime.setClock(new Clock() {
            @Override public ZoneId getZone() { return anterior.getZone(); }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() {
                return Instant.parse("2026-09-02T15:00:00Z")
                        .plusSeconds(llamadas.getAndIncrement());
            }
        });
        try {
            service.publicar(AmbitoControl.PROCESO, usuario(), 10L, 21L);
        } finally {
            AppTime.setClock(anterior);
        }

        assertEquals(vigente.getRetiradaEn(), borrador.getPublicadaEn());
    }

    private void prepararGuardadoMinimo() {
        when(planRepo.saveAndFlush(any())).thenAnswer(invocation -> {
            PlanControl p = invocation.getArgument(0); p.setId(10L); return p;
        });
    }

    private PlanWriteRequest request(BigDecimal objetivo, BigDecimal inferior, BigDecimal superior) {
        return new PlanWriteRequest("PC-PESO", "Peso de envase", "AJUSTE_PROCESO", "Inicial",
                List.of(new AplicabilidadWriteRequest("PT-1", null, TipoOrdenControl.OP,
                        PuntoAplicacionControl.LOTE_FINAL, null, null,
                        MomentoControl.DURANTE_FABRICACION, PuntoExigenciaControl.INFORMATIVO, List.of())),
                List.of(new CaracteristicaWriteRequest("Peso", TipoCaracteristicaControl.NUMERICA,
                        1L, 2L, 1, 1, 1, 8, objetivo, inferior, superior, null)));
    }

    private PlanWriteRequest conAplicabilidad(
            PlanWriteRequest base, AplicabilidadWriteRequest aplicabilidad) {
        return new PlanWriteRequest(base.codigo(), base.nombre(), base.proposito(),
                base.motivoCambio(), List.of(aplicabilidad), base.caracteristicas());
    }

    private PlanWriteRequest requestParaProducto(String productoId) {
        PlanWriteRequest base = request(BigDecimal.TEN, new BigDecimal("9.5"),
                new BigDecimal("10.5"));
        AplicabilidadWriteRequest aplicabilidad = new AplicabilidadWriteRequest(
                productoId, null, TipoOrdenControl.AMBAS, PuntoAplicacionControl.LOTE_FINAL,
                null, null, MomentoControl.DURANTE_FABRICACION,
                PuntoExigenciaControl.INFORMATIVO, List.of());
        return conAplicabilidad(base, aplicabilidad);
    }

    private User usuario() {
        User user = new User(); user.setId(5L); user.setUsername("tecnico"); return user;
    }

    private PlanControl planExistente() {
        PlanControl plan = new PlanControl();
        plan.setId(10L);
        plan.setCodigo("PC-PESO");
        plan.setNombre("Peso de envase");
        plan.setAmbito(AmbitoControl.PROCESO);
        return plan;
    }
}
