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

    /** Bloquea el producto para serializar la asignacion del siguiente consecutivo. */
    @Transactional
    public String siguiente(String productoId) {
        Producto producto = productoRepo.findByProductoIdForUpdate(productoId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + productoId));
        String prefijo = producto.getPrefijoLote() == null
                ? null : producto.getPrefijoLote().trim().toUpperCase(Locale.ROOT);
        if (prefijo == null || prefijo.isBlank()) {
            throw new IllegalStateException(
                    "El producto " + productoId + " no tiene prefijo de lote configurado.");
        }
        int year2 = Year.now(applicationClock).getValue() % 100;
        Pattern pattern = Pattern.compile("^" + Pattern.quote(prefijo) + "-(\\d+)-(\\d{2})$");
        List<Lote> lotes = loteRepo.findByProducto_ProductoId(productoId);
        int max = 0;
        for (Lote lote : lotes) {
            if (lote.getBatchNumber() == null) continue;
            Matcher matcher = pattern.matcher(lote.getBatchNumber());
            if (matcher.matches() && Integer.parseInt(matcher.group(2)) == year2) {
                max = Math.max(max, Integer.parseInt(matcher.group(1)));
            }
        }
        return prefijo + "-" + String.format("%07d", max + 1)
                + "-" + String.format("%02d", year2);
    }
}
