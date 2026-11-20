package exotic.app.planta.service.produccion;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LoteManufacturaNumeroService {

    private final ProductoRepo productoRepo;
    private final LoteRepo loteRepo;
    private final Clock applicationClock;
    private final exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionRepo ordenFabricacionRepo;

    public record Reserva(String prefijo, long consecutivo, String codigoProvisional) {}

    /** Bloquea el producto para serializar la asignacion del siguiente consecutivo. */
    @Transactional
    public String siguiente(String productoId) {
        return reservar(productoId).codigoProvisional();
    }

    /** El llamador persiste esta reserva en la OF dentro de la misma transaccion y bloqueo. */
    @Transactional
    public Reserva reservar(String productoId) {
        Producto producto = productoRepo.findByProductoIdForUpdate(productoId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + productoId));
        String prefijo = producto.getPrefijoLote() == null
                ? null : producto.getPrefijoLote().trim().toUpperCase(Locale.ROOT);
        if (prefijo == null || prefijo.isBlank()) {
            throw new IllegalStateException(
                    "El producto " + productoId + " no tiene prefijo de lote configurado.");
        }
        int year2 = Year.now(applicationClock).getValue() % 100;
        Pattern pattern = Pattern.compile("^.+-(\\d+)-(?:\\d{2}|\\d{2}-\\d{6})$");
        List<Lote> lotes = loteRepo.findByProducto_ProductoId(productoId);
        Long reservado = ordenFabricacionRepo.maxConsecutivoLote(productoId);
        long max = reservado == null ? 0 : reservado;
        for (Lote lote : lotes) {
            if (lote.getBatchNumber() == null) continue;
            Matcher matcher = pattern.matcher(lote.getBatchNumber());
            if (matcher.matches()) {
                try {
                    max = Math.max(max, Long.parseLong(matcher.group(1)));
                } catch (NumberFormatException exception) {
                    throw new IllegalStateException("El consecutivo legado excede el rango de numeracion OF.");
                }
            }
        }
        if (max >= 99999) throw new IllegalStateException("Se agotaron las cinco posiciones del consecutivo OF para " + productoId + ".");
        return new Reserva(prefijo, max + 1, prefijo + "-" + String.format(Locale.ROOT, "%07d", max + 1)
                + "-" + String.format(Locale.ROOT, "%02d", year2));
    }
}
