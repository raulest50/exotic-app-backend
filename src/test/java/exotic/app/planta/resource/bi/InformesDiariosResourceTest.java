package exotic.app.planta.resource.bi;

import exotic.app.planta.service.bi.BiExcelExportMode;
import exotic.app.planta.service.bi.BiExcelExportOptions;
import exotic.app.planta.service.bi.ExcelDecimalSeparator;
import exotic.app.planta.service.bi.InformesDiariosService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InformesDiariosResource.class)
@AutoConfigureMockMvc(addFilters = false)
class InformesDiariosResourceTest {

    private static final byte[] EXCEL_BYTES = new byte[] {1, 2, 3};

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InformesDiariosService informesDiariosService;

    @Test
    void exportarComprasExcel_acceptsSingleDate() throws Exception {
        LocalDate fecha = LocalDate.of(2026, 6, 15);
        when(informesDiariosService.exportarComprasExcel(eq(fecha), eq(fecha), any(BiExcelExportOptions.class)))
                .thenReturn(EXCEL_BYTES);

        mockMvc.perform(get("/bi/informes-diarios/compras/excel")
                        .param("fecha", "2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"informe_compras_ocm_2026-06-15.xlsx\""));

        verify(informesDiariosService).exportarComprasExcel(eq(fecha), eq(fecha), any(BiExcelExportOptions.class));
    }

    @Test
    void exportarIngresoMaterialesExcel_acceptsDateRangeAndDecimalSeparator() throws Exception {
        LocalDate desde = LocalDate.of(2026, 6, 1);
        LocalDate hasta = LocalDate.of(2026, 6, 30);
        when(informesDiariosService.exportarIngresoMaterialesExcel(
                eq(desde), eq(hasta), any(BiExcelExportOptions.class)))
                .thenReturn(EXCEL_BYTES);

        mockMvc.perform(get("/bi/informes-diarios/almacen/ingreso-materiales/excel")
                        .param("fechaDesde", "2026-06-01")
                        .param("fechaHasta", "2026-06-30")
                        .param("exportMode", "TEXT_DETERMINISTIC")
                        .param("decimalSeparator", "DOT"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"informe_ingreso_materiales_2026-06-01_a_2026-06-30.xlsx\""));

        ArgumentCaptor<BiExcelExportOptions> optionsCaptor = ArgumentCaptor.forClass(BiExcelExportOptions.class);
        verify(informesDiariosService).exportarIngresoMaterialesExcel(eq(desde), eq(hasta), optionsCaptor.capture());
        assertEquals(BiExcelExportMode.TEXT_DETERMINISTIC, optionsCaptor.getValue().exportMode());
        assertEquals(ExcelDecimalSeparator.DOT, optionsCaptor.getValue().decimalSeparator());
    }

    @Test
    void exportarComprasExcel_acceptsNumericExportMode() throws Exception {
        LocalDate fecha = LocalDate.of(2026, 6, 15);
        when(informesDiariosService.exportarComprasExcel(eq(fecha), eq(fecha), any(BiExcelExportOptions.class)))
                .thenReturn(EXCEL_BYTES);

        mockMvc.perform(get("/bi/informes-diarios/compras/excel")
                        .param("fecha", "2026-06-15")
                        .param("exportMode", "NUMERIC")
                        .param("decimalSeparator", "COMMA"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"informe_compras_ocm_2026-06-15.xlsx\""));

        ArgumentCaptor<BiExcelExportOptions> optionsCaptor = ArgumentCaptor.forClass(BiExcelExportOptions.class);
        verify(informesDiariosService).exportarComprasExcel(eq(fecha), eq(fecha), optionsCaptor.capture());
        assertEquals(BiExcelExportMode.NUMERIC, optionsCaptor.getValue().exportMode());
        assertNull(optionsCaptor.getValue().decimalSeparator());
    }

    @Test
    void exportarComprasExcel_legacyDecimalSeparatorUsesTextMode() throws Exception {
        LocalDate fecha = LocalDate.of(2026, 6, 15);
        when(informesDiariosService.exportarComprasExcel(eq(fecha), eq(fecha), any(BiExcelExportOptions.class)))
                .thenReturn(EXCEL_BYTES);

        mockMvc.perform(get("/bi/informes-diarios/compras/excel")
                        .param("fecha", "2026-06-15")
                        .param("decimalSeparator", "DOT"))
                .andExpect(status().isOk());

        ArgumentCaptor<BiExcelExportOptions> optionsCaptor = ArgumentCaptor.forClass(BiExcelExportOptions.class);
        verify(informesDiariosService).exportarComprasExcel(eq(fecha), eq(fecha), optionsCaptor.capture());
        assertEquals(BiExcelExportMode.TEXT_DETERMINISTIC, optionsCaptor.getValue().exportMode());
        assertEquals(ExcelDecimalSeparator.DOT, optionsCaptor.getValue().decimalSeparator());
    }

    @Test
    void exportarDispensacionMaterialesExcel_acceptsDateRange() throws Exception {
        LocalDate desde = LocalDate.of(2026, 6, 1);
        LocalDate hasta = LocalDate.of(2026, 6, 30);
        when(informesDiariosService.exportarDispensacionMaterialesExcel(
                eq(desde), eq(hasta), any(BiExcelExportOptions.class)))
                .thenReturn(EXCEL_BYTES);

        mockMvc.perform(get("/bi/informes-diarios/almacen/dispensacion-materiales/excel")
                        .param("fechaDesde", "2026-06-01")
                        .param("fechaHasta", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"informe_dispensacion_materiales_2026-06-01_a_2026-06-30.xlsx\""));

        verify(informesDiariosService).exportarDispensacionMaterialesExcel(
                eq(desde), eq(hasta), any(BiExcelExportOptions.class));
    }

    @Test
    void exportarIngresoTerminadosExcel_acceptsDateRange() throws Exception {
        LocalDate desde = LocalDate.of(2026, 6, 1);
        LocalDate hasta = LocalDate.of(2026, 6, 30);
        when(informesDiariosService.exportarIngresoTerminadosExcel(
                eq(desde), eq(hasta), any(BiExcelExportOptions.class)))
                .thenReturn(EXCEL_BYTES);

        mockMvc.perform(get("/bi/informes-diarios/almacen/ingreso-terminados/excel")
                        .param("fechaDesde", "2026-06-01")
                        .param("fechaHasta", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"informe_ingreso_terminados_2026-06-01_a_2026-06-30.xlsx\""));

        verify(informesDiariosService).exportarIngresoTerminadosExcel(
                eq(desde), eq(hasta), any(BiExcelExportOptions.class));
    }

    @Test
    void exportarComprasExcel_rejectsMixedSingleDateAndRange() throws Exception {
        mockMvc.perform(get("/bi/informes-diarios/compras/excel")
                        .param("fecha", "2026-06-15")
                        .param("fechaDesde", "2026-06-01")
                        .param("fechaHasta", "2026-06-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Use fecha o fechaDesde/fechaHasta, no ambas opciones."));
    }

    @Test
    void exportarComprasExcel_rejectsIncompleteRange() throws Exception {
        mockMvc.perform(get("/bi/informes-diarios/compras/excel")
                        .param("fechaDesde", "2026-06-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Debe enviar fecha o el rango completo fechaDesde/fechaHasta."));
    }

    @Test
    void exportarComprasExcel_rejectsInvertedRange() throws Exception {
        mockMvc.perform(get("/bi/informes-diarios/compras/excel")
                        .param("fechaDesde", "2026-06-30")
                        .param("fechaHasta", "2026-06-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("fechaDesde no puede ser posterior a fechaHasta."));
    }
}
