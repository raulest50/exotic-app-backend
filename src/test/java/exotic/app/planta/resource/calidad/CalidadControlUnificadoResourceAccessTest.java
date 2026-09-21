package exotic.app.planta.resource.calidad;

import exotic.app.planta.model.controles.TipoOrdenControl;
import exotic.app.planta.model.controles.AmbitoControl;
import exotic.app.planta.model.controles.EstadoVersionPlanControl;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.controles.ControlDeviationService;
import exotic.app.planta.service.controles.ControlExecutionService;
import exotic.app.planta.service.controles.ControlIdempotencyService;
import exotic.app.planta.service.controles.ControlPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalidadControlUnificadoResourceAccessTest {

    @Mock private ControlPlanService planService;
    @Mock private ControlExecutionService executionService;
    @Mock private ControlDeviationService deviationService;
    @Mock private ControlIdempotencyService idempotencyService;
    @Mock private ModuleTabAccessGuard accessGuard;
    @Mock private Authentication authentication;

    private CalidadControlUnificadoResource resource;

    @Test
    void resumenYDetalleDeVersionExigenLecturaDePlanes() {
        resource.resumenPlanes(authentication, "peso", EstadoVersionPlanControl.VIGENTE, 1, 10);
        resource.detalleVersion(authentication, 7L, 71L);
        verify(accessGuard, times(2)).requireTabAccess(
                authentication, ModuloSistema.CALIDAD, CalidadControlUnificadoResource.TAB_PLANES, 1,
                "No tiene el nivel requerido para administrar planes de Calidad.");
        verify(planService).listarResumenes(AmbitoControl.CALIDAD, "peso", EstadoVersionPlanControl.VIGENTE, 1, 10);
        verify(planService).detalleVersion(AmbitoControl.CALIDAD, 7L, 71L);
    }

    @Test
    void deniegaLasNuevasConsultasSinPermisoDePlanes() {
        when(accessGuard.requireTabAccess(
                authentication, ModuloSistema.CALIDAD, CalidadControlUnificadoResource.TAB_PLANES, 1,
                "No tiene el nivel requerido para administrar planes de Calidad."))
                .thenThrow(new AccessDeniedException("Sin permiso"));
        assertThrows(AccessDeniedException.class,
                () -> resource.resumenPlanes(authentication, null, null, 0, 10));
        assertThrows(AccessDeniedException.class,
                () -> resource.detalleVersion(authentication, 7L, 71L));
        verifyNoInteractions(planService);
    }

    @BeforeEach
    void setUp() {
        resource = new CalidadControlUnificadoResource(
                planService,
                executionService,
                deviationService,
                idempotencyService,
                accessGuard);
    }

    @Test
    void opcionesDeEnsayoUsanElPermisoDeRegistroConBypassMasterLike() {
        User actor = User.builder().username("master").build();
        when(accessGuard.requireTabAccess(
                authentication,
                ModuloSistema.CALIDAD,
                CalidadControlUnificadoResource.TAB_REGISTRO,
                1,
                "No tiene el nivel requerido para operar controles de Calidad."))
                .thenReturn(actor);

        resource.opcionesEnsayoPendiente(authentication, 3, TipoOrdenControl.OF, "peso", 0, 10);

        verify(accessGuard).requireTabAccess(
                authentication,
                ModuloSistema.CALIDAD,
                CalidadControlUnificadoResource.TAB_REGISTRO,
                1,
                "No tiene el nivel requerido para operar controles de Calidad.");
        verify(executionService).opcionesEnsayoCalidad(3, TipoOrdenControl.OF, "peso", 0, 10);
    }
}
