package exotic.app.planta.service.compras.proveedor.metricas;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.config.TimeZoneConfig;
import exotic.app.planta.model.bi.dto.ProveedorMaterialOrdenHistRowDTO;
import exotic.app.planta.model.bi.dto.ProveedorMaterialRecepcionRowDTO;
import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.model.compras.proveedor.metricas.EstadoLeadTimeProveedorKPI;
import exotic.app.planta.model.compras.proveedor.metricas.LeadTimeProveedorKPI;
import exotic.app.planta.repo.compras.ItemOrdenCompraRepo;
import exotic.app.planta.repo.compras.ProveedorRepo;
import exotic.app.planta.repo.compras.proveedor.metricas.LeadTimeProveedorKPIRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeadTimeProveedorKpiRefreshServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of(TimeZoneConfig.APP_TIME_ZONE_ID);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-03-31T13:00:00Z"),
            BOGOTA
    );

    private ProveedorRepo proveedorRepo;
    private ItemOrdenCompraRepo itemOrdenCompraRepo;
    private TransaccionAlmacenRepo transaccionAlmacenRepo;
    private LeadTimeProveedorKPIRepo leadTimeProveedorKPIRepo;
    private LeadTimeProveedorKpiRefreshService service;

    @BeforeEach
    void setUp() {
        AppTime.setClock(FIXED_CLOCK);
        proveedorRepo = Mockito.mock(ProveedorRepo.class);
        itemOrdenCompraRepo = Mockito.mock(ItemOrdenCompraRepo.class);
        transaccionAlmacenRepo = Mockito.mock(TransaccionAlmacenRepo.class);
        leadTimeProveedorKPIRepo = Mockito.mock(LeadTimeProveedorKPIRepo.class);
        service = new LeadTimeProveedorKpiRefreshService(
                proveedorRepo,
                itemOrdenCompraRepo,
                transaccionAlmacenRepo,
                leadTimeProveedorKPIRepo
        );
        when(leadTimeProveedorKPIRepo.save(any(LeadTimeProveedorKPI.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        AppTime.setClock(Clock.system(BOGOTA));
    }

    @Test
    void refreshAllProveedores_processesEveryProvider() {
        Proveedor proveedorUno = proveedor(1L, "PROV-1", "Proveedor Uno");
        Proveedor proveedorDos = proveedor(2L, "PROV-2", "Proveedor Dos");
        when(proveedorRepo.findAll()).thenReturn(List.of(proveedorUno, proveedorDos));
        when(itemOrdenCompraRepo.findLeadTimeOrderHistoryByProveedor(any(), any(), any())).thenReturn(List.of());
        when(transaccionAlmacenRepo.findLeadTimeReceiptHistoryByProveedor(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(leadTimeProveedorKPIRepo.findByProveedor_Pk(any())).thenReturn(Optional.empty());

        LeadTimeProveedorKpiRefreshService.LeadTimeProveedorKpiRefreshSummary summary =
                service.refreshAllProveedores();

        assertEquals(2, summary.proveedoresEvaluados());
        assertEquals(0, summary.vigentes());
        assertEquals(0, summary.desactualizados());
        assertEquals(2, summary.sinInformacion());
        assertEquals(0, summary.fallidos());
        verify(itemOrdenCompraRepo).findLeadTimeOrderHistoryByProveedor(eq("PROV-1"), any(), any());
        verify(itemOrdenCompraRepo).findLeadTimeOrderHistoryByProveedor(eq("PROV-2"), any(), any());
    }

    @Test
    void refreshAllProveedores_persistsVigenteWithMedianAcrossProviderObservations() {
        Proveedor proveedor = proveedor(1L, "PROV-1", "Proveedor Uno");
        when(proveedorRepo.findAll()).thenReturn(List.of(proveedor));
        when(leadTimeProveedorKPIRepo.findByProveedor_Pk(1L)).thenReturn(Optional.empty());
        when(itemOrdenCompraRepo.findLeadTimeOrderHistoryByProveedor(eq("PROV-1"), any(), any()))
                .thenReturn(List.of(
                        orderRow(101, "MAT-A", "2026-01-01T00:00:00", null, 10),
                        orderRow(101, "MAT-B", "2026-01-02T00:00:00", null, 5)
                ));
        when(transaccionAlmacenRepo.findLeadTimeReceiptHistoryByProveedor(eq("PROV-1"), any(), any(), any(), any()))
                .thenReturn(List.of(
                        receiptRow(101, "MAT-A", "2026-01-03T00:00:00", 10.0),
                        receiptRow(101, "MAT-B", "2026-01-06T00:00:00", 5.0)
                ));

        service.refreshAllProveedores();

        LeadTimeProveedorKPI saved = captureSavedKpi();
        assertEquals(EstadoLeadTimeProveedorKPI.VIGENTE, saved.getEstado());
        assertEquals(3.0, saved.getLeadTimeMedianoDias());
        assertEquals(2, saved.getObservaciones());
        assertEquals(1, saved.getOrdenesConsideradas());
        assertEquals(LocalDate.of(2026, 3, 31), saved.getFechaCorte());
        assertEquals(LocalDateTime.of(2026, 3, 31, 8, 0), saved.getCalculadoEn());
        assertEquals(LocalDateTime.of(2026, 3, 31, 8, 0), saved.getUltimaEvaluacionEn());
        assertNull(saved.getMotivoEstado());
    }

    @Test
    void refreshAllProveedores_prefersFechaEnvioProveedorOverFechaEmision() {
        Proveedor proveedor = proveedor(1L, "PROV-1", "Proveedor Uno");
        when(proveedorRepo.findAll()).thenReturn(List.of(proveedor));
        when(leadTimeProveedorKPIRepo.findByProveedor_Pk(1L)).thenReturn(Optional.empty());
        when(itemOrdenCompraRepo.findLeadTimeOrderHistoryByProveedor(eq("PROV-1"), any(), any()))
                .thenReturn(List.of(
                        orderRow(101, "MAT-A", "2026-01-01T00:00:00", "2026-01-04T00:00:00", 10)
                ));
        when(transaccionAlmacenRepo.findLeadTimeReceiptHistoryByProveedor(eq("PROV-1"), any(), any(), any(), any()))
                .thenReturn(List.of(
                        receiptRow(101, "MAT-A", "2026-01-07T00:00:00", 10.0)
                ));

        service.refreshAllProveedores();

        LeadTimeProveedorKPI saved = captureSavedKpi();
        assertEquals(EstadoLeadTimeProveedorKPI.VIGENTE, saved.getEstado());
        assertEquals(3.0, saved.getLeadTimeMedianoDias());
    }

    @Test
    void refreshAllProveedores_usesReceiptDateThatCompletesCumulativeQuantity() {
        Proveedor proveedor = proveedor(1L, "PROV-1", "Proveedor Uno");
        when(proveedorRepo.findAll()).thenReturn(List.of(proveedor));
        when(leadTimeProveedorKPIRepo.findByProveedor_Pk(1L)).thenReturn(Optional.empty());
        when(itemOrdenCompraRepo.findLeadTimeOrderHistoryByProveedor(eq("PROV-1"), any(), any()))
                .thenReturn(List.of(
                        orderRow(101, "MAT-A", "2026-01-01T00:00:00", null, 10)
                ));
        when(transaccionAlmacenRepo.findLeadTimeReceiptHistoryByProveedor(eq("PROV-1"), any(), any(), any(), any()))
                .thenReturn(List.of(
                        receiptRow(101, "MAT-A", "2026-01-03T00:00:00", 4.0),
                        receiptRow(101, "MAT-A", "2026-01-05T00:00:00", 6.0),
                        receiptRow(101, "MAT-A", "2026-01-10T00:00:00", 1.0)
                ));

        service.refreshAllProveedores();

        LeadTimeProveedorKPI saved = captureSavedKpi();
        assertEquals(4.0, saved.getLeadTimeMedianoDias());
    }

    @Test
    void refreshAllProveedores_marksExistingKpiAsDesactualizadoWithoutOverwritingValue() {
        Proveedor proveedor = proveedor(1L, "PROV-1", "Proveedor Uno");
        LeadTimeProveedorKPI existing = existingKpi(proveedor, 7.0);
        LocalDate oldFechaCorte = existing.getFechaCorte();
        LocalDateTime oldCalculadoEn = existing.getCalculadoEn();
        when(proveedorRepo.findAll()).thenReturn(List.of(proveedor));
        when(leadTimeProveedorKPIRepo.findByProveedor_Pk(1L)).thenReturn(Optional.of(existing));
        when(itemOrdenCompraRepo.findLeadTimeOrderHistoryByProveedor(eq("PROV-1"), any(), any()))
                .thenReturn(List.of());
        when(transaccionAlmacenRepo.findLeadTimeReceiptHistoryByProveedor(eq("PROV-1"), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.refreshAllProveedores();

        assertEquals(EstadoLeadTimeProveedorKPI.DESACTUALIZADO, existing.getEstado());
        assertEquals(7.0, existing.getLeadTimeMedianoDias());
        assertEquals(oldFechaCorte, existing.getFechaCorte());
        assertEquals(oldCalculadoEn, existing.getCalculadoEn());
        assertEquals(LocalDate.of(2026, 3, 31), existing.getUltimaFechaCorteEvaluada());
        assertEquals(LocalDateTime.of(2026, 3, 31, 8, 0), existing.getUltimaEvaluacionEn());
        verify(leadTimeProveedorKPIRepo).save(existing);
    }

    @Test
    void refreshAllProveedores_createsSinInformacionWhenNoExistingKpiAndNoValidObservations() {
        Proveedor proveedor = proveedor(1L, "PROV-1", "Proveedor Uno");
        when(proveedorRepo.findAll()).thenReturn(List.of(proveedor));
        when(leadTimeProveedorKPIRepo.findByProveedor_Pk(1L)).thenReturn(Optional.empty());
        when(itemOrdenCompraRepo.findLeadTimeOrderHistoryByProveedor(eq("PROV-1"), any(), any()))
                .thenReturn(List.of());
        when(transaccionAlmacenRepo.findLeadTimeReceiptHistoryByProveedor(eq("PROV-1"), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.refreshAllProveedores();

        LeadTimeProveedorKPI saved = captureSavedKpi();
        assertEquals(EstadoLeadTimeProveedorKPI.SIN_INFORMACION, saved.getEstado());
        assertNull(saved.getLeadTimeMedianoDias());
        assertEquals(0, saved.getObservaciones());
        assertEquals(0, saved.getOrdenesConsideradas());
        assertNull(saved.getCalculadoEn());
        assertEquals(LocalDate.of(2026, 3, 31), saved.getFechaCorte());
        assertEquals(365, saved.getVentanaDias());
    }

    @Test
    void refreshAllProveedores_excludesInvalidAndNegativeObservations() {
        Proveedor proveedor = proveedor(1L, "PROV-1", "Proveedor Uno");
        when(proveedorRepo.findAll()).thenReturn(List.of(proveedor));
        when(leadTimeProveedorKPIRepo.findByProveedor_Pk(1L)).thenReturn(Optional.empty());
        when(itemOrdenCompraRepo.findLeadTimeOrderHistoryByProveedor(eq("PROV-1"), any(), any()))
                .thenReturn(List.of(
                        orderRow(101, "MAT-A", "2026-01-01T00:00:00", null, 0),
                        orderRow(102, "MAT-A", "2026-01-10T00:00:00", null, 10),
                        orderRow(103, "MAT-A", "2026-01-20T00:00:00", null, 10)
                ));
        when(transaccionAlmacenRepo.findLeadTimeReceiptHistoryByProveedor(eq("PROV-1"), any(), any(), any(), any()))
                .thenReturn(List.of(
                        receiptRow(101, "MAT-A", "2026-01-02T00:00:00", 1.0),
                        receiptRow(102, "MAT-A", "2026-01-09T00:00:00", 10.0),
                        receiptRow(103, "MAT-A", "2026-01-23T00:00:00", 10.0)
                ));

        service.refreshAllProveedores();

        LeadTimeProveedorKPI saved = captureSavedKpi();
        assertEquals(EstadoLeadTimeProveedorKPI.VIGENTE, saved.getEstado());
        assertEquals(3.0, saved.getLeadTimeMedianoDias());
        assertEquals(1, saved.getObservaciones());
        assertEquals(3, saved.getOrdenesConsideradas());
    }

    @Test
    void refreshAllProveedores_countsProviderFailureWithoutStoppingBatch() {
        Proveedor proveedorUno = proveedor(1L, "PROV-1", "Proveedor Uno");
        Proveedor proveedorDos = proveedor(2L, "PROV-2", "Proveedor Dos");
        when(proveedorRepo.findAll()).thenReturn(List.of(proveedorUno, proveedorDos));
        when(itemOrdenCompraRepo.findLeadTimeOrderHistoryByProveedor(eq("PROV-1"), any(), any()))
                .thenThrow(new RuntimeException("fallo simulado"));
        when(itemOrdenCompraRepo.findLeadTimeOrderHistoryByProveedor(eq("PROV-2"), any(), any()))
                .thenReturn(List.of());
        when(transaccionAlmacenRepo.findLeadTimeReceiptHistoryByProveedor(eq("PROV-2"), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(leadTimeProveedorKPIRepo.findByProveedor_Pk(2L)).thenReturn(Optional.empty());

        LeadTimeProveedorKpiRefreshService.LeadTimeProveedorKpiRefreshSummary summary =
                service.refreshAllProveedores();

        assertEquals(2, summary.proveedoresEvaluados());
        assertEquals(1, summary.sinInformacion());
        assertEquals(1, summary.fallidos());
        verify(leadTimeProveedorKPIRepo, never()).findByProveedor_Pk(1L);
    }

    private LeadTimeProveedorKPI captureSavedKpi() {
        ArgumentCaptor<LeadTimeProveedorKPI> captor = ArgumentCaptor.forClass(LeadTimeProveedorKPI.class);
        verify(leadTimeProveedorKPIRepo).save(captor.capture());
        return captor.getValue();
    }

    private static Proveedor proveedor(Long pk, String id, String nombre) {
        Proveedor proveedor = new Proveedor();
        proveedor.setPk(pk);
        proveedor.setId(id);
        proveedor.setNombre(nombre);
        return proveedor;
    }

    private static LeadTimeProveedorKPI existingKpi(Proveedor proveedor, Double leadTimeMedianoDias) {
        LeadTimeProveedorKPI kpi = new LeadTimeProveedorKPI();
        kpi.setId(10L);
        kpi.setProveedor(proveedor);
        kpi.setFechaCorte(LocalDate.of(2026, 1, 31));
        kpi.setVentanaDias(365);
        kpi.setLeadTimeMedianoDias(leadTimeMedianoDias);
        kpi.setObservaciones(4);
        kpi.setOrdenesConsideradas(4);
        kpi.setCalculadoEn(LocalDateTime.of(2026, 1, 31, 18, 0));
        kpi.setEstado(EstadoLeadTimeProveedorKPI.VIGENTE);
        return kpi;
    }

    private static ProveedorMaterialOrdenHistRowDTO orderRow(
            Integer ordenCompraId,
            String materialId,
            String fechaEmision,
            String fechaEnvioProveedor,
            Integer cantidadOrdenada
    ) {
        return new ProveedorMaterialOrdenHistRowDTO(
                ordenCompraId,
                "PROV-1",
                "Proveedor Uno",
                materialId,
                materialId,
                LocalDateTime.parse(fechaEmision),
                fechaEnvioProveedor == null ? null : LocalDateTime.parse(fechaEnvioProveedor),
                cantidadOrdenada
        );
    }

    private static ProveedorMaterialRecepcionRowDTO receiptRow(
            Integer ordenCompraId,
            String materialId,
            String fechaMovimiento,
            Double cantidadRecibida
    ) {
        return new ProveedorMaterialRecepcionRowDTO(
                ordenCompraId,
                "PROV-1",
                "Proveedor Uno",
                materialId,
                materialId,
                null,
                null,
                LocalDateTime.parse(fechaMovimiento),
                cantidadRecibida
        );
    }
}
