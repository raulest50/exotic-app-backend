package exotic.app.planta.resource.bi;

import exotic.app.planta.model.bi.dto.InformeGlobalAlmacenDTO;
import exotic.app.planta.service.bi.InformesGlobalesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InformesGlobalesResource.class)
@AutoConfigureMockMvc(addFilters = false)
class InformesGlobalesResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InformesGlobalesService informesGlobalesService;

    @Test
    void obtenerReporteAlmacen_aceptaFechaUnica() throws Exception {
        LocalDate fecha = LocalDate.of(2026, 6, 15);
        InformeGlobalAlmacenDTO reporte = InformeGlobalAlmacenDTO.builder()
                .fechaDesde(fecha)
                .fechaHasta(fecha)
                .modoFecha("FECHA_UNICA")
                .diasRango(1)
                .resumenPorUnidad(List.of())
                .rankingDispensacion(List.of())
                .serieFisicaDiaria(List.of())
                .notas(List.of())
                .build();
        when(informesGlobalesService.obtenerReporteAlmacen(eq(fecha), eq(fecha))).thenReturn(reporte);

        mockMvc.perform(get("/bi/informes-globales/almacen")
                        .param("fecha", "2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modoFecha").value("FECHA_UNICA"))
                .andExpect(jsonPath("$.fechaDesde").value("2026-06-15"));

        verify(informesGlobalesService).obtenerReporteAlmacen(fecha, fecha);
    }

    @Test
    void obtenerReporteAlmacen_rechazaRangoMayorA31Dias() throws Exception {
        mockMvc.perform(get("/bi/informes-globales/almacen")
                        .param("fechaDesde", "2026-06-01")
                        .param("fechaHasta", "2026-07-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("El rango maximo permitido para este informe es de 31 dias."));
    }

    @Test
    void obtenerReporteAlmacen_rechazaFechaUnicaCombinadaConRango() throws Exception {
        mockMvc.perform(get("/bi/informes-globales/almacen")
                        .param("fecha", "2026-06-15")
                        .param("fechaDesde", "2026-06-01")
                        .param("fechaHasta", "2026-06-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Use fecha o fechaDesde/fechaHasta, no ambas opciones."));
    }
}
