package exotic.app.planta.resource.bi;

import exotic.app.planta.model.bi.dto.BusquedaStockMaterialDTO;
import exotic.app.planta.model.bi.dto.CoberturaMaterialesDTO;
import exotic.app.planta.model.bi.dto.InformeInventarioDTO;
import exotic.app.planta.service.bi.inventario.BusquedaStockMaterialService;
import exotic.app.planta.service.bi.inventario.CoberturaMaterialesService;
import exotic.app.planta.service.bi.inventario.InformeInventarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InformeInventarioResource.class)
@AutoConfigureMockMvc(addFilters = false)
class InformeInventarioResourceTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InformeInventarioService reportService;

    @MockBean
    private BusquedaStockMaterialService searchService;

    @MockBean
    private CoberturaMaterialesService coverageService;

    @Test
    void reportAcceptsSingleDateAndTrendWindow() throws Exception {
        LocalDate date = LocalDate.of(2026, 6, 15);
        InformeInventarioDTO report = InformeInventarioDTO.builder()
                .versionContrato(2)
                .periodo(InformeInventarioDTO.PeriodoDTO.builder()
                        .fechaDesde(date)
                        .fechaHasta(date)
                        .modoFecha("FECHA_UNICA")
                        .dias(1)
                        .build())
                .notas(List.of())
                .build();
        when(reportService.getReport(date, date, 90)).thenReturn(report);

        mockMvc.perform(get("/bi/informes-globales/almacen")
                        .param("fecha", "2026-06-15")
                        .param("ventanaTendenciaDias", "90"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionContrato").value(2))
                .andExpect(jsonPath("$.periodo.modoFecha").value("FECHA_UNICA"));

        verify(reportService).getReport(date, date, 90);
    }

    @Test
    void reportRejectsRangeLongerThan31Days() throws Exception {
        mockMvc.perform(get("/bi/informes-globales/almacen")
                        .param("fechaDesde", "2026-06-01")
                        .param("fechaHasta", "2026-07-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "El rango maximo permitido para este informe es de 31 dias."));
    }

    @Test
    void searchReturnsAtMostTheServiceResults() throws Exception {
        when(searchService.search("MP"))
                .thenReturn(BusquedaStockMaterialDTO.builder()
                        .buscar("MP")
                        .resultados(List.of())
                        .build());

        mockMvc.perform(get("/bi/informes-globales/almacen/stock-materiales")
                        .param("buscar", "MP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buscar").value("MP"))
                .andExpect(jsonPath("$.resultados").isArray());
    }

    @Test
    void coverageUses90DaysByDefault() throws Exception {
        when(coverageService.calculate(90))
                .thenReturn(CoberturaMaterialesDTO.builder()
                        .ventanaDias(90)
                        .estado(CoberturaMaterialesDTO.EstadoCobertura.SIN_CONSUMO)
                        .motivosConfianzaBaja(List.of())
                        .estimaciones(List.of())
                        .build());

        mockMvc.perform(get("/bi/informes-globales/almacen/cobertura"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ventanaDias").value(90))
                .andExpect(jsonPath("$.estado").value("SIN_CONSUMO"));
    }
}
