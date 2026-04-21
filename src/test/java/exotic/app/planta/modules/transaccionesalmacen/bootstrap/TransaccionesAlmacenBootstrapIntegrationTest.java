package exotic.app.planta.modules.transaccionesalmacen.bootstrap;

import exotic.app.planta.modules.transaccionesalmacen.support.AbstractTransaccionesAlmacenIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransaccionesAlmacenBootstrapIntegrationTest extends AbstractTransaccionesAlmacenIntegrationTest {

    @Test
    void getSuperMasterConfig_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/super-master-ops/config"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getSuperMasterConfig_returnsFlagsForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/super-master-ops/config")
                        .with(bearerToken())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitarAjustesInventario").exists())
                .andExpect(jsonPath("$.habilitarCargaMasiva").exists())
                .andExpect(jsonPath("$.habilitarEliminacionForzada").exists());
    }
}
