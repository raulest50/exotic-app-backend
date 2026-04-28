package exotic.app.planta.service.bi;

import exotic.app.planta.model.bi.dto.LeadTimeProveedorMaterialDTO;
import exotic.app.planta.model.bi.dto.LeadTimeProveedorMaterialPageRowDTO;
import exotic.app.planta.model.bi.dto.ProveedorMaterialOrdenHistRowDTO;
import exotic.app.planta.model.bi.dto.ProveedorMaterialRecepcionRowDTO;
import exotic.app.planta.model.bi.dto.PuntoReordenEstimadoDTO;
import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.compras.ItemOrdenCompraRepo;
import exotic.app.planta.repo.compras.ProveedorRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.MaterialRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

class ProveedoresBiServiceTest {

    private ProveedorRepo proveedorRepo;
    private MaterialRepo materialRepo;
    private ItemOrdenCompraRepo itemOrdenCompraRepo;
    private TransaccionAlmacenRepo transaccionAlmacenRepo;
    private ProveedoresBiService service;

    @BeforeEach
    void setUp() {
        proveedorRepo = Mockito.mock(ProveedorRepo.class);
        materialRepo = Mockito.mock(MaterialRepo.class);
        itemOrdenCompraRepo = Mockito.mock(ItemOrdenCompraRepo.class);
        transaccionAlmacenRepo = Mockito.mock(TransaccionAlmacenRepo.class);
        service = new ProveedoresBiService(proveedorRepo, materialRepo, itemOrdenCompraRepo, transaccionAlmacenRepo);
    }

    @Test
    void calcularLeadTimeProveedorMaterial_withoutReceipts_returnsNotCalculableStats() {
        Proveedor proveedor = proveedor("PROV-1", "Proveedor Uno");
        Material material = material("MAT-1", "Material Uno", true);
        when(proveedorRepo.findById("PROV-1")).thenReturn(Optional.of(proveedor));
        when(materialRepo.findById("MAT-1")).thenReturn(Optional.of(material));
        when(itemOrdenCompraRepo.findLeadTimeOrderHistory(eq("MAT-1"), eq("PROV-1"), any(), any()))
                .thenReturn(List.of(
                        orderRow(101, "PROV-1", "Proveedor Uno", "MAT-1", "Material Uno", "2026-01-01T00:00:00", 10)
                ));
        when(transaccionAlmacenRepo.findLeadTimeReceiptHistory(eq("MAT-1"), eq("PROV-1"), any(), any(), any(), any()))
                .thenReturn(List.of());

        LeadTimeProveedorMaterialDTO result = service.calcularLeadTimeProveedorMaterial(
                "PROV-1",
                "MAT-1",
                LocalDate.of(2026, 3, 31),
                365
        );

        assertEquals(1, result.getTotalOrdersConsidered());
        assertFalse(result.getFirstReceipt().isCalculable());
        assertEquals("No se registran movimientos COMPRA del material relacionados con la consulta.",
                result.getFirstReceipt().getReason());
        assertFalse(result.getCompleteReceipt().isCalculable());
        assertNull(result.getCompleteReceipt().getRepresentativeLeadTimeDays());
    }

    @Test
    void calcularLeadTimeProveedorMaterial_withThreeOrders_usesMedianForRepresentativeLeadTime() {
        Proveedor proveedor = proveedor("PROV-1", "Proveedor Uno");
        Material material = material("MAT-1", "Material Uno", true);
        when(proveedorRepo.findById("PROV-1")).thenReturn(Optional.of(proveedor));
        when(materialRepo.findById("MAT-1")).thenReturn(Optional.of(material));
        when(itemOrdenCompraRepo.findLeadTimeOrderHistory(eq("MAT-1"), eq("PROV-1"), any(), any()))
                .thenReturn(List.of(
                        orderRow(101, "PROV-1", "Proveedor Uno", "MAT-1", "Material Uno", "2026-01-01T00:00:00", 10),
                        orderRow(102, "PROV-1", "Proveedor Uno", "MAT-1", "Material Uno", "2026-01-10T00:00:00", 10),
                        orderRow(103, "PROV-1", "Proveedor Uno", "MAT-1", "Material Uno", "2026-01-20T00:00:00", 10)
                ));
        when(transaccionAlmacenRepo.findLeadTimeReceiptHistory(eq("MAT-1"), eq("PROV-1"), any(), any(), any(), any()))
                .thenReturn(List.of(
                        receiptRow(101, "PROV-1", "Proveedor Uno", "MAT-1", "Material Uno", "2026-01-03T00:00:00", 10.0),
                        receiptRow(102, "PROV-1", "Proveedor Uno", "MAT-1", "Material Uno", "2026-01-13T00:00:00", 4.0),
                        receiptRow(102, "PROV-1", "Proveedor Uno", "MAT-1", "Material Uno", "2026-01-15T00:00:00", 6.0),
                        receiptRow(103, "PROV-1", "Proveedor Uno", "MAT-1", "Material Uno", "2026-01-23T00:00:00", 10.0)
                ));

        LeadTimeProveedorMaterialDTO result = service.calcularLeadTimeProveedorMaterial(
                "PROV-1",
                "MAT-1",
                LocalDate.of(2026, 3, 31),
                365
        );

        assertTrue(result.getFirstReceipt().isCalculable());
        assertEquals(3, result.getFirstReceipt().getValidObservations());
        assertEquals(3.0, result.getFirstReceipt().getRepresentativeLeadTimeDays());
        assertEquals(2.6667, result.getFirstReceipt().getAverageLeadTimeDays());
        assertEquals(3.0, result.getCompleteReceipt().getRepresentativeLeadTimeDays());
        assertEquals(3, result.getCompleteReceipt().getValidObservations());
        assertNotNull(result.getFirstReceipt().getConfidenceScore());
    }

