package exotic.app.planta.service.bi;

import exotic.app.planta.model.bi.dto.InformeDiarioComprasRowDTO;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.produccion.MasterProductionScheduleSemanalRepo;
import exotic.app.planta.repo.produccion.MpsSemanalDiaRepo;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class InformesDiariosServiceTest {

    private TransaccionAlmacenRepo transaccionAlmacenRepo;
    private InformesDiariosService service;

    @BeforeEach
    void setUp() {
        transaccionAlmacenRepo = Mockito.mock(TransaccionAlmacenRepo.class);
        service = new InformesDiariosService(
                transaccionAlmacenRepo,
                Mockito.mock(MasterProductionScheduleSemanalRepo.class),
                Mockito.mock(MpsSemanalDiaRepo.class),
                Mockito.mock(CategoriaRepo.class));
    }

    @Test
    void exportarIngresoMaterialesExcel_conSeparadorComaEscribeDecimalComoTextoDeterministico() throws Exception {
        when(transaccionAlmacenRepo.findIngresosMaterialPorDia(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                anyCollection()))
                .thenReturn(List.of(movimiento(1234.5)));

        byte[] excel = service.exportarIngresoMaterialesExcel(
                LocalDate.of(2026, 6, 15),
                BiExcelExportOptions.of(ExcelDecimalSeparator.COMMA));

        assertStringCell(excel, "Ingreso materiales", 1, 3, "1.234,50");
    }

    @Test
    void exportarComprasExcel_conSeparadorPuntoEscribeDecimalComoTextoDeterministico() throws Exception {
        when(transaccionAlmacenRepo.findInformeDiarioComprasPorDia(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(TransaccionAlmacen.TipoEntidadCausante.OCM),
                eq(Movimiento.TipoMovimiento.COMPRA)))
                .thenReturn(List.of(compra(1234.5)));

        byte[] excel = service.exportarComprasExcel(
                LocalDate.of(2026, 6, 15),
                BiExcelExportOptions.of(ExcelDecimalSeparator.DOT));

        assertStringCell(excel, "Compras", 1, 9, "1,234.50");
    }

    @Test
    void exportarComprasExcel_sinSeparadorMantieneDecimalComoNumero() throws Exception {
        when(transaccionAlmacenRepo.findInformeDiarioComprasPorDia(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(TransaccionAlmacen.TipoEntidadCausante.OCM),
                eq(Movimiento.TipoMovimiento.COMPRA)))
                .thenReturn(List.of(compra(1234.5)));

        byte[] excel = service.exportarComprasExcel(
                LocalDate.of(2026, 6, 15),
                BiExcelExportOptions.standard());

        assertNumericCell(excel, "Compras", 1, 9, 1234.5);
    }

    private static Movimiento movimiento(double cantidad) {
        Movimiento movimiento = new Movimiento();
        movimiento.setFechaMovimiento(LocalDateTime.of(2026, 6, 15, 10, 30));
        movimiento.setCantidad(cantidad);
        movimiento.setTipoMovimiento(Movimiento.TipoMovimiento.COMPRA);
        movimiento.setAlmacen(Movimiento.Almacen.GENERAL);
        return movimiento;
    }

    private static InformeDiarioComprasRowDTO compra(double cantidad) {
        InformeDiarioComprasRowDTO row = new InformeDiarioComprasRowDTO();
        row.setFechaIngreso(LocalDateTime.of(2026, 6, 15, 11, 0));
        row.setCantidadIngresada(cantidad);
        row.setAlmacen(Movimiento.Almacen.GENERAL);
        return row;
    }

    private static void assertStringCell(
            byte[] excel,
            String sheetName,
            int rowIndex,
            int columnIndex,
            String expectedValue) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(excel))) {
            Cell cell = workbook.getSheet(sheetName).getRow(rowIndex).getCell(columnIndex);
            assertEquals(CellType.STRING, cell.getCellType());
            assertEquals(expectedValue, cell.getStringCellValue());
        }
    }

    private static void assertNumericCell(
            byte[] excel,
            String sheetName,
            int rowIndex,
            int columnIndex,
            double expectedValue) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(excel))) {
            Cell cell = workbook.getSheet(sheetName).getRow(rowIndex).getCell(columnIndex);
            assertEquals(CellType.NUMERIC, cell.getCellType());
            assertEquals(expectedValue, cell.getNumericCellValue(), 0.000001);
        }
    }
}
