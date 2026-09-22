package exotic.app.planta.service.compras;

import exotic.app.planta.repo.compras.OrdenCompraRepo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OcmCierreBatchServiceTest {
    @Test
    void soloProcesaIdsConfirmadosYContinuaSiUnaOrdenFalla() {
        var repo = mock(OrdenCompraRepo.class);
        var cierre = mock(OcmCierreService.class);
        var service = new OcmCierreBatchService(repo, mock(RecepcionCompletaOcmService.class),
                mock(OcmCierreConfigService.class), cierre);
        when(cierre.cerrarCompletaManualmente(1, "master")).thenReturn(null);
        when(cierre.cerrarCompletaManualmente(2, "master")).thenReturn("Ya cerrada");
        when(cierre.cerrarCompletaManualmente(3, "master")).thenThrow(new IllegalStateException("Fallo"));
        when(cierre.cerrarCompletaManualmente(4, "master")).thenReturn(null);

        var result = service.cerrarSeleccionadas(List.of(1, 2, 3, 4, 1), "master");
        assertEquals(List.of(1, 4), result.cerradas());
        assertEquals(2, result.omitidas().get(0).ordenCompraId());
        assertEquals(3, result.fallidas().get(0).ordenCompraId());
        verify(cierre, times(1)).cerrarCompletaManualmente(1, "master");
        verifyNoInteractions(repo); // No second query can add unseen orders to the confirmation.
    }
}
