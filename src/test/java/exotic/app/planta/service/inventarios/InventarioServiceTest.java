package exotic.app.planta.service.inventarios;

import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.dto.AlcanceInventario;
import exotic.app.planta.model.inventarios.dto.InventarioConsolidadoPageDTO;
import exotic.app.planta.model.inventarios.dto.InventarioExcelRequestDTO;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.dto.ProductoStockDTO;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.service.productos.ProductoService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventarioServiceTest {
    private MovimientosService movimientosService;
    private InventarioService service;

    @BeforeEach
    void setUp() {
        ProductoService productoService = mock(ProductoService.class);
        TransaccionAlmacenRepo movimientoRepo = mock(TransaccionAlmacenRepo.class);
        movimientosService = mock(MovimientosService.class);
        service = new InventarioService(
                productoService,
                movimientoRepo,
                movimientosService,
                Clock.fixed(
                        Instant.parse("2026-08-03T15:42:00Z"),
                        ZoneId.of("America/Bogota")
                )
        );
    }

    @Test
    void disponibleOperativoUsesOnlyGeneralAndReturnsTheAuthoritativeCutoff() {
        ProductoStockDTO row = new ProductoStockDTO(mock(Producto.class), 25.0);
        when(movimientosService.searchProductsWithStockByAlmacenes(
                eq("ENVASE"),
                eq("NOMBRE"),
                eq(0),
                eq(10),
                eq(List.of(Movimiento.Almacen.GENERAL)),
                eq(LocalDateTime.of(2026, 8, 3, 10, 42))
        )).thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 10), 1));

        InventarioConsolidadoPageDTO result = service.getInventarioConsolidado(
                " ENVASE ",
                "nombre",
                0,
                10,
                AlcanceInventario.DISPONIBLE_OPERATIVO,
                null
        );

        assertEquals(List.of(Movimiento.Almacen.GENERAL), result.getAlmacenesIncluidos());
        assertEquals(AlcanceInventario.DISPONIBLE_OPERATIVO, result.getAlcance());
        assertEquals(
                OffsetDateTime.parse("2026-08-03T10:42:00-05:00"),
                result.getFechaHoraCorte()
        );
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void missingScopePreservesThePhysicalTotalAsDefault() {
        List<Movimiento.Almacen> allWarehouses = List.of(Movimiento.Almacen.values());
        when(movimientosService.searchProductsWithStockByAlmacenes(
                eq(""),
                eq("NOMBRE"),
                eq(0),
                eq(10),
                eq(allWarehouses),
                eq(LocalDateTime.of(2026, 8, 3, 10, 42))
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        InventarioConsolidadoPageDTO result = service.getInventarioConsolidado(
                "",
                "NOMBRE",
                0,
                10,
                null,
                null
        );

        assertEquals(AlcanceInventario.FISICO_TOTAL, result.getAlcance());
        assertEquals(allWarehouses, result.getAlmacenesIncluidos());
    }

    @Test
    void personalizadoRejectsEmptyOrDuplicatedWarehouseSelections() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getInventarioConsolidado(
                        "",
                        "NOMBRE",
                        0,
                        10,
                        AlcanceInventario.PERSONALIZADO,
                        List.of()
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getInventarioConsolidado(
                        "",
                        "NOMBRE",
                        0,
                        10,
                        AlcanceInventario.PERSONALIZADO,
                        List.of(Movimiento.Almacen.GENERAL, Movimiento.Almacen.GENERAL)
                )
        );
    }

    @Test
    void inventoryExcelUsesOneCutoffAndDocumentsTheSelectedScope() throws Exception {
        Producto producto = mock(Producto.class);
        when(producto.getProductoId()).thenReturn("MP-001");
        when(producto.getNombre()).thenReturn("Material de prueba");
        when(producto.getTipoUnidades()).thenReturn("KG");
        when(movimientosService.findProductsWithStockForExportByAlmacenes(
                eq(""),
                eq("NOMBRE"),
                eq(List.of(
                        Movimiento.Almacen.AVERIAS,
                        Movimiento.Almacen.CALIDAD,
                        Movimiento.Almacen.DEVOLUCIONES
                )),
                eq(LocalDateTime.of(2026, 8, 3, 10, 42))
        )).thenReturn(List.of(new ProductoStockDTO(producto, 12.5)));

        InventarioExcelRequestDTO request = new InventarioExcelRequestDTO(
                "",
                "NOMBRE",
                AlcanceInventario.RESTRINGIDO,
                List.of()
        );

        byte[] result = service.generateInventoryExcel(request);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var sheet = workbook.getSheet("Inventario");
            assertEquals("Stock restringido/no disponible", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("AVERIAS, CALIDAD, DEVOLUCIONES", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("03/08/2026 10:42:00 -05:00", sheet.getRow(2).getCell(1).getStringCellValue());
            assertEquals("Stock", sheet.getRow(4).getCell(2).getStringCellValue());
            assertEquals(12.5, sheet.getRow(5).getCell(2).getNumericCellValue());
        }

        verify(movimientosService).findProductsWithStockForExportByAlmacenes(
                "",
                "NOMBRE",
                List.of(
                        Movimiento.Almacen.AVERIAS,
                        Movimiento.Almacen.CALIDAD,
                        Movimiento.Almacen.DEVOLUCIONES
                ),
                LocalDateTime.of(2026, 8, 3, 10, 42)
        );
    }
}
