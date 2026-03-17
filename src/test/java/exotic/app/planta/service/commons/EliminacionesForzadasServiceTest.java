package exotic.app.planta.service.commons;

import exotic.app.planta.model.commons.dto.eliminaciones.EliminacionTerminadosBatchResultDTO;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.repo.activos.fijos.gestion.DepreciacionActivoRepo;
import exotic.app.planta.repo.activos.fijos.gestion.DocumentoBajaActivoRepo;
import exotic.app.planta.repo.activos.fijos.gestion.IncorporacionActivoHeaderRepo;
import exotic.app.planta.repo.compras.ItemOrdenCompraRepo;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import exotic.app.planta.repo.contabilidad.AsientoContableRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.producto.InsumoRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.repo.producto.manufacturing.snapshots.ManufacturingVersionRepo;
import exotic.app.planta.repo.producto.procesos.NodeConnectionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionCompletoRepo;
import exotic.app.planta.repo.ventas.FacturaVentaRepo;
import exotic.app.planta.repo.ventas.ItemFacturaVentaRepo;
import exotic.app.planta.repo.ventas.ItemOrdenVentaRepo;
import exotic.app.planta.repo.ventas.OrdenVentaRepo;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EliminacionesForzadasServiceTest {

    @Test
    void ejecutarEliminacionTodosLosTerminados_blocksInProduction() {
        EliminacionesForzadasService service = buildService();

        doThrow(new IllegalStateException("Operación bloqueada"))
                .when(dangerousOperationGuard)
                .assertNotProduction("La purga total de terminados");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                service::ejecutarEliminacionTodosLosTerminados
        );

        assertEquals("Operación bloqueada", exception.getMessage());
        verifyNoInteractions(terminadoRepo);
    }

    @Test
    void ejecutarEliminacionTodosLosTerminados_returnsPartialSummary() {
        EliminacionesForzadasService service = buildService();

        doNothing().when(dangerousOperationGuard).assertNotProduction(anyString());
        when(terminadoRepo.findAllProductoIdsOrderByProductoIdAsc()).thenReturn(List.of("T-001", "T-002"));

        Terminado terminado = new Terminado();
        terminado.setProductoId("T-001");

        when(productoRepo.findById("T-001")).thenReturn(Optional.of(terminado));
        when(terminadoRepo.findById("T-001")).thenReturn(Optional.of(terminado));
        when(insumoRepo.findByProducto_ProductoId("T-001")).thenReturn(List.of());
        when(ordenProduccionRepo.findByProducto_ProductoId("T-001")).thenReturn(List.of());
        when(transaccionAlmacenHeaderRepo.findDistinctByProductoIdWithMovimientos("T-001")).thenReturn(List.of());
        when(manufacturingVersionRepo.findByProducto_ProductoId("T-001")).thenReturn(List.of());
        when(itemFacturaVentaRepo.findByProducto_ProductoId("T-001")).thenReturn(List.of());
        when(itemOrdenVentaRepo.findByProducto_ProductoId("T-001")).thenReturn(List.of());

        when(productoRepo.findById("T-002")).thenReturn(Optional.empty());

        EliminacionTerminadosBatchResultDTO result = service.ejecutarEliminacionTodosLosTerminados();

        assertEquals(true, result.isPermitted());
        assertEquals(true, result.isExecuted());
        assertEquals(2, result.getTotalTerminados());
        assertEquals(1, result.getEliminados());
        assertEquals(1, result.getFallidos());
        assertEquals(1, result.getFailures().size());
        assertEquals("T-002", result.getFailures().get(0).getProductoId());

        verify(terminadoRepo).deleteById("T-001");
    }

    private final OrdenCompraRepo ordenCompraRepo = mock(OrdenCompraRepo.class);
    private final ItemOrdenCompraRepo itemOrdenCompraRepo = mock(ItemOrdenCompraRepo.class);
    private final LoteRepo loteRepo = mock(LoteRepo.class);
    private final TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo = mock(TransaccionAlmacenHeaderRepo.class);
    private final OrdenProduccionRepo ordenProduccionRepo = mock(OrdenProduccionRepo.class);
    private final ProductoRepo productoRepo = mock(ProductoRepo.class);
    private final TerminadoRepo terminadoRepo = mock(TerminadoRepo.class);
    private final InsumoRepo insumoRepo = mock(InsumoRepo.class);
    private final TransaccionAlmacenRepo transaccionAlmacenRepo = mock(TransaccionAlmacenRepo.class);
    private final ManufacturingVersionRepo manufacturingVersionRepo = mock(ManufacturingVersionRepo.class);
    private final ItemOrdenVentaRepo itemOrdenVentaRepo = mock(ItemOrdenVentaRepo.class);
    private final ItemFacturaVentaRepo itemFacturaVentaRepo = mock(ItemFacturaVentaRepo.class);
    private final OrdenVentaRepo ordenVentaRepo = mock(OrdenVentaRepo.class);
    private final FacturaVentaRepo facturaVentaRepo = mock(FacturaVentaRepo.class);
    private final ProcesoProduccionCompletoRepo procesoProduccionCompletoRepo = mock(ProcesoProduccionCompletoRepo.class);
    private final NodeConnectionRepo nodeConnectionRepo = mock(NodeConnectionRepo.class);
    private final AsientoContableRepo asientoContableRepo = mock(AsientoContableRepo.class);
    private final IncorporacionActivoHeaderRepo incorporacionActivoHeaderRepo = mock(IncorporacionActivoHeaderRepo.class);
    private final DepreciacionActivoRepo depreciacionActivoRepo = mock(DepreciacionActivoRepo.class);
    private final DocumentoBajaActivoRepo documentoBajaActivoRepo = mock(DocumentoBajaActivoRepo.class);
    private final DangerousOperationGuard dangerousOperationGuard = mock(DangerousOperationGuard.class);
    private final EntityManager entityManager = mock(EntityManager.class);

    private EliminacionesForzadasService buildService() {
        PlatformTransactionManager transactionManager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        return new EliminacionesForzadasService(
                ordenCompraRepo,
                itemOrdenCompraRepo,
                loteRepo,
                transaccionAlmacenHeaderRepo,
                ordenProduccionRepo,
                productoRepo,
                terminadoRepo,
                insumoRepo,
                transaccionAlmacenRepo,
                manufacturingVersionRepo,
                itemOrdenVentaRepo,
                itemFacturaVentaRepo,
                ordenVentaRepo,
                facturaVentaRepo,
                procesoProduccionCompletoRepo,
                nodeConnectionRepo,
                asientoContableRepo,
                incorporacionActivoHeaderRepo,
                depreciacionActivoRepo,
                documentoBajaActivoRepo,
                dangerousOperationGuard,
                transactionTemplate,
                entityManager
        );
    }
}
