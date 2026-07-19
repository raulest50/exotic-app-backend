package exotic.app.planta.resource.bi;

import exotic.app.planta.model.bi.dto.InformeGlobalProduccionDTO;
import exotic.app.planta.service.bi.InformeProduccionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InformeProduccionResource.class)
@AutoConfigureMockMvc(addFilters = false)
class InformeProduccionResourceTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InformeProduccionService service;

    @Test
    void keepsTheExistingProductionUrlAndDateContract() throws Exception {
        LocalDate date = LocalDate.of(2026, 6, 15);
        when(service.obtenerReporte(date, date))
                .thenReturn(InformeGlobalProduccionDTO.builder()
                        .fechaDesde(date)
                        .fechaHasta(date)
                        .modoFecha("FECHA_UNICA")
                        .diasRango(1)
                        .build());

        mockMvc.perform(get("/bi/informes-globales/produccion")
                        .param("fecha", "2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modoFecha").value("FECHA_UNICA"));

        verify(service).obtenerReporte(date, date);
    }
}
