package exotic.app.planta.service.inventarios;

import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.inventarios.LoteRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MaterialLoteGenerationService {

    private static final DateTimeFormatter RECEPCION_DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");
    private static final int MAX_SEQUENCE_ATTEMPTS = 10_000;
    private static final String FALLBACK_PREFIX = "MAT";

    private final LoteRepo loteRepo;

    public String generarLoteRecepcionOcm(
            Material material,
            OrdenCompraMateriales ordenCompra,
            LocalDate fechaIngreso
    ) {
        return generarLoteRecepcionOcm(material, ordenCompra, fechaIngreso, Set.of());
    }

    public String generarLoteRecepcionOcmPreview(
            Material material,
            OrdenCompraMateriales ordenCompra,
            LocalDate fechaIngreso,
            Set<String> batchNumbersReservados
    ) {
        return generarLoteRecepcionOcm(material, ordenCompra, fechaIngreso, batchNumbersReservados);
    }

    private String generarLoteRecepcionOcm(
            Material material,
            OrdenCompraMateriales ordenCompra,
            LocalDate fechaIngreso,
            Set<String> batchNumbersReservados
    ) {
        if (material == null) {
            throw new IllegalArgumentException("El material es requerido para generar el lote de recepcion OCM.");
        }
        if (ordenCompra == null || ordenCompra.getOrdenCompraId() <= 0) {
            throw new IllegalArgumentException("La orden de compra es requerida para generar el lote de recepcion OCM.");
        }
        if (fechaIngreso == null) {
            throw new IllegalArgumentException("La fecha de ingreso es requerida para generar el lote de recepcion OCM.");
        }

        String base = buildBase(material, ordenCompra, fechaIngreso);
        int nextSequence = resolveNextSequence(base, ordenCompra.getOrdenCompraId());
        Set<String> reserved = batchNumbersReservados != null ? batchNumbersReservados : Set.of();

        for (int sequence = nextSequence; sequence < nextSequence + MAX_SEQUENCE_ATTEMPTS; sequence++) {
            String candidate = base + "-" + formatSequence(sequence);
            if (reserved.contains(candidate)) {
                continue;
            }
            if (loteRepo.findByBatchNumber(candidate) != null) {
                continue;
            }
            return candidate;
        }

        throw new IllegalStateException("No fue posible generar un lote unico de recepcion OCM para base " + base);
    }

    private String buildBase(Material material, OrdenCompraMateriales ordenCompra, LocalDate fechaIngreso) {
        return resolvePrefix(material)
                + "-"
                + fechaIngreso.format(RECEPCION_DATE_FORMAT)
                + "-"
                + String.format("%06d", ordenCompra.getOrdenCompraId());
    }

    private String resolvePrefix(Material material) {
        String prefijoLote = normalizeAlphanumeric(material.getPrefijoLote());
        if (!prefijoLote.isBlank()) {
            return prefijoLote;
        }

        String productPrefix = normalizeAlphanumeric(material.getProductoId());
        if (productPrefix.isBlank()) {
            return FALLBACK_PREFIX;
        }

        return productPrefix.substring(0, Math.min(productPrefix.length(), 6));
    }

    private int resolveNextSequence(String base, int ordenCompraId) {
        Pattern pattern = Pattern.compile("^" + Pattern.quote(base) + "-(\\d+)$");
        List<Lote> lotesOrden = loteRepo.findByOrdenCompraMateriales_OrdenCompraId(ordenCompraId);

        int maxSequence = 0;
        for (Lote lote : lotesOrden) {
            String batchNumber = lote.getBatchNumber();
            if (batchNumber == null) {
                continue;
            }

            Matcher matcher = pattern.matcher(batchNumber);
            if (!matcher.matches()) {
                continue;
            }

            int sequence = Integer.parseInt(matcher.group(1));
            if (sequence > maxSequence) {
                maxSequence = sequence;
            }
        }

        return maxSequence + 1;
    }

    private String formatSequence(int sequence) {
        return sequence < 100 ? String.format("%02d", sequence) : String.valueOf(sequence);
    }

    private String normalizeAlphanumeric(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
    }
}
