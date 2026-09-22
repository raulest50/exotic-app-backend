package exotic.app.planta.resource.master.configs;

import exotic.app.planta.model.compras.dto.OcmCierreDTOs.ConfigWrite;
import exotic.app.planta.model.compras.dto.OcmCierreDTOs.Modo;
import exotic.app.planta.model.master.configs.MasterDirectiveKeys;
import exotic.app.planta.service.compras.OcmCierreBatchService;
import exotic.app.planta.service.compras.OcmCierreConfigService;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OcmCierreResourceTest {
    @Test
    void soloSuperMasterOMasterHabilitadoPuedenAdministrarCierres() {
        var config = mock(OcmCierreConfigService.class);
        var batch = mock(OcmCierreBatchService.class);
        var directives = mock(MasterDirectiveService.class);
        var resource = new OcmCierreResource(config, batch, directives);
        var request = new ConfigWrite(Modo.RECEPCION_COMPLETA, null);
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> resource.actualizar(request, actor("operario"))).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> resource.previsualizar(actor("master")));
        verifyNoInteractions(config, batch);

        resource.actualizar(request, actor("super_master"));
        verify(config).actualizar(request);
        when(directives.getBooleanDirectiveValue(MasterDirectiveKeys.ENABLE_MASTER_SUPERMASTER_DIRECTIVES_ACCESS, true))
                .thenReturn(true);
        resource.previsualizar(actor("master"));
        verify(batch).previsualizar();
    }

    private Authentication actor(String username) {
        return new UsernamePasswordAuthenticationToken(username, null, List.of());
    }
}