    @Test
    void listarLeadTimesPorMaterial_penalizesLowConfidenceProvidersInAscendingRanking() {
        Material material = material("MAT-1", "Material Uno", true);
        when(materialRepo.findById("MAT-1")).thenReturn(Optional.of(material));
        when(itemOrdenCompraRepo.findLeadTimeOrderHistory(eq("MAT-1"), isNull(), any(), any()))
                .thenReturn(List.of(
                        orderRow(101, "PROV-A", "Proveedor Alfa", "MAT-1", "Material Uno", "2026-01-01T00:00:00", 10),
                        orderRow(102, "PROV-A", "Proveedor Alfa", "MAT-1", "Material Uno", "2026-01-05T00:00:00", 10),
                        orderRow(103, "PROV-A", "Proveedor Alfa", "MAT-1", "Material Uno", "2026-01-09T00:00:00", 10),
                        orderRow(201, "PROV-B", "Proveedor Beta", "MAT-1", "Material Uno", "2026-01-03T00:00:00", 10)
                ));
        when(transaccionAlmacenRepo.findLeadTimeReceiptHistory(eq("MAT-1"), isNull(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        receiptRow(101, "PROV-A", "Proveedor Alfa", "MAT-1", "Material Uno", "2026-01-03T00:00:00", 10.0),
                        receiptRow(102, "PROV-A", "Proveedor Alfa", "MAT-1", "Material Uno", "2026-01-07T00:00:00", 10.0),
                        receiptRow(103, "PROV-A", "Proveedor Alfa", "MAT-1", "Material Uno", "2026-01-11T00:00:00", 10.0),
                        receiptRow(201, "PROV-B", "Proveedor Beta", "MAT-1", "Material Uno", "2026-01-05T00:00:00", 10.0)
                ));

        Page<LeadTimeProveedorMaterialPageRowDTO> result = service.listarLeadTimesPorMaterial(
                "MAT-1",
                LocalDate.of(2026, 3, 31),
                365,
                0,
                10,
                "asc"
        );

        assertEquals(2, result.getTotalElements());
        assertEquals("PROV-A", result.getContent().get(0).getProveedorId());
        assertEquals("PROV-B", result.getContent().get(1).getProveedorId());
        assertTrue(result.getContent().get(0).getAdjustedLeadTimeDays() <= result.getContent().get(1).getAdjustedLeadTimeDays());
    }

    @Test
    void estimarPuntoReorden_nonInventareable_returnsNoData() {
        Material material = material("MAT-2", "Agua de proceso", false);
        when(materialRepo.findById("MAT-2")).thenReturn(Optional.of(material));

        PuntoReordenEstimadoDTO result = service.estimarPuntoReorden(
                "MAT-2",
                LocalDate.of(2026, 3, 31),
                365
        );

        assertEquals("NO_DATA", result.getMetodoUsado());
        assertEquals("El material no es inventareable.", result.getReason());
        assertNull(result.getPuntoReordenEstimado());
    }

    @Test
    void estimarPuntoReorden_withDemandAndLeadTime_returnsFullStatisticalEstimate() {
        Material material = material("MAT-1", "Material Uno", true);
        when(materialRepo.findById("MAT-1")).thenReturn(Optional.of(material));
        when(itemOrdenCompraRepo.findLeadTimeOrderHistory(eq("MAT-1"), isNull(), any(), any()))
                .thenReturn(List.of(
                        orderRow(101, "PROV-A", "Proveedor Alfa", "MAT-1", "Material Uno", "2025-10-01T00:00:00", 10),
                        orderRow(102, "PROV-A", "Proveedor Alfa", "MAT-1", "Material Uno", "2025-10-10T00:00:00", 10),
                        orderRow(201, "PROV-B", "Proveedor Beta", "MAT-1", "Material Uno", "2025-10-20T00:00:00", 10),
                        orderRow(202, "PROV-B", "Proveedor Beta", "MAT-1", "Material Uno", "2025-11-01T00:00:00", 10)
                ));
        when(transaccionAlmacenRepo.findLeadTimeReceiptHistory(eq("MAT-1"), isNull(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        receiptRow(101, "PROV-A", "Proveedor Alfa", "MAT-1", "Material Uno", "2025-10-03T00:00:00", 10.0),
                        receiptRow(102, "PROV-A", "Proveedor Alfa", "MAT-1", "Material Uno", "2025-10-14T00:00:00", 10.0),
                        receiptRow(201, "PROV-B", "Proveedor Beta", "MAT-1", "Material Uno", "2025-10-23T00:00:00", 10.0),
                        receiptRow(202, "PROV-B", "Proveedor Beta", "MAT-1", "Material Uno", "2025-11-06T00:00:00", 10.0)
                ));
        when(transaccionAlmacenRepo.findByProducto_ProductoIdAndFechaMovimientoBetweenOrderByFechaMovimientoAscMovimientoIdAsc(
                eq("MAT-1"), any(), any()))
                .thenReturn(demandHistory("MAT-1"));

        PuntoReordenEstimadoDTO result = service.estimarPuntoReorden(
                "MAT-1",
                LocalDate.of(2026, 1, 31),
                120
        );

        assertEquals("FULL_STATISTICAL", result.getMetodoUsado());
        assertNotNull(result.getPuntoReordenEstimado());
        assertTrue(result.getPuntoReordenEstimado() > 0.0);
        assertEquals(4, result.getObservacionesLeadTime());
        assertEquals(2, result.getProveedoresObservados());
        assertNotNull(result.getConfianzaGlobal());
    }

    private static Proveedor proveedor(String id, String nombre) {
        Proveedor proveedor = new Proveedor();
        proveedor.setId(id);
        proveedor.setNombre(nombre);
        return proveedor;
    }

    private static Material material(String id, String nombre, boolean inventareable) {
        Material material = new Material();
        material.setProductoId(id);
        material.setNombre(nombre);
        material.setInventareable(inventareable);
        return material;
    }

    private static ProveedorMaterialOrdenHistRowDTO orderRow(
            int ordenCompraId,
            String proveedorId,
            String proveedorNombre,
            String materialId,
            String materialNombre,
            String fechaEmision,
            int cantidad
    ) {
        return new ProveedorMaterialOrdenHistRowDTO(
                ordenCompraId,
                proveedorId,
                proveedorNombre,
                materialId,
                materialNombre,
                LocalDateTime.parse(fechaEmision),
                cantidad
        );
    }

    private static ProveedorMaterialRecepcionRowDTO receiptRow(
            int ordenCompraId,
            String proveedorId,
            String proveedorNombre,
            String materialId,
            String materialNombre,
            String fechaMovimiento,
            double cantidadRecibida
    ) {
        return new ProveedorMaterialRecepcionRowDTO(
                ordenCompraId,
                proveedorId,
                proveedorNombre,
                materialId,
                materialNombre,
                null,
                LocalDateTime.parse(fechaMovimiento),
                cantidadRecibida
        );
    }

    private static List<Movimiento> demandHistory(String materialId) {
        List<Movimiento> movimientos = new ArrayList<>();
        movimientos.add(consumptionMovement(materialId, "2025-10-05T08:00:00", Movimiento.TipoMovimiento.DISPENSACION, -10.0));
        movimientos.add(consumptionMovement(materialId, "2025-10-06T08:00:00", Movimiento.TipoMovimiento.CONSUMO, -8.0));
        movimientos.add(consumptionMovement(materialId, "2025-10-08T08:00:00", Movimiento.TipoMovimiento.DISPENSACION, -12.0));
        movimientos.add(consumptionMovement(materialId, "2025-10-12T08:00:00", Movimiento.TipoMovimiento.CONSUMO, -9.0));
        movimientos.add(consumptionMovement(materialId, "2025-10-20T08:00:00", Movimiento.TipoMovimiento.DISPENSACION, -11.0));
        movimientos.add(consumptionMovement(materialId, "2025-11-02T08:00:00", Movimiento.TipoMovimiento.CONSUMO, -7.0));
        movimientos.add(consumptionMovement(materialId, "2025-11-18T08:00:00", Movimiento.TipoMovimiento.DISPENSACION, -13.0));
        movimientos.add(consumptionMovement(materialId, "2025-12-03T08:00:00", Movimiento.TipoMovimiento.CONSUMO, -9.0));
        movimientos.add(consumptionMovement(materialId, "2025-12-20T08:00:00", Movimiento.TipoMovimiento.DISPENSACION, -10.0));
        movimientos.add(consumptionMovement(materialId, "2026-01-10T08:00:00", Movimiento.TipoMovimiento.CONSUMO, -8.0));
        return movimientos;
    }

    private static Movimiento consumptionMovement(
            String materialId,
            String timestamp,
            Movimiento.TipoMovimiento tipoMovimiento,
            double cantidad
    ) {
        Material material = new Material();
        material.setProductoId(materialId);

        Movimiento movimiento = new Movimiento();
        movimiento.setProducto(material);
        movimiento.setTipoMovimiento(tipoMovimiento);
        movimiento.setCantidad(cantidad);
        movimiento.setFechaMovimiento(LocalDateTime.parse(timestamp));
        return movimiento;
    }
}
