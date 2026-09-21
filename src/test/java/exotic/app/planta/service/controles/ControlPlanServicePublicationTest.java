package exotic.app.planta.service.controles;

import exotic.app.planta.model.controles.*;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.controles.*;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionRepo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ControlPlanServicePublicationTest {
    @Mock private PlanControlRepo planRepo;
    @Mock private VersionPlanControlRepo versionRepo;
    @Mock private MagnitudControlRepo magnitudRepo;
    @Mock private UnidadControlRepo unidadRepo;
    @Mock private ProductoRepo productoRepo;
    @Mock private CategoriaRepo categoriaRepo;
    @Mock private AreaProduccionRepo areaRepo;
    @Mock private ProcesoProduccionRepo procesoRepo;
    @Mock private User actor;
    @InjectMocks private ControlPlanService service;

    @ParameterizedTest
    @EnumSource(AmbitoControl.class)
    void escribeLaRetiradaMientrasLaNuevaVersionAunEsBorrador(AmbitoControl ambito) {
        PlanControl plan = plan(ambito);
        VersionPlanControl anterior = version(plan, 1, EstadoVersionPlanControl.VIGENTE);
        VersionPlanControl borrador = version(plan, 2, EstadoVersionPlanControl.BORRADOR);
        prepararPublicacion(plan, borrador, anterior);

        // Verificar el estado en el primer flush: comprobar solamente el resultado
        // final no detectaría que PostgreSQL puede recibir dos VIGENTE a la vez.
        doAnswer(invocation -> {
            assertEquals(EstadoVersionPlanControl.RETIRADA, anterior.getEstado());
            assertNotNull(anterior.getRetiradaEn());
            assertSame(actor, anterior.getRetiradaPor());
            assertEquals(EstadoVersionPlanControl.BORRADOR, borrador.getEstado());
            assertNull(borrador.getPublicadaEn());
            assertNull(borrador.getPublicadaPor());
            return anterior;
        }).when(versionRepo).saveAndFlush(anterior);

        var response = service.publicar(ambito, actor, plan.getId(), borrador.getId());

        var persistenceOrder = inOrder(versionRepo);
        persistenceOrder.verify(versionRepo).saveAndFlush(anterior);
        persistenceOrder.verify(versionRepo).saveAndFlush(borrador);
        assertEquals(EstadoVersionPlanControl.VIGENTE, borrador.getEstado());
        assertEquals(anterior.getRetiradaEn(), borrador.getPublicadaEn());
        assertSame(actor, borrador.getPublicadaPor());
        assertEquals(2, response.versiones().size());
        assertEquals(1L, response.versiones().stream()
                .filter(item -> item.estado() == EstadoVersionPlanControl.VIGENTE).count());
    }

    @ParameterizedTest
    @EnumSource(AmbitoControl.class)
    void publicaPrimeraVersionYRepetirPublicacionNoCambiaSuVigencia(AmbitoControl ambito) {
        PlanControl plan = plan(ambito);
        VersionPlanControl borrador = version(plan, 1, EstadoVersionPlanControl.BORRADOR);
        prepararPublicacion(plan, borrador, null);

        var response = service.publicar(ambito, actor, plan.getId(), borrador.getId());

        assertEquals(EstadoVersionPlanControl.VIGENTE, response.versiones().get(0).estado());
        assertNotNull(borrador.getPublicadaEn());
        assertSame(actor, borrador.getPublicadaPor());
        assertNull(borrador.getRetiradaEn());
        verify(versionRepo, times(1)).saveAndFlush(any(VersionPlanControl.class));

        LocalDateTime publicadaEn = borrador.getPublicadaEn();
        service.publicar(ambito, actor, plan.getId(), borrador.getId());

        assertEquals(publicadaEn, borrador.getPublicadaEn());
        verify(versionRepo, times(1)).saveAndFlush(any(VersionPlanControl.class));
    }

    @ParameterizedTest
    @EnumSource(AmbitoControl.class)
    void noActivaElBorradorSiFallaLaEscrituraDeLaRetirada(AmbitoControl ambito) {
        PlanControl plan = plan(ambito);
        VersionPlanControl anterior = version(plan, 1, EstadoVersionPlanControl.VIGENTE);
        VersionPlanControl borrador = version(plan, 2, EstadoVersionPlanControl.BORRADOR);
        prepararPublicacion(plan, borrador, anterior);
        var failure = new DataIntegrityViolationException("Fallo al persistir la retirada");
        when(versionRepo.saveAndFlush(anterior)).thenThrow(failure);

        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> service.publicar(ambito, actor, plan.getId(), borrador.getId())));

        assertEquals(EstadoVersionPlanControl.BORRADOR, borrador.getEstado());
        assertNull(borrador.getPublicadaEn());
        verify(versionRepo, never()).saveAndFlush(borrador);
        // El rollback de la transacción requiere validación con PostgreSQL;
        // estos repositorios simulados solo verifican que no se continúa publicando.
    }

    private void prepararPublicacion(PlanControl plan, VersionPlanControl borrador, VersionPlanControl anterior) {
        when(planRepo.findByIdAndAmbitoForUpdate(plan.getId(), plan.getAmbito())).thenReturn(Optional.of(plan));
        when(versionRepo.findByIdAndPlan_Ambito(borrador.getId(), plan.getAmbito())).thenReturn(Optional.of(borrador));
        when(versionRepo.findFirstByPlan_IdAndEstado(plan.getId(), EstadoVersionPlanControl.VIGENTE))
                .thenReturn(Optional.ofNullable(anterior));
    }

    private PlanControl plan(AmbitoControl ambito) {
        PlanControl plan = new PlanControl();
        plan.setId(5L);
        plan.setCodigo("PLAN-PUBLICACION");
        plan.setNombre("Plan de prueba de publicación");
        plan.setAmbito(ambito);
        return plan;
    }

    private VersionPlanControl version(PlanControl plan, int numero, EstadoVersionPlanControl estado) {
        VersionPlanControl version = new VersionPlanControl();
        version.setId(10L + numero);
        version.setPlan(plan);
        version.setNumero(numero);
        version.setEstado(estado);
        if (estado == EstadoVersionPlanControl.VIGENTE) {
            version.setPublicadaEn(LocalDateTime.of(2026, 9, 21, 8, 0));
        }
        AplicabilidadPlanControl rule = new AplicabilidadPlanControl();
        rule.setVersion(version);
        rule.setPuntoAplicacion(PuntoAplicacionControl.SALIDA_OPERACION);
        rule.setMomento(MomentoControl.DURANTE_FABRICACION);
        version.getAplicabilidades().add(rule);
        CaracteristicaPlanControl characteristic = new CaracteristicaPlanControl();
        characteristic.setVersion(version);
        characteristic.setNombre("Medición");
        characteristic.setMagnitud(new MagnitudControl());
        version.getCaracteristicas().add(characteristic);
        plan.getVersiones().add(version);
        return version;
    }
}
