package exotic.app.planta.modules.transaccionesalmacen.ingreso_terminados;

import exotic.app.planta.model.inventarios.dto.ReporteHyLRequestDTO;
import exotic.app.planta.modules.transaccionesalmacen.support.AbstractTransaccionesAlmacenIntegrationTest;
import exotic.app.planta.modules.transaccionesalmacen.support.TransaccionesAlmacenFixtureFactory.ModuleFixture;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransaccionesAlmacenIngresoTerminadosIntegrationTest
        extends AbstractTransaccionesAlmacenIntegrationTest {

    @Test
    void reporteHyL_generatesBinaryXlsWithRealCostsAndConsolidatesRows() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();
        ReporteHyLRequestDTO payload = new ReporteHyLRequestDTO(
                LocalDate.of(2026, 6, 30),
                false,
                List.of(
                        new ReporteHyLRequestDTO.IngresoHyLItemDTO(
                                fixture.terminado().getProductoId(), fixture.terminado().getNombre(), 3),
                        new ReporteHyLRequestDTO.IngresoHyLItemDTO(
                                fixture.terminado().getProductoId(), fixture.terminado().getNombre(), 4),
                        new ReporteHyLRequestDTO.IngresoHyLItemDTO(
                                fixture.terminado().getProductoId(), fixture.terminado().getNombre(), 0)
                )
        );

        MvcResult result = mockMvc.perform(post("/ingresos_terminados_almacen/reporte-hyl")
                        .with(bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.ms-excel"))
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"reporte_hyl_20260630.xls\""))
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        String prefix = new String(Arrays.copyOf(bytes, Math.min(bytes.length, 5)), StandardCharsets.UTF_8);
        assertFalse(prefix.startsWith("<?xml"));

        try (HSSFWorkbook workbook = new HSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("Reporte HyL");
            assertNotNull(sheet);

            Row headerRow = sheet.getRow(0);
            assertEquals("codigo", headerRow.getCell(0).getStringCellValue());
            assertEquals("nombre", headerRow.getCell(1).getStringCellValue());
            assertEquals("precio1", headerRow.getCell(2).getStringCellValue());
            assertEquals("precio2", headerRow.getCell(3).getStringCellValue());
            assertEquals("precio3", headerRow.getCell(4).getStringCellValue());
            assertEquals("precio4", headerRow.getCell(5).getStringCellValue());
            assertEquals("cantidad", headerRow.getCell(6).getStringCellValue());
            assertEquals("costo", headerRow.getCell(7).getStringCellValue());

            Row dataRow = sheet.getRow(1);
            assertEquals(fixture.terminado().getProductoId(), dataRow.getCell(0).getStringCellValue());
            assertEquals(fixture.terminado().getNombre(), dataRow.getCell(1).getStringCellValue());
            assertEquals(7.0, dataRow.getCell(6).getNumericCellValue());
            assertEquals(35.0, dataRow.getCell(7).getNumericCellValue());
            assertEquals(1, sheet.getLastRowNum());
        }
    }

    @Test
    void reporteHyL_generatesZeroCostsWhenRequested() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();
        ReporteHyLRequestDTO payload = new ReporteHyLRequestDTO(
                LocalDate.of(2026, 6, 30),
                true,
                List.of(new ReporteHyLRequestDTO.IngresoHyLItemDTO(
                        fixture.terminado().getProductoId(), fixture.terminado().getNombre(), 5))
        );

        MvcResult result = mockMvc.perform(post("/ingresos_terminados_almacen/reporte-hyl")
                        .with(bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn();

        try (HSSFWorkbook workbook = new HSSFWorkbook(
                new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            assertEquals(0.0, workbook.getSheet("Reporte HyL").getRow(1).getCell(7).getNumericCellValue());
        }
    }

    @Test
    void reporteHyL_rejectsWithoutPositiveProduction() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();
        ReporteHyLRequestDTO payload = new ReporteHyLRequestDTO(
                LocalDate.of(2026, 6, 30),
                true,
                List.of(new ReporteHyLRequestDTO.IngresoHyLItemDTO(
                        fixture.terminado().getProductoId(), fixture.terminado().getNombre(), 0))
        );

        mockMvc.perform(post("/ingresos_terminados_almacen/reporte-hyl")
                        .with(bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reporteHyL_rejectsMissingProductWhenRealCostsAreRequired() throws Exception {
        ReporteHyLRequestDTO payload = missingProductPayload(false);

        mockMvc.perform(post("/ingresos_terminados_almacen/reporte-hyl")
                        .with(bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isNotFound());
    }

    @Test
    void reporteHyL_rejectsMissingProductEvenWhenCostsAreZero() throws Exception {
        ReporteHyLRequestDTO payload = missingProductPayload(true);

        mockMvc.perform(post("/ingresos_terminados_almacen/reporte-hyl")
                        .with(bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isNotFound());
    }

    private static ReporteHyLRequestDTO missingProductPayload(boolean costosEnCero) {
        return new ReporteHyLRequestDTO(
                LocalDate.of(2026, 6, 30),
                costosEnCero,
                List.of(new ReporteHyLRequestDTO.IngresoHyLItemDTO(
                        "PT-NO-EXISTE", "Producto inexistente", 2))
        );
    }
}
