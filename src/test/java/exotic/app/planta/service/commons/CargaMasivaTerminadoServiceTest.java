package exotic.app.planta.service.commons;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.commons.dto.ValidationResultDTO;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CargaMasivaTerminadoServiceTest {

    @Mock
    private ProductoRepo productoRepo;

    @Mock
    private TerminadoRepo terminadoRepo;

    @Mock
    private CategoriaRepo categoriaRepo;

    private CargaMasivaTerminadoService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new CargaMasivaTerminadoService(productoRepo, terminadoRepo, categoriaRepo, objectMapper);
    }

    @Test
    void validateExcelSinInsumos_rejectsNumericProductoIdCells() throws Exception {
        MockMultipartFile file = excelFile(new Object[][]{
                {100043d, "Terminado 1", "", 10d, 19d, "U", 1d, 0d, 0d, "", "", ""}
        });

        ValidationResultDTO result = service.validateExcelSinInsumos(file);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> "producto_id".equals(error.getColumnName())));
        assertTrue(result.getErrors().stream().anyMatch(error -> error.getMessage().contains("celdas numericas")));
    }

    @Test
    void processBulkInsertSinInsumos_doesNotPersistInvalidProductoIds() throws Exception {
        MockMultipartFile file = excelFile(new Object[][]{
                {" abc123 ", "Terminado 1", "", 10d, 19d, "U", 1d, 0d, 0d, "", "", ""}
        });

        ValidationResultDTO result = service.processBulkInsertSinInsumos(file);

        assertFalse(result.isValid());
        verify(terminadoRepo, never()).save(any());
    }

    @Test
    void validateJsonConInsumos_rejectsSchemaVersionAndInvalidIds() {
        MockMultipartFile file = jsonFile("""
                {
                  "schemaVersion": 2,
                  "exportedAt": "2026-04-13T10:00:00",
                  "terminados": [
                    {
                      "productoId": "abc123",
                      "nombre": "Terminado 1",
                      "observaciones": null,
                      "costo": 10,
                      "ivaPercentual": 19,
                      "tipoUnidades": "U",
                      "cantidadUnidad": 1,
                      "stockMinimo": 0,
                      "inventareable": true,
                      "status": 0,
                      "categoria": null,
                      "fotoUrl": null,
                      "prefijoLote": null,
                      "insumos": [
                        {
                          "insumoId": 1,
                          "cantidadRequerida": 1,
                          "producto": {
                            "productoId": "AB_123",
                            "nombre": "Insumo 1",
                            "tipoProducto": "M"
                          }
                        }
                      ]
                    }
                  ]
                }
                """);

        ValidationResultDTO result = service.validateJsonConInsumos(file);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> "schemaVersion".equals(error.getColumnName())));
        assertTrue(result.getErrors().stream().anyMatch(error -> "productoId".equals(error.getColumnName())));
        assertTrue(result.getErrors().stream().anyMatch(error -> "insumos.producto.productoId".equals(error.getColumnName())));
    }

    @Test
    void processBulkInsertJsonConInsumos_persistsValidUppercaseIds() {
        Material insumo = new Material();
        insumo.setProductoId("INSUMO01");
        when(productoRepo.findByProductoId("TERMINADO01")).thenReturn(Optional.empty());
        when(productoRepo.findByProductoId("INSUMO01")).thenReturn(Optional.of(insumo));

        MockMultipartFile file = jsonFile("""
                {
                  "schemaVersion": 1,
                  "exportedAt": "2026-04-13T10:00:00",
                  "terminados": [
                    {
                      "productoId": "TERMINADO01",
                      "nombre": "Terminado 1",
                      "observaciones": null,
                      "costo": 10,
                      "ivaPercentual": 19,
                      "tipoUnidades": "U",
                      "cantidadUnidad": 1,
                      "stockMinimo": 0,
                      "inventareable": true,
                      "status": 0,
                      "categoria": null,
                      "fotoUrl": null,
                      "prefijoLote": null,
                      "insumos": [
                        {
                          "insumoId": 1,
                          "cantidadRequerida": 1,
                          "producto": {
                            "productoId": "INSUMO01",
                            "nombre": "Insumo 1",
                            "tipoProducto": "M"
                          }
                        }
                      ]
                    }
                  ]
                }
                """);

        ValidationResultDTO result = service.processBulkInsertJsonConInsumos(file);

        assertTrue(result.isValid());
        verify(terminadoRepo).save(any());
    }

    private MockMultipartFile excelFile(Object[][] rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Datos");
            Row header = sheet.createRow(0);
            String[] headers = {
                    "producto_id", "nombre", "observaciones", "costo", "iva_percentual", "tipo_unidades",
                    "cantidad_unidad", "stock_minimo", "status", "categoria_id", "foto_url", "prefijo_lote"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                for (int cellIndex = 0; cellIndex < rows[rowIndex].length; cellIndex++) {
                    Object value = rows[rowIndex][cellIndex];
                    if (value instanceof Number number) {
                        row.createCell(cellIndex).setCellValue(number.doubleValue());
                    } else {
                        row.createCell(cellIndex).setCellValue(value != null ? value.toString() : "");
                    }
                }
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "terminados.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }

    private MockMultipartFile jsonFile(String json) {
        return new MockMultipartFile(
                "file",
                "terminados.json",
                "application/json",
                json.getBytes(StandardCharsets.UTF_8)
        );
    }
}
