package exotic.app.planta.resource.bi;

import exotic.app.planta.model.bi.dto.AlertasMaterialesExploracionDTO;
import exotic.app.planta.model.bi.dto.BusquedaStockMaterialDTO;
import exotic.app.planta.model.bi.dto.CoberturaMaterialesDTO;
import exotic.app.planta.model.bi.dto.FuenteDemandaCobertura;
import exotic.app.planta.model.bi.dto.InformeInventarioDTO;
import exotic.app.planta.model.bi.dto.PaginaInformeInventarioDTO;
import exotic.app.planta.service.bi.inventario.AjustesInventarioDetalleService;
import exotic.app.planta.service.bi.inventario.AlertasInventarioDetalleService;
import exotic.app.planta.service.bi.inventario.BusquedaStockMaterialService;
import exotic.app.planta.service.bi.inventario.CoberturaMaterialesService;
import exotic.app.planta.service.bi.inventario.InformeInventarioService;
import exotic.app.planta.service.bi.inventario.InformeInventarioDetalleService;
import exotic.app.planta.service.bi.inventario.MaterialOpExcelService;
import exotic.app.planta.service.bi.inventario.OcmPendientesExcelService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @MockBean
    private AlertasInventarioDetalleService alertDetailService;

    @MockBean
    private OcmPendientesExcelService pendingPurchaseOrderExcelService;

    @MockBean
    private MaterialOpExcelService materialOpExcelService;

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
        when(coverageService.calculate(
                90,
                FuenteDemandaCobertura.SOLO_DISPENSACIONES,
                "TODOS",
                "TODOS",
                null,
                "AGOTAMIENTO",
                null,
                0,
                10))
                .thenReturn(CoberturaMaterialesDTO.builder()
                        .ventanaDias(90)
                        .fuenteDemanda(FuenteDemandaCobertura.SOLO_DISPENSACIONES)
                        .estado(CoberturaMaterialesDTO.EstadoCobertura.SIN_CONSUMO)
                        .motivosConfianzaBaja(List.of())
                        .estimaciones(List.of())
                        .build());

        mockMvc.perform(get("/bi/informes-globales/almacen/cobertura"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ventanaDias").value(90))
                .andExpect(jsonPath("$.fuenteDemanda").value("SOLO_DISPENSACIONES"))
                .andExpect(jsonPath("$.estado").value("SIN_CONSUMO"));
    }

    @Test
    void coverageAcceptsExpandedDemandSource() throws Exception {
        when(coverageService.calculate(
                30,
                FuenteDemandaCobertura.DISPENSACIONES_MAS_CONTINGENCIAS,
                "TODOS",
                "TODOS",
                null,
                "AGOTAMIENTO",
                null,
                0,
                10))
                .thenReturn(CoberturaMaterialesDTO.builder()
                        .ventanaDias(30)
                        .fuenteDemanda(
                                FuenteDemandaCobertura.DISPENSACIONES_MAS_CONTINGENCIAS)
                        .escenarioExploratorio(true)
                        .estado(CoberturaMaterialesDTO.EstadoCobertura.ESTIMADO)
                        .motivosConfianzaBaja(List.of())
                        .estimaciones(List.of())
                        .build());

        mockMvc.perform(get("/bi/informes-globales/almacen/cobertura")
                        .param("ventanaDias", "30")
                        .param(
                                "fuenteDemanda",
                                "DISPENSACIONES_MAS_CONTINGENCIAS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuenteDemanda")
                        .value("DISPENSACIONES_MAS_CONTINGENCIAS"))
                .andExpect(jsonPath("$.escenarioExploratorio").value(true));
    }

    @Test
    void coverageAcceptsExplorationFilters() throws Exception {
        when(coverageService.calculate(
                30,
                FuenteDemandaCobertura.SOLO_DISPENSACIONES,
                "HASTA_7_DIAS",
                "EMPAQUE",
                "U",
                "MAYOR_DEMANDA",
                "envase",
                1,
                20))
                .thenReturn(CoberturaMaterialesDTO.builder()
                        .ventanaDias(30)
                        .fuenteDemanda(FuenteDemandaCobertura.SOLO_DISPENSACIONES)
                        .estado(CoberturaMaterialesDTO.EstadoCobertura.ESTIMADO)
                        .motivosConfianzaBaja(List.of())
                        .estimaciones(List.of())
                        .facetas(CoberturaMaterialesDTO.FacetasCoberturaDTO.builder()
                                .gruposDisponibles(List.of("EMPAQUE"))
                                .unidadesDisponibles(List.of("U"))
                                .build())
                        .pagina(new PaginaInformeInventarioDTO<>(
                                List.of(), 0, 20, 8, 1, true, true))
                        .build());

        mockMvc.perform(get("/bi/informes-globales/almacen/cobertura")
                        .param("ventanaDias", "30")
                        .param("horizonte", "HASTA_7_DIAS")
                        .param("grupo", "EMPAQUE")
                        .param("unidad", "U")
                        .param("orden", "MAYOR_DEMANDA")
                        .param("buscar", "envase")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina.totalElements").value(8))
                .andExpect(jsonPath("$.facetas.unidadesDisponibles[0]")
                        .value("U"));

        verify(coverageService).calculate(
                30,
                FuenteDemandaCobertura.SOLO_DISPENSACIONES,
                "HASTA_7_DIAS",
                "EMPAQUE",
                "U",
                "MAYOR_DEMANDA",
                "envase",
                1,
                20);
    }

    @Test
    void coverageExcelUsesTheExplorationSelectionWithoutPagination()
            throws Exception {
        byte[] excel = new byte[] {1, 2, 3};
        when(coverageService.exportExcel(
                30,
                FuenteDemandaCobertura.DISPENSACIONES_MAS_CONTINGENCIAS,
                "HASTA_7_DIAS",
                "EMPAQUE",
                "U",
                "MAYOR_DEMANDA",
                "envase"))
                .thenReturn(new CoberturaMaterialesService.ExcelExport(
                        excel,
                        LocalDateTime.of(2026, 7, 18, 10, 5)));

        mockMvc.perform(get(
                        "/bi/informes-globales/almacen/cobertura/excel")
                        .param("ventanaDias", "30")
                        .param(
                                "fuenteDemanda",
                                "DISPENSACIONES_MAS_CONTINGENCIAS")
                        .param("horizonte", "HASTA_7_DIAS")
                        .param("grupo", "EMPAQUE")
                        .param("unidad", "U")
                        .param("orden", "MAYOR_DEMANDA")
                        .param("buscar", "envase"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"cobertura_materiales_2026-07-18_1005.xlsx\""))
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(content().bytes(excel));

        verify(coverageService).exportExcel(
                30,
                FuenteDemandaCobertura.DISPENSACIONES_MAS_CONTINGENCIAS,
                "HASTA_7_DIAS",
                "EMPAQUE",
                "U",
                "MAYOR_DEMANDA",
                "envase");
    }

    @Test
    void coverageExcelReturnsBadRequestForInvalidSelection() throws Exception {
        when(coverageService.exportExcel(
                90,
                FuenteDemandaCobertura.SOLO_DISPENSACIONES,
                "TODOS",
                "TODOS",
                null,
                "MAYOR_DEMANDA",
                null))
                .thenThrow(new IllegalArgumentException(
                        "Debe seleccionar una unidad para ordenar por demanda."));

        mockMvc.perform(get(
                        "/bi/informes-globales/almacen/cobertura/excel")
                        .param("orden", "MAYOR_DEMANDA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Debe seleccionar una unidad para ordenar por demanda."));
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
    void pendingPurchaseOrdersExcelReturnsTheFreshCompleteWorkbook()
            throws Exception {
        byte[] excel = new byte[] {4, 5, 6};
        when(pendingPurchaseOrderExcelService.exportExcel())
                .thenReturn(new OcmPendientesExcelService.ExcelExport(
                        excel,
                        LocalDateTime.of(2026, 7, 18, 10, 5)));

        mockMvc.perform(get(
                        "/bi/informes-globales/almacen/ocm-pendientes/excel"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"ocm_pendientes_2026-07-18_1005.xlsx\""))
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(content().bytes(excel));

        verify(pendingPurchaseOrderExcelService).exportExcel();
    }

    @Test
    void wipMaterialUsesIndependentPagination() throws Exception {
        when(detailService.getWipMaterialEstimate(1, 10))
                .thenReturn(new PaginaInformeInventarioDTO<>(
                        List.of(), 1, 10, 14, 2, false, true));

        mockMvc.perform(get(
                        "/bi/informes-globales/almacen/wip-material-estimado")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(14));

        verify(detailService).getWipMaterialEstimate(1, 10);
    }

    @Test
    void materialOpExcelEndpointsReturnTimestampedWorkbooks()
            throws Exception {
        byte[] dispensed = new byte[] {7, 8};
        byte[] wip = new byte[] {9, 10};
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 18, 10, 5);
        when(materialOpExcelService.exportDispensedMaterial())
                .thenReturn(new MaterialOpExcelService.ExcelExport(
                        dispensed,
                        cutoff));
        when(materialOpExcelService.exportWipMaterial())
                .thenReturn(new MaterialOpExcelService.ExcelExport(wip, cutoff));

        mockMvc.perform(get(
                        "/bi/informes-globales/almacen/op-material-directo/excel"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"material_dispensado_op_abiertas_2026-07-18_1005.xlsx\""))
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(content().bytes(dispensed));

        mockMvc.perform(get(
                        "/bi/informes-globales/almacen/wip-material-estimado/excel"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"wip_material_estimado_2026-07-18_1005.xlsx\""))
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(content().bytes(wip));

        verify(materialOpExcelService).exportDispensedMaterial();
        verify(materialOpExcelService).exportWipMaterial();
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

    @Test
    void materialAlertsAcceptExplorationFilters() throws Exception {
        when(alertDetailService.getAlerts(
                "AGOTADO",
                "EMPAQUE",
                "U",
                "STOCK_ASC",
                "envase",
                1,
                20))
                .thenReturn(AlertasMaterialesExploracionDTO.builder()
                        .resumen(AlertasMaterialesExploracionDTO.ResumenAlertasDTO
                                .builder()
                                .total(12)
                                .agotadas(8)
                                .build())
                        .prioritarios(List.of())
                        .facetas(AlertasMaterialesExploracionDTO.FacetasAlertasDTO
                                .builder()
                                .gruposDisponibles(List.of("EMPAQUE"))
                                .unidadesDisponibles(List.of("U"))
                                .build())
                        .pagina(new PaginaInformeInventarioDTO<>(
                                List.of(), 0, 20, 8, 1, true, true))
                        .build());

        mockMvc.perform(get("/bi/informes-globales/almacen/alertas-materiales")
                        .param("tipo", "AGOTADO")
                        .param("grupo", "EMPAQUE")
                        .param("unidad", "U")
                        .param("orden", "STOCK_ASC")
                        .param("buscar", "envase")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.agotadas").value(8))
                .andExpect(jsonPath("$.pagina.totalElements").value(8));

        verify(alertDetailService).getAlerts(
                "AGOTADO",
                "EMPAQUE",
                "U",
                "STOCK_ASC",
                "envase",
                1,
                20);
    }
}
