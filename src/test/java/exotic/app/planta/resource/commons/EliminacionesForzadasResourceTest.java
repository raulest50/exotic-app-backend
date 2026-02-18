package exotic.app.planta.resource.commons;

import exotic.app.planta.model.commons.dto.eliminaciones.PurgaModuloProductosSummaryDTO;
import exotic.app.planta.service.commons.EliminacionesForzadasService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EliminacionesForzadasResource.class)
@AutoConfigureMockMvc(addFilters = false)
class EliminacionesForzadasResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EliminacionesForzadasService eliminacionesForzadasService;

    @Test
    void estudiarPurgaModuloProductos_returnsSummary() throws Exception {
        PurgaModuloProductosSummaryDTO dto = new PurgaModuloProductosSummaryDTO();
        dto.setTransaccionesAlmacen(10);
        dto.setMovimientos(50);
        dto.setLotes(5);
        dto.setOrdenesCompra(3);
        dto.setOrdenesProduccion(2);
        dto.setInsumos(20);
        dto.setProcesosProduccionCompleto(1);
        dto.setCasePacks(1);
        dto.setInsumosEmpaque(5);
        dto.setManufacturingVersions(0);
        dto.setProductos(15);
        dto.setPermitido(true);
        dto.setMensajeEntorno("Perfil activo: dev");

        when(eliminacionesForzadasService.estudiarPurgaModuloProductos()).thenReturn(dto);

        mockMvc.perform(get("/api/eliminaciones-forzadas/purga-modulo-productos/estudiar")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.permitido").value(true))
                .andExpect(jsonPath("$.productos").value(15))
                .andExpect(jsonPath("$.transaccionesAlmacen").value(10));
    }
}
