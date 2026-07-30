package exotic.app.planta.service.commons;

import exotic.app.planta.model.commons.dto.CargaPuntosReordenDTOs;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.repo.producto.MaterialRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CargaMasivaPuntosReordenServiceTest {

    private MaterialRepo materialRepo;
    private ProductoRepo productoRepo;
    private CargaMasivaPuntosReordenService service;

    @BeforeEach
    void setUp() {
        materialRepo = mock(MaterialRepo.class);
        productoRepo = mock(ProductoRepo.class);
        service = new CargaMasivaPuntosReordenService(materialRepo, productoRepo);
    }

    @Test
    void generateTemplateContainsCurrentInventariableMaterialsAndInstructions() throws Exception {
        Material first = material("MAT-001", "Material uno", 12.5, true);
        Material second = material("MAT-002", "Material dos", -1, true);
        when(materialRepo.findByInventareableTrueOrderByProductoIdAsc())
                .thenReturn(List.of(first, second));

        byte[] bytes = service.generateTemplateExcel();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet data = workbook.getSheet("Puntos de reorden");
            assertEquals("codigo", data.getRow(0).getCell(0).getStringCellValue());
            assertEquals("nuevo_punto_reorden", data.getRow(0).getCell(3).getStringCellValue());
            assertEquals("MAT-001", data.getRow(1).getCell(0).getStringCellValue());
            assertEquals(12.5, data.getRow(1).getCell(2).getNumericCellValue());
            assertTrue(data.getRow(1).getCell(0).getCellStyle().getLocked());
            assertFalse(data.getRow(1).getCell(3).getCellStyle().getLocked());
            assertTrue(workbook.getSheet("Instrucciones") != null);
        }
    }

    @Test
    void validateClassifiesIgnoredUnchangedAndChangedRows() throws Exception {
        Material changed = material("MAT-001", "Material uno", 10, true);
        Material unchanged = material("MAT-003", "Material tres", 0, true);
        when(productoRepo.findAllById(anyCollection()))
                .thenReturn(List.of(changed, unchanged));

        MockMultipartFile file = workbookFile(List.of(
                row("MAT-001", "Nombre alterado", 10.0, 15.5),
                row("MAT-002", "Material dos", -1.0, null),
                row("MAT-003", "Material tres", 0.0, 0.0)
        ));

        CargaPuntosReordenDTOs.ValidationResponse result = service.validateExcel(file);

        assertTrue(result.valid());
        assertEquals(3, result.totalRows());
        assertEquals(1, result.ignoredRows());
        assertEquals(1, result.unchangedRows());
        assertEquals(1, result.updateRows());
        assertEquals("Material uno", result.changes().getFirst().nombre());
        assertEquals(15.5, result.changes().getFirst().newValue());
    }

    @Test
    void validateIgnoresBlankNewValueEvenWhenCurrentValueIsStale() throws Exception {
        MockMultipartFile file = workbookFile(List.of(
                row("MAT-001", "Material uno", 10.0, null)
        ));

        CargaPuntosReordenDTOs.ValidationResponse result = service.validateExcel(file);

        assertTrue(result.valid());
        assertEquals(1, result.ignoredRows());
        assertEquals(0, result.updateRows());
        verify(productoRepo, never()).findAllById(anyCollection());
    }

    @Test
    void validateReportsStaleCurrentValueAsConflictDetail() throws Exception {
        Material material = material("MAT-001", "Material uno", 20, true);
        when(productoRepo.findAllById(anyCollection())).thenReturn(List.of(material));

        CargaPuntosReordenDTOs.ValidationResponse result = service.validateExcel(
                workbookFile(List.of(row("MAT-001", "Material uno", 10.0, 15.0))));

        assertFalse(result.valid());
        assertEquals(0, result.updateRows());
        assertEquals("punto_reorden_actual", result.errors().getFirst().columnName());
        assertTrue(result.errors().getFirst().message().contains("ahora es 20"));
    }

    @Test
    void validateRejectsUnknownNonMaterialNonInventariableAndDuplicateCodes() throws Exception {
        Terminado terminado = new Terminado();
        terminado.setProductoId("TERM-001");
        Material nonInventariable = material("MAT-DIRECTO", "Consumo directo", -1, false);
        when(productoRepo.findAllById(anyCollection()))
                .thenReturn(List.of(terminado, nonInventariable));

        CargaPuntosReordenDTOs.ValidationResponse result = service.validateExcel(
                workbookFile(List.of(
                        row("NO-EXISTE", "Desconocido", 0.0, 1.0),
                        row("TERM-001", "Terminado", 0.0, 1.0),
                        row("MAT-DIRECTO", "Consumo directo", -1.0, 0.0),
                        row("MAT-DIRECTO", "Duplicado", -1.0, 2.0)
                )));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.message().contains("No existe")));
        assertTrue(result.errors().stream().anyMatch(error -> error.message().contains("no es material")));
        assertTrue(result.errors().stream().anyMatch(error -> error.message().contains("no es inventariable")));
        assertTrue(result.errors().stream().anyMatch(error -> error.message().contains("duplicado")));
    }

    @Test
    void executeUpdatesAllValidatedMaterials() throws Exception {
        Material first = material("MAT-001", "Material uno", 10, true);
        Material second = material("MAT-002", "Material dos", -1, true);
        when(materialRepo.findAllByProductoIdInForUpdate(anyCollection()))
                .thenReturn(List.of(first, second));
        when(materialRepo.saveAllAndFlush(anyList())).thenReturn(List.of(first, second));

        CargaPuntosReordenDTOs.ExecutionResponse result = service.execute(workbookFile(List.of(
                row("MAT-001", "Material uno", 10.0, 15.0),
                row("MAT-002", "Material dos", -1.0, 0.0)
        )));

        assertTrue(result.success());
        assertEquals(2, result.updatedRows());
        assertEquals(15, first.getPuntoReorden());
        assertEquals(0, second.getPuntoReorden());
        verify(materialRepo).saveAllAndFlush(anyList());
    }

    @Test
    void executeDoesNotWriteWhenAnyRowIsInvalid() throws Exception {
        Material validMaterial = material("MAT-001", "Material uno", 10, true);
        when(materialRepo.findAllByProductoIdInForUpdate(anyCollection()))
                .thenReturn(List.of(validMaterial));
        MockMultipartFile file = workbookFile(List.of(
                row("MAT-001", "Material uno", 10.0, 15.0),
                row("MAT-002", "Material dos", 5.0, -2.0)
        ));

        assertThrows(CargaPuntosReordenValidationException.class, () -> service.execute(file));

        assertEquals(10, validMaterial.getPuntoReorden());
        verify(materialRepo, never()).saveAllAndFlush(anyList());
    }

    private static Material material(
            String productoId,
            String nombre,
            double puntoReorden,
            boolean inventareable
    ) {
        Material material = new Material();
        material.setProductoId(productoId);
        material.setNombre(nombre);
        material.setPuntoReorden(puntoReorden);
        material.setInventareable(inventareable);
        return material;
    }

    private static ExcelRow row(
            String productoId,
            String nombre,
            Double currentValue,
            Double newValue
    ) {
        return new ExcelRow(productoId, nombre, currentValue, newValue);
    }

    private static MockMultipartFile workbookFile(List<ExcelRow> rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Puntos de reorden");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("codigo");
            header.createCell(1).setCellValue("nombre");
            header.createCell(2).setCellValue("punto_reorden_actual");
            header.createCell(3).setCellValue("nuevo_punto_reorden");

            for (int index = 0; index < rows.size(); index++) {
                ExcelRow data = rows.get(index);
                Row row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(data.productoId());
                row.createCell(1).setCellValue(data.nombre());
                if (data.currentValue() != null) {
                    row.createCell(2).setCellValue(data.currentValue());
                }
                if (data.newValue() != null) {
                    row.createCell(3).setCellValue(data.newValue());
                }
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "puntos_reorden.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray());
        }
    }

    private record ExcelRow(
            String productoId,
            String nombre,
            Double currentValue,
            Double newValue
    ) {
    }
}
