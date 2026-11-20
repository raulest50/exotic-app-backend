package exotic.app.planta.service.produccion;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Contratos de numeracion; los mocks no acreditan la exclusion mutua de PostgreSQL. */
class LoteManufacturaNumeroServiceTest {
    private final ProductoRepo productos = mock(ProductoRepo.class);
    private final LoteRepo lotes = mock(LoteRepo.class);
    private final OrdenFabricacionRepo ordenes = mock(OrdenFabricacionRepo.class);
    private SemiTerminado producto;

    @BeforeEach
    void productoConPrefijoVigente() {
        producto = new SemiTerminado();
        producto.setProductoId("SEMI-1");
        producto.setPrefijoLote("NUE");
        when(productos.findByProductoIdForUpdate("SEMI-1")).thenReturn(Optional.of(producto));
    }

    @Test
    void renombrarElLoteNoLiberaElConsecutivoPersistidoEnLaOf() {
        when(lotes.findByProducto_ProductoId("SEMI-1"))
                .thenReturn(List.of(lote("ANT-00042-07-181126")));
        when(ordenes.maxConsecutivoLote("SEMI-1")).thenReturn(42L);

        var reserva = servicio(2026).reservar("SEMI-1");

        assertThat(reserva.prefijo()).isEqualTo("NUE");
        assertThat(reserva.consecutivo()).isEqualTo(43L);
        assertThat(reserva.codigoProvisional()).isEqualTo("NUE-0000043-26");
    }

    @Test
    void elMayorConsecutivoLegadoSobreviveAlCambioDePrefijoYDeAnio() {
        when(lotes.findByProducto_ProductoId("SEMI-1")).thenReturn(List.of(
                lote("ANT-0000018-24"), lote("NUE-0000003-26"), lote("REFERENCIA-MANUAL")));
        when(ordenes.maxConsecutivoLote("SEMI-1")).thenReturn(12L);

        assertThat(servicio(2026).reservar("SEMI-1").codigoProvisional())
                .isEqualTo("NUE-0000019-26");
    }

    @Test
    void unLoteManualConFormatoDefinitivoConsumeElConsecutivoAunqueNoTengaReservaOf() {
        when(lotes.findByProducto_ProductoId("SEMI-1"))
                .thenReturn(List.of(lote("ANT-00042-07-181126")));
        when(ordenes.maxConsecutivoLote("SEMI-1")).thenReturn(null);

        var reserva = servicio(2026).reservar("SEMI-1");

        assertThat(reserva.consecutivo()).isEqualTo(43L);
        assertThat(reserva.codigoProvisional()).isEqualTo("NUE-0000043-26");
    }

    @ParameterizedTest
    @ValueSource(ints = {2026, 2027})
    void cambiarDeAnioNoReiniciaUnaReservaDurable(int anio) {
        when(lotes.findByProducto_ProductoId("SEMI-1")).thenReturn(List.of());
        when(ordenes.maxConsecutivoLote("SEMI-1")).thenReturn(72L);

        var reserva = servicio(anio).reservar("SEMI-1");

        assertThat(reserva.consecutivo()).isEqualTo(73L);
        assertThat(reserva.codigoProvisional()).isEqualTo("NUE-0000073-" + (anio % 100));
    }

    @Test
    void noDesbordaLasCincoPosicionesDelFormatoDefinitivo() {
        when(lotes.findByProducto_ProductoId("SEMI-1")).thenReturn(List.of());
        when(ordenes.maxConsecutivoLote("SEMI-1")).thenReturn(99999L);

        assertThatIllegalStateException().isThrownBy(() -> servicio(2026).reservar("SEMI-1"))
                .withMessageContaining("cinco posiciones");
    }

    private LoteManufacturaNumeroService servicio(int anio) {
        Clock clock = Clock.fixed(Instant.parse(anio + "-11-19T15:00:00Z"), ZoneOffset.UTC);
        return new LoteManufacturaNumeroService(productos, lotes, clock, ordenes);
    }

    private Lote lote(String numero) {
        Lote lote = new Lote();
        lote.setBatchNumber(numero);
        return lote;
    }
}
