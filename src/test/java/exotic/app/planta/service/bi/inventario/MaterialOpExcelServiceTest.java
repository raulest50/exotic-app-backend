package exotic.app.planta.service.bi.inventario;

import exotic.app.planta.model.bi.dto.InformeInventarioDTO;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaterialOpExcelServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-18T15:05:00Z"),
            ZoneId.of("America/Bogota"));

    @Test
    void exportsSummaryAndMaterialDetailFromTheSameWipSnapshot()
            throws Exception {
        PendientesInventarioAssembler assembler =
                mock(PendientesInventarioAssembler.class);
        LocalDateTime firstMovement =
                LocalDateTime.of(2026, 7, 10, 8, 15);
        var snapshot = new PendientesInventarioAssembler.MaterialOpSnapshot(
                List.of(new PendientesInventarioAssembler.MaterialOpOrderSnapshot(
                        321,
                        "L-321",
                        11,
                        firstMovement,
                        1,
                        List.of(InformeInventarioDTO.CantidadUnidadDTO.builder()
                                .unidadMedida("KG")
                                .cantidad(4.5)
                                .build()),
                        450)),
                List.of(new PendientesInventarioAssembler.MaterialOpLineSnapshot(
                        321,
                        "L-321",
                        11,
                        firstMovement,
                        firstMovement,
                        "MP-1",
                        "Aceite",
                        "KG",
                        PendientesInventarioAssembler.MaterialOrigin.CONSUMO_DIRECTO,
                        4.5,
                        100,
                        true,
                        450)));
        when(assembler.getAllWipMaterialSnapshot()).thenReturn(snapshot);
        var service = new MaterialOpExcelService(assembler, CLOCK);

        var export = service.exportWipMaterial();

        assertEquals(
                LocalDateTime.of(2026, 7, 18, 10, 5),
                export.cutoff());
        try (var workbook = WorkbookFactory.create(
                new ByteArrayInputStream(export.content()))) {
            assertEquals(2, workbook.getNumberOfSheets());
            var summary = workbook.getSheet("Resumen por OP");
            var detail = workbook.getSheet("Detalle materiales");
            assertEquals(
                    "WIP material estimado",
                    summary.getRow(0).getCell(0).getStringCellValue());
            assertEquals(
                    "INICIO WIP",
                    summary.getRow(3).getCell(3).getStringCellValue());
            assertEquals(
                    "Consumo directo",
                    detail.getRow(4).getCell(4).getStringCellValue());
            assertEquals(
                    CellType.NUMERIC,
                    detail.getRow(4).getCell(8).getCellType());
            assertEquals(
                    450,
                    summary.getRow(4).getCell(6).getNumericCellValue(),
                    0.000001);
            assertEquals(
                    450,
                    detail.getRow(4).getCell(11).getNumericCellValue(),
                    0.000001);
        }
        verify(assembler).getAllWipMaterialSnapshot();
    }

    @Test
    void exportsValidHeaderOnlySheetsWhenThereAreNoOpenOrders()
            throws Exception {
        PendientesInventarioAssembler assembler =
                mock(PendientesInventarioAssembler.class);
        when(assembler.getAllDispensedMaterialSnapshot())
                .thenReturn(new PendientesInventarioAssembler.MaterialOpSnapshot(
                        List.of(),
                        List.of()));
        var service = new MaterialOpExcelService(assembler, CLOCK);

        var export = service.exportDispensedMaterial();

        try (var workbook = WorkbookFactory.create(
                new ByteArrayInputStream(export.content()))) {
            assertEquals(
                    3,
                    workbook.getSheet("Resumen por OP").getLastRowNum());
            assertEquals(
                    3,
                    workbook.getSheet("Detalle materiales").getLastRowNum());
        }
    }
}
