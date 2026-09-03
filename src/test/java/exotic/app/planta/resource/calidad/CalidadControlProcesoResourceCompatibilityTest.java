package exotic.app.planta.resource.calidad;

import exotic.app.planta.config.LegacyControlCompatibilityProperties;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.PlantillaRequest;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.PlantillaResponse;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.calidad.BatchRecordQualityService;
import exotic.app.planta.service.calidad.CalidadControlProcesoService;
import exotic.app.planta.service.calidad.LegacyControlCompatibilityService;
import exotic.app.planta.service.controles.ControlIdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalidadControlProcesoResourceCompatibilityTest {

    private LegacyControlCompatibilityService bridge;
    private LegacyControlCompatibilityProperties properties;
    private CalidadControlProcesoResource resource;
    private Authentication authentication;
    private User actor;

    @BeforeEach
    void setUp() {
        bridge = mock(LegacyControlCompatibilityService.class);
        properties = new LegacyControlCompatibilityProperties();
        UserRepository users = mock(UserRepository.class);
        resource = new CalidadControlProcesoResource(
                mock(CalidadControlProcesoService.class), bridge, properties,
                mock(BatchRecordQualityService.class), users,
                mock(ControlIdempotencyService.class));
        authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("super_master");
        actor = new User();
        actor.setId(1L);
        actor.setUsername("super_master");
        when(users.findByUsername("super_master")).thenReturn(Optional.of(actor));
    }

    @Test
    void retired_respondeGoneSinInvocarElAdaptador() {
        properties.setWriteMode(LegacyControlCompatibilityProperties.WriteMode.RETIRED);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> resource.guardarBorrador(authentication, new PlantillaRequest()));

        assertEquals(HttpStatus.GONE, error.getStatusCode());
        verifyNoInteractions(bridge);
    }

    @Test
    void bridge_delegaLaMutacionConElActorAutenticado() {
        PlantillaRequest request = new PlantillaRequest();
        PlantillaResponse response = PlantillaResponse.builder().id(8L).build();
        when(bridge.guardarBorrador(actor, request)).thenReturn(response);

        assertSame(response, resource.guardarBorrador(authentication, request));
        verify(bridge).guardarBorrador(actor, request);
    }
}
