package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.ActorTipoEventoSeguimiento;
import exotic.app.planta.model.produccion.EstadoDispensacionMateriales;
import exotic.app.planta.model.produccion.EstadoSeguimientoOrdenArea;
import exotic.app.planta.model.produccion.PoliticaDispensacionInicio;
import exotic.app.planta.model.produccion.TipoEventoSeguimiento;
import exotic.app.planta.model.produccion.fabricacion.EstadoOrdenFabricacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacionEvento;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.master.configs.SuperMasterConfigRepo;
import exotic.app.planta.repo.master.configs.SuperMasterVerificationCodeRepo;
import exotic.app.planta.repo.producto.SemiTerminadoRepo;
import exotic.app.planta.repo.producto.manufacturing.snapshots.ManufacturingVersionRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionOperacionEventoRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionOperacionRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.resource.produccion.DespejeLineaResource;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.commons.EmailService;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import exotic.app.planta.service.master.configs.SuperMasterOpsService;
import exotic.app.planta.service.produccion.SeguimientoOrdenAreaService.DispensacionRetroactividadDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Reglas del servicio y del resumen OP/OF, con repositorios simulados.
 * No valida el predicado JPQL ni la exclusion mutua real de PostgreSQL.
 */
class OrdenFabricacionRetroactividadTest {
    private static final Long OF_ID = 42L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-17T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 17, 10, 0);
    private static final LocalDateTime FECHA_PREVIA = AHORA.minusDays(1);

    private OrdenFabricacionRepo ordenRepo;
    private OrdenFabricacionOperacionRepo operacionRepo;
    private OrdenFabricacionOperacionEventoRepo eventoRepo;
    private MasterDirectiveService directivas;
    private BatchRecordProjectionQueueService proyeccion;
    private OrdenFabricacionOperacionService operacionService;
    private TransaccionAlmacenHeaderRepo transaccionRepo;
    private LoteRepo loteRepo;
    private EntityManager entityManager;
    private OrdenFabricacionService ordenService;
    private User actor;

    @BeforeEach
    void prepararServiciosSinAplicacionNiBaseDeDatos() {
        ordenRepo = mock(OrdenFabricacionRepo.class);
        operacionRepo = mock(OrdenFabricacionOperacionRepo.class);
        eventoRepo = mock(OrdenFabricacionOperacionEventoRepo.class);
        directivas = mock(MasterDirectiveService.class);
        proyeccion = mock(BatchRecordProjectionQueueService.class);
        operacionService = mock(OrdenFabricacionOperacionService.class);
        transaccionRepo = mock(TransaccionAlmacenHeaderRepo.class);
        loteRepo = mock(LoteRepo.class);
        entityManager = mock(EntityManager.class);
        actor = new User();
        actor.setId(7L);
        actor.setUsername("super_master");
        ordenService = new OrdenFabricacionService(
                ordenRepo, mock(SemiTerminadoRepo.class), mock(ManufacturingVersionRepo.class),
                loteRepo, mock(UserRepository.class), mock(BatchRecordRepo.class),
                mock(VencimientoLoteService.class), proyeccion, directivas, operacionService,
                eventoRepo, transaccionRepo, mock(LoteManufacturaNumeroService.class), CLOCK,
                operacionRepo, entityManager);
    }

    @Test
    void directivaApagadaNoBloqueaNiModificaUnaOf() {
        when(directivas.isDispensacionNoBloqueaInicioProduccion()).thenReturn(false);

        assertThat(ordenService.aplicarDispensacionNoBloqueanteRetroactiva(OF_ID, actor)).isFalse();

        verifyNoInteractions(ordenRepo, operacionRepo, eventoRepo, proyeccion, entityManager,
                operacionService, transaccionRepo, loteRepo);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoDispensacionMateriales.class, names = {"PENDIENTE", "PARCIAL", "COMPLETA"})
    void cambiaSoloPoliticaConservandoEntregasLiberacionYOperaciones(EstadoDispensacionMateriales estadoFisico) {
        OrdenFabricacion orden = candidata(estadoFisico);
        OrdenFabricacionOperacion primera = operacion(orden, 101L, EstadoSeguimientoOrdenArea.ESPERA);
        OrdenFabricacionOperacion segunda = operacion(orden, 102L, EstadoSeguimientoOrdenArea.COLA);
        primera.setFechaVisible(FECHA_PREVIA);
        prepararCandidata(orden, List.of(primera, segunda));

        assertThat(ordenService.aplicarDispensacionNoBloqueanteRetroactiva(OF_ID, actor)).isTrue();

        assertThat(orden.getPoliticaDispensacionInicio()).isEqualTo(PoliticaDispensacionInicio.NO_BLOQUEANTE);
        assertThat(orden.getFechaAplicacionPoliticaDispensacion()).isEqualTo(AHORA);
        assertThat(orden.getEstadoDispensacionMateriales()).isEqualTo(
                estadoFisico == EstadoDispensacionMateriales.PENDIENTE
                        ? EstadoDispensacionMateriales.LIBERADA_SIN_DISPENSACION : estadoFisico);
        assertThat(orden.getEstado()).isEqualTo(EstadoOrdenFabricacion.PLANIFICADA);
        assertThat(orden.getFechaLanzamiento()).isEqualTo(AHORA.plusDays(2));
        assertThat(orden.getLiberadaEn()).isNull();
        assertThat(orden.getFechaInicio()).isNull();
        assertThat(orden.getFechaFinal()).isNull();
        assertThat(primera.getEstadoEnum()).isEqualTo(EstadoSeguimientoOrdenArea.ESPERA);
        assertThat(segunda.getEstadoEnum()).isEqualTo(EstadoSeguimientoOrdenArea.COLA);
        assertThat(primera.getFechaVisible()).isEqualTo(FECHA_PREVIA);
        assertThat(segunda.getFechaVisible()).isNull();
        for (OrdenFabricacionOperacion operacion : List.of(primera, segunda)) {
            assertThat(operacion.getFechaEstadoActual()).isEqualTo(FECHA_PREVIA);
            assertThat(operacion.getFechaCompletado()).isNull();
            assertThat(operacion.getUsuarioReporta()).isNull();
        }
        verify(ordenRepo).save(orden);
        verify(operacionRepo, never()).save(any(OrdenFabricacionOperacion.class));
        verify(proyeccion).solicitarActualizacion(orden);
        verifyNoInteractions(operacionService, transaccionRepo, loteRepo);

        ArgumentCaptor<OrdenFabricacionOperacionEvento> eventos =
                ArgumentCaptor.forClass(OrdenFabricacionOperacionEvento.class);
        verify(eventoRepo, times(2)).save(eventos.capture());
        assertThat(eventos.getAllValues()).extracting(OrdenFabricacionOperacionEvento::getOperacion)
                .containsExactly(primera, segunda);
        assertThat(eventos.getAllValues()).allSatisfy(evento -> {
            assertThat(evento.getEstadoOrigen()).isEqualTo(evento.getOperacion().getEstado());
            assertThat(evento.getEstadoDestino()).isEqualTo(evento.getOperacion().getEstado());
            assertThat(evento.getActorTipo()).isEqualTo(ActorTipoEventoSeguimiento.SYSTEM);
            assertThat(evento.getTipoEvento()).isEqualTo(TipoEventoSeguimiento.SISTEMA);
            assertThat(evento.getUsuario()).isSameAs(actor);
            assertThat(evento.getFechaEvento()).isEqualTo(AHORA);
            assertThat(evento.getNota()).isNotBlank();
        });
    }

    @Test
    void revalidaTrasRefrescarYOmiteUnaCandidataQueYaInicio() {
        OrdenFabricacion orden = candidata(EstadoDispensacionMateriales.PARCIAL);
        when(directivas.isDispensacionNoBloqueaInicioProduccion()).thenReturn(true);
        when(ordenRepo.findByIdForUpdate(OF_ID)).thenReturn(Optional.of(orden));
        when(candidatasRepo(null)).thenReturn(List.of(OF_ID));
        when(candidatasRepo(OF_ID)).thenReturn(List.of());
        // Simula que el refresco observa trabajo registrado despues del preview.
        doAnswer(invocation -> {
            orden.setEstado(EstadoOrdenFabricacion.EN_EJECUCION);
            orden.setFechaInicio(FECHA_PREVIA);
            return null;
        }).when(entityManager).refresh(orden, LockModeType.PESSIMISTIC_WRITE);

        assertThat(ordenService.listarCandidatasDispensacionNoBloqueante()).containsExactly(OF_ID);
        assertThat(ordenService.aplicarDispensacionNoBloqueanteRetroactiva(OF_ID, actor)).isFalse();

        assertThat(orden.getEstado()).isEqualTo(EstadoOrdenFabricacion.EN_EJECUCION);
        assertThat(orden.getFechaInicio()).isEqualTo(FECHA_PREVIA);
        assertThat(orden.getPoliticaDispensacionInicio()).isEqualTo(PoliticaDispensacionInicio.BLOQUEANTE);
        assertThat(orden.getFechaAplicacionPoliticaDispensacion()).isNull();
        assertThat(orden.getEstadoDispensacionMateriales()).isEqualTo(EstadoDispensacionMateriales.PARCIAL);
        InOrder secuencia = inOrder(ordenRepo, entityManager);
        secuencia.verify(ordenRepo).findByIdForUpdate(OF_ID);
        secuencia.verify(entityManager).flush();
        secuencia.verify(entityManager).refresh(orden, LockModeType.PESSIMISTIC_WRITE);
        secuencia.verify(ordenRepo).findIdsCandidatasDispensacionNoBloqueante(
                OF_ID, PoliticaDispensacionInicio.BLOQUEANTE,
                List.of(EstadoOrdenFabricacion.PLANIFICADA, EstadoOrdenFabricacion.LIBERADA),
                List.of(EstadoSeguimientoOrdenArea.COLA.getCode(), EstadoSeguimientoOrdenArea.ESPERA.getCode()),
                ActorTipoEventoSeguimiento.USER, TipoEventoSeguimiento.OPERATIVO,
                EstadoSeguimientoOrdenArea.EN_PROCESO.getCode());
        verify(ordenRepo, never()).save(any(OrdenFabricacion.class));
        verifyNoInteractions(operacionRepo, eventoRepo, proyeccion, operacionService, transaccionRepo, loteRepo);
    }

    @Test
    void reintentoNoDuplicaAplicacionNiAuditoriaCuandoDejaDeSerCandidata() {
        OrdenFabricacion orden = candidata(EstadoDispensacionMateriales.PENDIENTE);
        OrdenFabricacionOperacion operacion = operacion(orden, 101L, EstadoSeguimientoOrdenArea.COLA);
        prepararCandidata(orden, List.of(operacion));
        when(candidatasRepo(OF_ID)).thenReturn(List.of(OF_ID), List.of());

        assertThat(ordenService.aplicarDispensacionNoBloqueanteRetroactiva(OF_ID, actor)).isTrue();
        assertThat(ordenService.aplicarDispensacionNoBloqueanteRetroactiva(OF_ID, actor)).isFalse();

        verify(ordenRepo).save(orden);
        verify(eventoRepo).save(any(OrdenFabricacionOperacionEvento.class));
        verify(proyeccion).solicitarActualizacion(orden);
        assertThat(operacion.getEstadoEnum()).isEqualTo(EstadoSeguimientoOrdenArea.COLA);
    }

    @Test
    void previewConSoloOfEsEjecutableYConservaCamposOpEnCero() {
        SeguimientoOrdenAreaService seguimiento = mock(SeguimientoOrdenAreaService.class);
        OrdenFabricacionService fabricacion = mock(OrdenFabricacionService.class);
        DispensacionRetroactividadDTO resumenOp = resumenOp(true, 0, 0, 0);
        when(seguimiento.previewRetroactividadDispensacionNoBloqueante()).thenReturn(resumenOp);
        when(fabricacion.listarCandidatasDispensacionNoBloqueante()).thenReturn(List.of(OF_ID));

        DispensacionRetroactividadDTO resultado = superMaster(seguimiento, fabricacion)
                .previewRetroactividadDispensacionNoBloqueante();

        assertThat(resultado.isEjecutable()).isTrue();
        assertThat(resultado.getOrdenesCandidatas()).isZero();
        assertThat(resultado.getOrdenesAplicadas()).isZero();
        assertThat(resultado.getOrdenesOmitidas()).isZero();
        assertThat(resultado.getOrdenIdsAplicadas()).isEmpty();
        assertThat(resultado.getOrdenesFabricacionCandidatas()).isEqualTo(1);
        assertThat(resultado.getOrdenesFabricacionAplicadas()).isZero();
        assertThat(resultado.getOrdenFabricacionIdsAplicadas()).isEmpty();
        verify(fabricacion, never()).aplicarDispensacionNoBloqueanteRetroactiva(anyLong(), any(User.class));
    }

    @Test
    void aplicacionMantieneConteosEIdsOpOfSeparadosAunqueCoincidaElNumero() {
        SeguimientoOrdenAreaService seguimiento = mock(SeguimientoOrdenAreaService.class);
        OrdenFabricacionService fabricacion = mock(OrdenFabricacionService.class);
        DispensacionRetroactividadDTO resumenOp = resumenOp(true, 2, 1, 1);
        resumenOp.getOrdenIdsAplicadas().add(42);
        when(seguimiento.aplicarRetroactividadDispensacionNoBloqueante()).thenReturn(resumenOp);
        when(fabricacion.listarCandidatasDispensacionNoBloqueante()).thenReturn(List.of(OF_ID, 43L));
        when(fabricacion.aplicarDispensacionNoBloqueanteRetroactiva(OF_ID, actor)).thenReturn(true);
        when(fabricacion.aplicarDispensacionNoBloqueanteRetroactiva(43L, actor)).thenReturn(false);

        DispensacionRetroactividadDTO resultado = superMaster(seguimiento, fabricacion)
                .aplicarRetroactividadDispensacionNoBloqueante(actor.getUsername());

        assertThat(resultado.getOrdenesCandidatas()).isEqualTo(2);
        assertThat(resultado.getOrdenesAplicadas()).isEqualTo(1);
        assertThat(resultado.getOrdenesOmitidas()).isEqualTo(1);
        assertThat(resultado.getOrdenIdsAplicadas()).containsExactly(42);
        assertThat(resultado.getOrdenesFabricacionCandidatas()).isEqualTo(2);
        assertThat(resultado.getOrdenesFabricacionAplicadas()).isEqualTo(1);
        assertThat(resultado.getOrdenesFabricacionOmitidas()).isEqualTo(1);
        assertThat(resultado.getOrdenFabricacionIdsAplicadas()).containsExactly(42L);
        assertThat(resultado.getMensaje()).contains("1 OP y 1 OF");
        verify(fabricacion).aplicarDispensacionNoBloqueanteRetroactiva(OF_ID, actor);
        verify(fabricacion).aplicarDispensacionNoBloqueanteRetroactiva(43L, actor);
    }

    @Test
    void agregadorConDirectivaApagadaMuestraCandidatasSinAplicarlas() {
        SeguimientoOrdenAreaService seguimiento = mock(SeguimientoOrdenAreaService.class);
        OrdenFabricacionService fabricacion = mock(OrdenFabricacionService.class);
        when(seguimiento.aplicarRetroactividadDispensacionNoBloqueante()).thenReturn(resumenOp(false, 1, 0, 0));
        when(fabricacion.listarCandidatasDispensacionNoBloqueante()).thenReturn(List.of(OF_ID, 43L));

        DispensacionRetroactividadDTO resultado = superMaster(seguimiento, fabricacion)
                .aplicarRetroactividadDispensacionNoBloqueante(actor.getUsername());

        assertThat(resultado.isEjecutable()).isFalse();
        assertThat(resultado.getOrdenesAplicadas()).isZero();
        assertThat(resultado.getOrdenesFabricacionCandidatas()).isEqualTo(2);
        assertThat(resultado.getOrdenesFabricacionAplicadas()).isZero();
        assertThat(resultado.getOrdenesFabricacionOmitidas()).isEqualTo(2);
        assertThat(resultado.getOrdenFabricacionIdsAplicadas()).isEmpty();
        verify(fabricacion, never()).aplicarDispensacionNoBloqueanteRetroactiva(anyLong(), any(User.class));
    }

    @Test
    void conflictoDeOcupacionConservaHttp409YMensajeOperativoParaElCliente() {
        DespejeLineaResource resource = new DespejeLineaResource(
                mock(PlantillaDespejeService.class), mock(DespejeLineaService.class),
                mock(ModuleTabAccessGuard.class), mock(UserRepository.class));
        String mensaje = "Otra operación ocupa el área; una pausa conserva su ocupación.";

        var respuesta = resource.conflictoOperativo(new ResponseStatusException(HttpStatus.CONFLICT, mensaje));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().getMessage()).isEqualTo(mensaje);
    }

    private void prepararCandidata(OrdenFabricacion orden, List<OrdenFabricacionOperacion> operaciones) {
        when(directivas.isDispensacionNoBloqueaInicioProduccion()).thenReturn(true);
        when(ordenRepo.findByIdForUpdate(orden.getOrdenFabricacionId())).thenReturn(Optional.of(orden));
        when(candidatasRepo(orden.getOrdenFabricacionId())).thenReturn(List.of(orden.getOrdenFabricacionId()));
        when(operacionRepo.findByOrdenFabricacion_OrdenFabricacionIdOrderByPosicionSecuenciaAsc(
                orden.getOrdenFabricacionId())).thenReturn(operaciones);
    }

    private List<Long> candidatasRepo(Long id) {
        return ordenRepo.findIdsCandidatasDispensacionNoBloqueante(id, PoliticaDispensacionInicio.BLOQUEANTE,
                List.of(EstadoOrdenFabricacion.PLANIFICADA, EstadoOrdenFabricacion.LIBERADA),
                List.of(EstadoSeguimientoOrdenArea.COLA.getCode(), EstadoSeguimientoOrdenArea.ESPERA.getCode()),
                ActorTipoEventoSeguimiento.USER, TipoEventoSeguimiento.OPERATIVO,
                EstadoSeguimientoOrdenArea.EN_PROCESO.getCode());
    }

    private static OrdenFabricacion candidata(EstadoDispensacionMateriales estadoFisico) {
        OrdenFabricacion orden = new OrdenFabricacion();
        orden.setOrdenFabricacionId(OF_ID);
        orden.setEstado(EstadoOrdenFabricacion.PLANIFICADA);
        orden.setEstadoDispensacionMateriales(estadoFisico);
        orden.setFechaLanzamiento(AHORA.plusDays(2));
        return orden;
    }

    private static OrdenFabricacionOperacion operacion(OrdenFabricacion orden, Long id,
                                                       EstadoSeguimientoOrdenArea estado) {
        OrdenFabricacionOperacion operacion = new OrdenFabricacionOperacion();
        operacion.setId(id);
        operacion.setOrdenFabricacion(orden);
        operacion.setEstadoEnum(estado);
        operacion.setFechaEstadoActual(FECHA_PREVIA);
        return operacion;
    }

    private SuperMasterOpsService superMaster(SeguimientoOrdenAreaService seguimiento,
                                             OrdenFabricacionService fabricacion) {
        UserRepository usuarios = mock(UserRepository.class);
        when(usuarios.findByUsername(actor.getUsername())).thenReturn(Optional.of(actor));
        return new SuperMasterOpsService(mock(SuperMasterConfigRepo.class),
                mock(SuperMasterVerificationCodeRepo.class), usuarios, mock(EmailService.class),
                CLOCK, seguimiento, fabricacion);
    }

    private static DispensacionRetroactividadDTO resumenOp(boolean directiva, int candidatas,
                                                          int aplicadas, int omitidas) {
        DispensacionRetroactividadDTO dto = new DispensacionRetroactividadDTO();
        dto.setDirectivaActual(directiva);
        dto.setOrdenesCandidatas(candidatas);
        dto.setOrdenesAplicadas(aplicadas);
        dto.setOrdenesOmitidas(omitidas);
        return dto;
    }
}
