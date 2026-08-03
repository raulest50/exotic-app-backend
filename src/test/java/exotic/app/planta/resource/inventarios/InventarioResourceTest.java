package exotic.app.planta.resource.inventarios;

import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.dto.AlcanceInventario;
import exotic.app.planta.model.inventarios.dto.InventarioConsolidadoPageDTO;
import exotic.app.planta.service.inventarios.InventarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventarioResource.class)
@AutoConfigureMockMvc(addFilters = false)
class InventarioResourceTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventarioService inventarioService;

    @Test
    void consolidatedInventoryAcceptsACommaSeparatedCustomWarehouseSelection() throws Exception {
        InventarioConsolidadoPageDTO response = new InventarioConsolidadoPageDTO(
                List.of(),
                0,
                10,
                0,
                0,
                AlcanceInventario.PERSONALIZADO,
                List.of(Movimiento.Almacen.GENERAL, Movimiento.Almacen.AVERIAS),
                OffsetDateTime.parse("2026-08-03T10:42:00-05:00")
        );
        when(inventarioService.getInventarioConsolidado(
                "",
                "NOMBRE",
                0,
                10,
                AlcanceInventario.PERSONALIZADO,
                List.of(Movimiento.Almacen.GENERAL, Movimiento.Almacen.AVERIAS)
        )).thenReturn(response);

        mockMvc.perform(get("/inventario/consolidado")
                        .param("alcance", "PERSONALIZADO")
                        .param("almacenes", "GENERAL,AVERIAS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alcance").value("PERSONALIZADO"))
                .andExpect(jsonPath("$.almacenesIncluidos[0]").value("GENERAL"))
                .andExpect(jsonPath("$.almacenesIncluidos[1]").value("AVERIAS"))
                .andExpect(jsonPath("$.fechaHoraCorte").value("2026-08-03T10:42:00-05:00"));

        verify(inventarioService).getInventarioConsolidado(
                "",
                "NOMBRE",
                0,
                10,
                AlcanceInventario.PERSONALIZADO,
                List.of(Movimiento.Almacen.GENERAL, Movimiento.Almacen.AVERIAS)
        );
    }
}
