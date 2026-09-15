package exotic.app.planta.resource.bi;

import exotic.app.planta.model.bi.dto.InformeGlobalProduccionDTO;
import exotic.app.planta.model.bi.dto.PaginaDesviacionesProduccionDTO;
import exotic.app.planta.service.bi.InformeProduccionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InformeProduccionResourceTest {
    @Mock private InformeProduccionService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InformeProduccionResource(service))
                .build();
    }

    @Test
    void exponeDesviacionesPaginadasParaUnaFecha() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 15);
        InformeGlobalProduccionDTO.DetalleReferenciaDTO reference =
                InformeGlobalProduccionDTO.DetalleReferenciaDTO.builder()
                        .productoId("P-01")
                        .productoNombre("Producto")
                        .categoriaNombre("Categoría")
                        .cantidadPlaneada(10)
                        .cantidadProducida(0)
                        .diferencia(-10)
                        .planeado(true)
                        .producido(false)
                        .noPlaneado(false)
                        .build();
        PaginaDesviacionesProduccionDTO response = new PaginaDesviacionesProduccionDTO(
                List.of(new PaginaDesviacionesProduccionDTO.DesviacionDTO(
                        reference,
                        PaginaDesviacionesProduccionDTO.TipoDesviacion.SIN_PRODUCCION,
                        -10,
                        -100d)),
                new PaginaDesviacionesProduccionDTO.CountsDTO(1, 0, 0, 0),
                0,
                5,
                1,
                1,
                true,
                true);
        when(service.obtenerDesviaciones(date, date, 0, 5)).thenReturn(response);

        mockMvc.perform(get("/bi/informes-globales/produccion/desviaciones")
                        .param("fecha", "2026-09-15")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reference.productoId").value("P-01"))
                .andExpect(jsonPath("$.items[0].kind").value("SIN_PRODUCCION"))
                .andExpect(jsonPath("$.counts.sinProduccion").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(service).obtenerDesviaciones(date, date, 0, 5);
    }

    @Test
    void devuelveBadRequestCuandoLaPaginacionEsInvalida() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 15);
        when(service.obtenerDesviaciones(date, date, -1, 5))
                .thenThrow(new IllegalArgumentException("La pagina no puede ser negativa."));

        mockMvc.perform(get("/bi/informes-globales/produccion/desviaciones")
                        .param("fecha", "2026-09-15")
                        .param("page", "-1")
                        .param("size", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("La pagina no puede ser negativa."));
    }
}
