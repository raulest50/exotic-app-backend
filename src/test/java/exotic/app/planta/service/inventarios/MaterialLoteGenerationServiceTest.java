package exotic.app.planta.service.inventarios;

import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.inventarios.LoteRepo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaterialLoteGenerationServiceTest {

    private final LoteRepo loteRepo = mock(LoteRepo.class);
    private final MaterialLoteGenerationService service = new MaterialLoteGenerationService(loteRepo);

    @Test
    void generarLoteRecepcionOcm_usesMaterialPrefixDateOrderAndSequence() {
        Material material = material("MP001", "car");
        OrdenCompraMateriales orden = orden(1842);
        when(loteRepo.findByOrdenCompraMateriales_OrdenCompraId(1842)).thenReturn(List.of());
        when(loteRepo.findByBatchNumber("CAR-260525-001842-01")).thenReturn(null);

        String batchNumber = service.generarLoteRecepcionOcm(material, orden, LocalDate.of(2026, 5, 25));

        assertEquals("CAR-260525-001842-01", batchNumber);
    }

    @Test
    void generarLoteRecepcionOcm_usesDeterministicProductIdFallbackWhenPrefixIsMissing() {
        Material material = material("MP-TA-001", null);
        OrdenCompraMateriales orden = orden(1842);
        when(loteRepo.findByOrdenCompraMateriales_OrdenCompraId(1842)).thenReturn(List.of());
        when(loteRepo.findByBatchNumber("MPTA00-260525-001842-01")).thenReturn(null);

        String batchNumber = service.generarLoteRecepcionOcm(material, orden, LocalDate.of(2026, 5, 25));

        assertEquals("MPTA00-260525-001842-01", batchNumber);
    }

    @Test
    void generarLoteRecepcionOcm_incrementsSequenceFromExistingOrderLots() {
        Material material = material("MP001", "CAR");
        OrdenCompraMateriales orden = orden(1842);
        when(loteRepo.findByOrdenCompraMateriales_OrdenCompraId(1842)).thenReturn(List.of(
                lote("CAR-260525-001842-01"),
                lote("CAR-260525-001842-02")
        ));
        when(loteRepo.findByBatchNumber("CAR-260525-001842-03")).thenReturn(null);

        String batchNumber = service.generarLoteRecepcionOcm(material, orden, LocalDate.of(2026, 5, 25));

        assertEquals("CAR-260525-001842-03", batchNumber);
    }

    @Test
    void generarLoteRecepcionOcm_skipsCollidingBatchNumber() {
        Material material = material("MP001", "CAR");
        OrdenCompraMateriales orden = orden(1842);
        when(loteRepo.findByOrdenCompraMateriales_OrdenCompraId(1842)).thenReturn(List.of());
        when(loteRepo.findByBatchNumber("CAR-260525-001842-01")).thenReturn(lote("CAR-260525-001842-01"));
        when(loteRepo.findByBatchNumber("CAR-260525-001842-02")).thenReturn(null);

        String batchNumber = service.generarLoteRecepcionOcm(material, orden, LocalDate.of(2026, 5, 25));

        assertEquals("CAR-260525-001842-02", batchNumber);
    }

    @Test
    void generarLoteRecepcionOcmPreview_skipsReservedBatchNumbers() {
        Material material = material("MP001", "CAR");
        OrdenCompraMateriales orden = orden(1842);
        Set<String> reserved = new HashSet<>();
        reserved.add("CAR-260525-001842-01");
        when(loteRepo.findByOrdenCompraMateriales_OrdenCompraId(1842)).thenReturn(List.of());
        when(loteRepo.findByBatchNumber("CAR-260525-001842-02")).thenReturn(null);

        String batchNumber = service.generarLoteRecepcionOcmPreview(
                material,
                orden,
                LocalDate.of(2026, 5, 25),
                reserved
        );

        assertEquals("CAR-260525-001842-02", batchNumber);
    }

    @Test
    void generarLoteRecepcionOcm_doesNotTruncateLongOrderIds() {
        Material material = material("MP001", "CAR");
        OrdenCompraMateriales orden = orden(1234567);
        when(loteRepo.findByOrdenCompraMateriales_OrdenCompraId(1234567)).thenReturn(List.of());
        when(loteRepo.findByBatchNumber("CAR-260525-1234567-01")).thenReturn(null);

        String batchNumber = service.generarLoteRecepcionOcm(material, orden, LocalDate.of(2026, 5, 25));

        assertEquals("CAR-260525-1234567-01", batchNumber);
    }

    private static Material material(String productoId, String prefijoLote) {
        Material material = new Material();
        material.setProductoId(productoId);
        material.setPrefijoLote(prefijoLote);
        return material;
    }

    private static OrdenCompraMateriales orden(int ordenCompraId) {
        OrdenCompraMateriales orden = new OrdenCompraMateriales();
        orden.setOrdenCompraId(ordenCompraId);
        return orden;
    }

    private static Lote lote(String batchNumber) {
        Lote lote = new Lote();
        lote.setBatchNumber(batchNumber);
        return lote;
    }
}
