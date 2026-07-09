package exotic.app.planta.modules.transaccionesalmacen.ingreso_terminados;

import exotic.app.planta.model.inventarios.dto.IngresoMasivoRequestDTO;
import exotic.app.planta.model.inventarios.dto.IngresoTerminadoRequestDTO;
import exotic.app.planta.model.inventarios.dto.ReporteHyLRequestDTO;
import exotic.app.planta.modules.transaccionesalmacen.support.AbstractTransaccionesAlmacenIntegrationTest;
import exotic.app.planta.modules.transaccionesalmacen.support.TransaccionesAlmacenFixtureFactory.ModuleFixture;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransaccionesAlmacenIngresoTerminadosIntegrationTest extends AbstractTransaccionesAlmacenIntegrationTest {

    @Test
    void buscarOpPorLoteYPlantilla_returnOperationalData() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();
        LocalDate fechaReporte = LocalDate.of(2026, 6, 30);

        mockMvc.perform(get("/ingresos_terminados_almacen/buscar-op-por-lote")
                        .with(bearerToken())
                        .param("loteAsignado", fixture.ordenAbierta().getLoteAsignado()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordenProduccion.ordenId").value(fixture.ordenAbierta().getOrdenId()))
                .andExpect(jsonPath("$.terminado.productoId").value(fixture.terminado().getProductoId()));

        MvcResult result = mockMvc.perform(get("/ingresos_terminados_almacen/plantilla")
                        .with(bearerToken())
                        .param("fechaReporte", fechaReporte.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.containsString("plantilla_reporte_produccion_terminados_" + fechaReporte)))
                .andReturn();

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheet("Produccion Diaria PT");
            assertNotNull(sheet);
            Row headerRow = sheet.getRow(0);
            assertNotNull(headerRow);
            assertEquals("producto_id", headerRow.getCell(0).getStringCellValue());
            assertEquals("producto_nombre", headerRow.getCell(1).getStringCellValue());
            assertEquals("categoria_nombre", headerRow.getCell(2).getStringCellValue());
            assertEquals("cantidad_producida", headerRow.getCell(3).getStringCellValue());
            assertEquals("fecha_reporte", headerRow.getCell(4).getStringCellValue());

            Row firstDataRow = sheet.getRow(1);
            assertNotNull(firstDataRow);
            assertEquals(fechaReporte.toString(), firstDataRow.getCell(4).getStringCellValue());
        }
    }

    @Test
    void reporteHyL_generatesBinaryXlsWithRealCostsAndConsolidatesRows() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();
        LocalDate fechaReporte = LocalDate.of(2026, 6, 30);
        ReporteHyLRequestDTO payload = new ReporteHyLRequestDTO(
                fechaReporte,
                false,
                List.of(
                        new ReporteHyLRequestDTO.IngresoHyLItemDTO(
                                fixture.terminado().getProductoId(),
                                fixture.terminado().getNombre(),
                                3
                        ),
                        new ReporteHyLRequestDTO.IngresoHyLItemDTO(
                                fixture.terminado().getProductoId(),
                                fixture.terminado().getNombre(),
                                4
                        ),
                        new ReporteHyLRequestDTO.IngresoHyLItemDTO(
                                fixture.terminado().getProductoId(),
                                fixture.terminado().getNombre(),
                                0
                        )
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
                        fixture.terminado().getProductoId(),
                        fixture.terminado().getNombre(),
                        5
                ))
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
                        fixture.terminado().getProductoId(),
                        fixture.terminado().getNombre(),
                        0
                ))
        );

        mockMvc.perform(post("/ingresos_terminados_almacen/reporte-hyl")
                        .with(bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reporteHyL_rejectsMissingProductWhenRealCostsAreRequired() throws Exception {
        ReporteHyLRequestDTO payload = new ReporteHyLRequestDTO(
                LocalDate.of(2026, 6, 30),
                false,
                List.of(new ReporteHyLRequestDTO.IngresoHyLItemDTO(
                        "PT-NO-EXISTE",
                        "Producto inexistente",
                        2
                ))
        );

        mockMvc.perform(post("/ingresos_terminados_almacen/reporte-hyl")
                        .with(bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isNotFound());
    }

    @Test
    void reporteHyL_rejectsMissingProductEvenWhenCostsAreZero() throws Exception {
        ReporteHyLRequestDTO payload = new ReporteHyLRequestDTO(
                LocalDate.of(2026, 6, 30),
                true,
                List.of(new ReporteHyLRequestDTO.IngresoHyLItemDTO(
                        "PT-NO-EXISTE",
                        "Producto inexistente",
                        2
                ))
        );

        mockMvc.perform(post("/ingresos_terminados_almacen/reporte-hyl")
                        .with(bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isNotFound());
    }

    @Test
    void registrarIngresoTerminado_createsBackflushAndClosesOrder() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();

        IngresoTerminadoRequestDTO payload = new IngresoTerminadoRequestDTO(
                "master",
                fixture.ordenAbierta().getOrdenId(),
                8,
                LocalDate.now().plusMonths(12),
                "Ingreso terminado desde test"
        );

        mockMvc.perform(post("/ingresos_terminados_almacen/registrar")
                        .with(bearerToken())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoEntidadCausante").value("OP"))
                .andExpect(jsonPath("$.movimientosTransaccion[0].tipoMovimiento").value("BACKFLUSH"));
    }

    @Test
    void registrarMasivo_returnsMultiStatusWhenOneOrderFails() throws Exception {
        ModuleFixture fixture = fixtureFactory.seedModuleFixture();

        IngresoMasivoRequestDTO payload = new IngresoMasivoRequestDTO(
                "master",
                List.of(
                        new IngresoMasivoRequestDTO.IngresoItemDTO(
                                fixture.ordenMasivo().getOrdenId(),
                                5,
                                LocalDate.now().plusMonths(9)
                        ),
                        new IngresoMasivoRequestDTO.IngresoItemDTO(
                                fixture.ordenCancelada().getOrdenId(),
                                5,
                                LocalDate.now().plusMonths(9)
                        )
                )
        );

        mockMvc.perform(post("/ingresos_terminados_almacen/registrar-masivo")
                        .with(bearerToken())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().is(207))
                .andExpect(jsonPath("$.totalProcesados").value(2))
                .andExpect(jsonPath("$.exitosos").value(1))
                .andExpect(jsonPath("$.fallidos").value(1));
    }
}
