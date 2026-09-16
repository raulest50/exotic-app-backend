package exotic.app.planta.resource.calidad;

import exotic.app.planta.model.controles.TipoOrdenControl;
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

import static org.mockito.Mockito.verify;
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
