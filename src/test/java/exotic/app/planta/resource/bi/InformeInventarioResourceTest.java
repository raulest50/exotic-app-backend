package exotic.app.planta.resource.bi;

import exotic.app.planta.model.bi.dto.BusquedaStockMaterialDTO;
import exotic.app.planta.model.bi.dto.CoberturaMaterialesDTO;
import exotic.app.planta.model.bi.dto.InformeInventarioDTO;
import exotic.app.planta.model.bi.dto.PaginaInformeInventarioDTO;
import exotic.app.planta.service.bi.inventario.AjustesInventarioDetalleService;
import exotic.app.planta.service.bi.inventario.BusquedaStockMaterialService;
import exotic.app.planta.service.bi.inventario.CoberturaMaterialesService;
import exotic.app.planta.service.bi.inventario.InformeInventarioService;
import exotic.app.planta.service.bi.inventario.InformeInventarioDetalleService;
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
    private InformeInventarioDetalleService detailService;

    @MockBean
    private BusquedaStockMaterialService searchService;

    @MockBean
    private CoberturaMaterialesService coverageService;

    @MockBean
    private AjustesInventarioDetalleService adjustmentDetailService;

    @Test
    void reportAcceptsSingleDateAndIgnoresLegacyTrendWindow() throws Exception {
        LocalDate date = LocalDate.of(2026, 6, 15);
        InformeInventarioDTO report = InformeInventarioDTO.builder()
                .versionContrato(3)
                .periodo(InformeInventarioDTO.PeriodoDTO.builder()
                        .fechaDesde(date)
                        .fechaHasta(date)
                        .modoFecha("FECHA_UNICA")
                        .dias(1)
                        .build())
                .stock(InformeInventarioDTO.StockDTO.builder()
                        .resumen(InformeInventarioDTO.ResumenStockDTO.builder()
                                .valorizacion(InformeInventarioDTO.ValorizacionInventarioDTO.builder()
                                        .materiales(InformeInventarioDTO.ValorizacionMaterialesDTO.builder()
                                                .total(850_000_000)
                                                .materiaPrima(275_000_000)
                                                .empaque(575_000_000)
                                                .build())
                                        .terminados(22_000_000_000d)
                                        .build())
                                .coberturaCostosDetalle(InformeInventarioDTO.CoberturaCostosDetalleDTO.builder()
                                        .globalPct(98d)
                                        .materialesPct(97d)
                                        .terminadosPct(100d)
                                        .build())
                                .build())
                        .materialesPorTipo(InformeInventarioDTO.MaterialesPorTipoDTO.builder()
                                .materiaPrima(List.of(InformeInventarioDTO.StockUnidadDTO.builder()
                                        .unidadMedida("KG")
                                        .cantidadNeta(125)
                                        .cantidadPositiva(130)
                                        .cantidadNegativa(-5)
                                        .referenciasConStock(8)
                                        .build()))
                                .empaque(List.of(InformeInventarioDTO.StockUnidadDTO.builder()
                                        .unidadMedida("U")
                                        .cantidadNeta(2_500)
                                        .cantidadPositiva(2_500)
                                        .cantidadNegativa(0)
                                        .referenciasConStock(12)
                                        .build()))
                                .build())
                        .build())
                .notas(List.of())
                .build();
        when(reportService.getReport(date, date)).thenReturn(report);

        mockMvc.perform(get("/bi/informes-globales/almacen")
                        .param("fecha", "2026-06-15")
                        .param("ventanaTendenciaDias", "90"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionContrato").value(3))
                .andExpect(jsonPath("$.periodo.modoFecha").value("FECHA_UNICA"))
                .andExpect(jsonPath("$.stock.resumen.valorizacion.materiales.total")
                        .value(850_000_000))
                .andExpect(jsonPath("$.stock.resumen.valorizacion.terminados")
                        .value(22_000_000_000d))
                .andExpect(jsonPath("$.stock.resumen.coberturaCostosDetalle.globalPct")
                        .value(98d))
                .andExpect(jsonPath("$.stock.resumen.coberturaCostosDetalle.materialesPct")
                        .value(97d))
                .andExpect(jsonPath("$.stock.resumen.coberturaCostosDetalle.terminadosPct")
                        .value(100d))
                .andExpect(jsonPath("$.stock.materialesPorTipo.materiaPrima[0].unidadMedida")
                        .value("KG"))
                .andExpect(jsonPath("$.stock.materialesPorTipo.materiaPrima[0].cantidadNeta")
                        .value(125))
                .andExpect(jsonPath("$.stock.materialesPorTipo.empaque[0].unidadMedida")
                        .value("U"))
                .andExpect(jsonPath("$.stock.materialesPorTipo.empaque[0].cantidadNeta")
                        .value(2_500));

        verify(reportService).getReport(date, date);
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

    @Test
    void pendingPurchaseOrdersUsesIndependentPagination() throws Exception {
        when(detailService.getPendingPurchaseOrders(1, 10))
                .thenReturn(new PaginaInformeInventarioDTO<>(
                        List.of(), 1, 10, 12, 2, false, true));

        mockMvc.perform(get("/bi/informes-globales/almacen/ocm-pendientes")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(12));

        verify(detailService).getPendingPurchaseOrders(1, 10);
    }

    @Test
    void adjustmentMaterialsUsesReportDatesAndIndependentFilters() throws Exception {
        LocalDate date = LocalDate.of(2026, 7, 20);
        when(adjustmentDetailService.getMaterials(
                date,
                date,
                "MATERIA_PRIMA",
                "NEGATIVO",
                "MOVIMIENTOS",
                "alcohol",
                1,
                5))
                .thenReturn(new PaginaInformeInventarioDTO<>(
                        List.of(), 1, 5, 7, 2, false, true));

        mockMvc.perform(get("/bi/informes-globales/almacen/ajustes-materiales")
                        .param("fecha", "2026-07-20")
                        .param("grupo", "MATERIA_PRIMA")
                        .param("tipo", "NEGATIVO")
                        .param("orden", "MOVIMIENTOS")
                        .param("buscar", "alcohol")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(7));

        verify(adjustmentDetailService).getMaterials(
                date,
                date,
                "MATERIA_PRIMA",
                "NEGATIVO",
                "MOVIMIENTOS",
                "alcohol",
                1,
                5);
    }
}
