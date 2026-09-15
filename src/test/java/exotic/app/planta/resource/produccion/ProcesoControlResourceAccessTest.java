package exotic.app.planta.resource.produccion;

import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.controles.ControlDeviationService;
import exotic.app.planta.service.controles.ControlExecutionService;
import exotic.app.planta.service.controles.ControlIdempotencyService;
import exotic.app.planta.service.controles.ControlPlanService;
import exotic.app.planta.service.controles.ControlRequeridoExcepcionalService;
import exotic.app.planta.service.controles.ControlWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcesoControlResourceAccessTest {

    @Mock private ControlPlanService planService;
    @Mock private ControlExecutionService executionService;
    @Mock private ControlDeviationService deviationService;
    @Mock private ControlWorkflowService workflowService;
    @Mock private ControlRequeridoExcepcionalService excepcionalService;
    @Mock private ControlIdempotencyService idempotencyService;
    @Mock private ModuleTabAccessGuard accessGuard;
    @Mock private Authentication authentication;

    private ProcesoControlResource resource;

    @BeforeEach
    void setUp() {
        resource = new ProcesoControlResource(
                planService,
                executionService,
                deviationService,
                workflowService,
                excepcionalService,
                idempotencyService,
                accessGuard);
    }

    @Test
    void todasLasFachadasDeProcesoUsanLaReglaConBypassMasterLike() {
        User actor = User.builder().username("master").build();
        when(accessGuard.requireTabAccess(
                eq(authentication), eq(ModuloSistema.PRODUCCION),
                anyString(), anyInt(), anyString())).thenReturn(actor);

        resource.listarPlanes(authentication, null);
        resource.publicar(authentication, 1L, 2L);
        resource.pendientes(
                authentication, null, null, null, null, null, null,
                null, null, null, null, 0, 20);
        resource.historial(
                authentication, null, null, null, null, null, null, 0, 20);
        resource.detalleEjecucion(authentication, 1L);
        resource.desviaciones(authentication, null, null, 0, 20);
        resource.resolver(authentication, 1L, "test-key", null);

        verify(accessGuard).requireTabAccess(
                authentication, ModuloSistema.PRODUCCION,
                ProcesoControlResource.TAB_PLANES, 1,
                "No tiene el nivel requerido para administrar planes de proceso.");
        verify(accessGuard).requireTabAccess(
                authentication, ModuloSistema.PRODUCCION,
                ProcesoControlResource.TAB_PLANES, 3,
                "No tiene el nivel requerido para administrar planes de proceso.");
        verify(accessGuard).requireTabAccess(
                authentication, ModuloSistema.PRODUCCION,
                ProcesoControlResource.TAB_REGISTRO, 1,
                "No tiene el nivel requerido para operar controles de proceso.");
        verify(accessGuard, times(2)).requireTabAccess(
                authentication, ModuloSistema.PRODUCCION,
                ProcesoControlResource.TAB_HISTORIAL, 1,
                "No tiene el nivel requerido para operar controles de proceso.");
        verify(accessGuard).requireTabAccess(
                authentication, ModuloSistema.PRODUCCION,
                ProcesoControlResource.TAB_DESVIACIONES, 1,
                "No tiene el nivel requerido para operar controles de proceso.");
        verify(accessGuard).requireTabAccess(
                authentication, ModuloSistema.PRODUCCION,
                ProcesoControlResource.TAB_DESVIACIONES, 2,
                "No tiene el nivel requerido para operar controles de proceso.");

        verify(accessGuard, never()).requireTabAccessWithSuperMasterBypass(
                eq(authentication), eq(ModuloSistema.PRODUCCION),
                anyString(), anyInt(), anyString());
        verify(accessGuard, never()).requireTabAccessWithoutMasterBypass(
                eq(authentication), eq(ModuloSistema.PRODUCCION),
                anyString(), anyInt(), anyString());
    }

    @Test
    void busquedaDeLotesUsaLaReglaMultipleConBypassMasterLike() {
        User actor = User.builder().username("master").build();
        Map<String, Integer> requiredTabs = Map.of(
                ProcesoControlResource.TAB_REGISTRO, 1,
                ProcesoControlResource.TAB_PLANES, 3);
        when(accessGuard.requireAnyTabAccess(
                authentication, ModuloSistema.PRODUCCION, requiredTabs,
                "Se requiere acceso a registro o administracion de planes para buscar lotes."))
                .thenReturn(actor);

        resource.lotes(authentication, null, 20);

        verify(accessGuard).requireAnyTabAccess(
                authentication, ModuloSistema.PRODUCCION, requiredTabs,
                "Se requiere acceso a registro o administracion de planes para buscar lotes.");
        verify(workflowService).buscarLotes(null, 20);
    }
}
