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
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcmPendientesExcelServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-18T15:05:00Z"),
            ZoneId.of("America/Bogota"));

    @Test
    void exportsAllPendingLinesAsNativeExcelCells() throws Exception {
        PendientesInventarioAssembler assembler =
                mock(PendientesInventarioAssembler.class);
        when(assembler.getAllPendingPurchaseOrders()).thenReturn(
                IntStream.rangeClosed(1, 11)
                        .mapToObj(index -> order(
                                100 + index,
                                line(
                                        index,
                                        "MP-" + index,
                                        "Material " + index,
                                        index,
                                        index * 100)))
                        .toList());
        var service = new OcmPendientesExcelService(assembler, CLOCK);

        var export = service.exportExcel();

        assertEquals(
                LocalDateTime.of(2026, 7, 18, 10, 5),
                export.cutoff());
        try (var workbook = WorkbookFactory.create(
                new ByteArrayInputStream(export.content()))) {
            assertEquals(1, workbook.getNumberOfSheets());
            var sheet = workbook.getSheet("OCM pendientes");
            assertEquals(
                    "Materiales pendientes de ingreso por OCM",
                    sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals(
                    "Fecha y hora de corte (hora Colombia)",
                    sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals(
                    CellType.NUMERIC,
                    sheet.getRow(2).getCell(3).getCellType());
            assertEquals(
                    "CANTIDAD PENDIENTE",
                    sheet.getRow(3).getCell(9).getStringCellValue());
            assertEquals(14, sheet.getLastRowNum());
            assertEquals(
                    CellType.NUMERIC,
                    sheet.getRow(4).getCell(1).getCellType());
            assertEquals(
                    CellType.NUMERIC,
                    sheet.getRow(4).getCell(9).getCellType());
            assertEquals(100,
                    sheet.getRow(4).getCell(11).getNumericCellValue(),
                    0.000001);
            assertEquals(1100,
                    sheet.getRow(14).getCell(11).getNumericCellValue(),
                    0.000001);
            double exportedPendingValue = IntStream.rangeClosed(4, 14)
                    .mapToDouble(rowIndex ->
                            sheet.getRow(rowIndex)
                                    .getCell(11)
                                    .getNumericCellValue())
                    .sum();
            assertEquals(6600, exportedPendingValue, 0.000001);
        }
        verify(assembler).getAllPendingPurchaseOrders();
    }

    @Test
    void exportsAValidHeaderOnlyWorkbookWhenThereAreNoPendingOrders()
            throws Exception {
        PendientesInventarioAssembler assembler =
                mock(PendientesInventarioAssembler.class);
        when(assembler.getAllPendingPurchaseOrders()).thenReturn(List.of());
        var service = new OcmPendientesExcelService(assembler, CLOCK);

        var export = service.exportExcel();

        try (var workbook = WorkbookFactory.create(
                new ByteArrayInputStream(export.content()))) {
            var sheet = workbook.getSheet("OCM pendientes");
            assertEquals(3, sheet.getLastRowNum());
            assertEquals(
                    "VALOR PENDIENTE SIN IVA",
                    sheet.getRow(3).getCell(11).getStringCellValue());
        }
    }

    private InformeInventarioDTO.OcmDTO order(
            int orderId,
            InformeInventarioDTO.LineaOcmDTO line
    ) {
        return InformeInventarioDTO.OcmDTO.builder()
                .ocmId(orderId)
                .fechaEmision(LocalDateTime.of(2026, 7, 1, 8, 0))
                .proveedor("Proveedor " + orderId)
                .referencias(1)
                .cantidadesPorUnidad(List.of())
                .valorPendienteSinIva(line.valorPendienteSinIva())
                .lineas(List.of(line))
                .build();
    }

    private InformeInventarioDTO.LineaOcmDTO line(
            int itemId,
            String productId,
            String productName,
            double pending,
            double pendingValue
    ) {
        return InformeInventarioDTO.LineaOcmDTO.builder()
                .itemId(itemId)
                .productoId(productId)
                .productoNombre(productName)
                .unidadMedida("KG")
                .ordenado(pending + 2)
                .recibidoAplicado(2)
                .pendiente(pending)
                .precioUnitarioSinIva(pendingValue / pending)
                .valorPendienteSinIva(pendingValue)
                .build();
    }
}
