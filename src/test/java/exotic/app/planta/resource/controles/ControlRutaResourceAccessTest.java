package exotic.app.planta.resource.controles;

import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.controles.ControlRutaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ControlRutaResourceAccessTest {
    @Mock private ControlRutaService service;
    @Mock private ModuleTabAccessGuard guard;
    @Mock private Authentication authentication;
    @InjectMocks private ControlRutaResource resource;

    @Test
    void soloConsultaConLecturaEnAlgunoDeLosTresDiagramas() {
        resource.listar(authentication, 7, null);
        verify(guard).requireAnyTabAccess(eq(authentication), eq(Map.of(
                ModuloSistema.PRODUCCION, Map.of("PARAMETROS_POR_CATEGORIA", 1, "PLANES_CONTROL_PROCESO", 1),
                ModuloSistema.CALIDAD, Map.of("PLANES_CONTROL_CALIDAD", 1))), anyString());
        verify(service).listar(7, null);
    }

    @Test
    void noConsultaPlanesCuandoElGuardDeniegaElAcceso() {
        when(guard.requireAnyTabAccess(eq(authentication), anyMap(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertThrows(ResponseStatusException.class, () -> resource.listar(authentication, 7, null));
        verifyNoInteractions(service);
    }

    @Test
    void detalleUsaLosMismosPermisosDeLecturaDeLosTresDiagramas() {
        resource.detalle(authentication, 12L, 3);

        verify(guard).requireAnyTabAccess(eq(authentication), eq(Map.of(
                ModuloSistema.PRODUCCION, Map.of("PARAMETROS_POR_CATEGORIA", 1, "PLANES_CONTROL_PROCESO", 1),
                ModuloSistema.CALIDAD, Map.of("PLANES_CONTROL_CALIDAD", 1))), anyString());
        verify(service).detalleVigente(12L, 3);
    }

    @Test
    void noExponeMedicionesSinPermisoDeLectura() {
        when(guard.requireAnyTabAccess(eq(authentication), anyMap(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        assertThrows(ResponseStatusException.class, () -> resource.detalle(authentication, 12L, 3));
        verifyNoInteractions(service);
    }
}
