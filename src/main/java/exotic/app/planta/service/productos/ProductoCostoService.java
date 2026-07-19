package exotic.app.planta.service.productos;

import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.costos.CargaCostosLote;
import exotic.app.planta.model.producto.costos.ProductoCostoHistorial;
import exotic.app.planta.model.producto.costos.ProductoCostoOrigen;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.costos.ProductoCostoHistorialRepo;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductoCostoService {
    public static final int COST_SCALE = 6;
    private static final BigDecimal MAX_COST = new BigDecimal("9999999999999.999999");

    private final ProductoRepo productoRepo;
    private final ProductoCostoHistorialRepo historialRepo;
    private final EntityManager entityManager;
    private final Clock applicationClock;

    @Transactional
    public ResultadoCambio actualizarCosto(String productoId, BigDecimal nuevoCosto, ContextoCambio contexto) {
        Producto producto = productoRepo.findByProductoIdForUpdate(productoId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + productoId));
        return actualizarProductoBloqueado(
                producto,
                producto.getCostoVersion(),
                producto.getCosto(),
                nuevoCosto,
                contexto);
    }

    @Transactional
    public ResultadoCambio actualizarProductoBloqueado(
            Producto producto,
            long versionEsperada,
            BigDecimal costoEsperado,
            BigDecimal nuevoCosto,
            ContextoCambio contexto
    ) {
        if (producto == null || producto.getProductoId() == null) {
            throw new IllegalArgumentException("El producto persistido es obligatorio");
        }

        BigDecimal anterior = normalizar(producto.getCosto());
        BigDecimal esperado = normalizar(costoEsperado);
        BigDecimal nuevo = normalizar(nuevoCosto);
        if (producto.getCostoVersion() != versionEsperada || anterior.compareTo(esperado) != 0) {
            throw new CostoVersionConflictException(producto.getProductoId());
        }
        if (anterior.compareTo(nuevo) == 0) {
            return new ResultadoCambio(producto, anterior, nuevo, false, versionEsperada);
        }

        long nuevaVersion = Math.addExact(versionEsperada, 1L);
        int updated = productoRepo.actualizarCostoSiVersion(
                producto.getProductoId(), nuevo, versionEsperada, nuevaVersion);
        if (updated != 1) {
            throw new CostoVersionConflictException(producto.getProductoId());
        }

        guardarHistorial(producto, nuevaVersion, anterior, nuevo, contexto);
        entityManager.refresh(producto);
        return new ResultadoCambio(producto, anterior, nuevo, true, nuevaVersion);
    }

    @Transactional
    public ResultadoCambio registrarCostoInicial(Producto producto, ContextoCambio contexto) {
        if (producto == null || producto.getProductoId() == null) {
            throw new IllegalArgumentException("El producto persistido es obligatorio");
        }
        if (producto.getCostoVersion() != 0L) {
            throw new IllegalStateException("El producto ya tiene un costo inicial versionado: " + producto.getProductoId());
        }

        BigDecimal costo = normalizar(producto.getCosto());
        producto.asignarCostoInicial(costo);
        productoRepo.flush();
        long nuevaVersion = Math.addExact(historialRepo.findUltimaVersion(producto.getProductoId()), 1L);
        int updated = productoRepo.actualizarCostoSiVersion(producto.getProductoId(), costo, 0L, nuevaVersion);
        if (updated != 1) {
            throw new CostoVersionConflictException(producto.getProductoId());
        }

        guardarHistorial(producto, nuevaVersion, null, costo, contexto);
        entityManager.refresh(producto);
        return new ResultadoCambio(producto, null, costo, true, nuevaVersion);
    }

    public BigDecimal normalizar(BigDecimal costo) {
        if (costo == null) {
            throw new IllegalArgumentException("El costo es obligatorio");
        }
        if (costo.signum() < 0) {
            throw new IllegalArgumentException("El costo no puede ser negativo");
        }
        BigDecimal normalizado = costo.setScale(COST_SCALE, RoundingMode.HALF_UP);
        if (normalizado.compareTo(MAX_COST) > 0) {
            throw new IllegalArgumentException("El costo excede la precision permitida");
        }
        return normalizado;
    }

    private void guardarHistorial(
            Producto producto,
            long version,
            BigDecimal anterior,
            BigDecimal nuevo,
            ContextoCambio contexto
    ) {
        ContextoCambio seguro = contexto != null
                ? contexto
                : ContextoCambio.sistema(ProductoCostoOrigen.CREACION);
        ProductoCostoHistorial historial = new ProductoCostoHistorial();
        historial.setProductoId(producto.getProductoId());
        historial.setProductoNombre(producto.getNombre());
        historial.setTipoProducto(producto.getTipo_producto());
        historial.setVersion(version);
        historial.setCostoAnterior(anterior);
        historial.setCostoNuevo(nuevo);
        historial.setCambiadoEn(LocalDateTime.now(applicationClock));
        historial.setUsuario(seguro.usuario());
        historial.setUsuarioUsername(resolveUsername(seguro));
        historial.setOrigen(seguro.origen());
        historial.setMotivo(trimToLength(seguro.motivo(), 500));
        historial.setReferencia(trimToLength(seguro.referencia(), 255));
        historial.setCargaCostosLote(seguro.lote());
        historialRepo.save(historial);
    }

    private String resolveUsername(ContextoCambio contexto) {
        String username;
        if (contexto.usuario() != null && contexto.usuario().getUsername() != null
                && !contexto.usuario().getUsername().isBlank()) {
            username = contexto.usuario().getUsername().trim();
        } else {
            username = contexto.username() == null || contexto.username().isBlank()
                    ? "system"
                    : contexto.username().trim();
        }
        return username.substring(0, Math.min(username.length(), 120));
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(trimmed.length(), maxLength));
    }

    public record ContextoCambio(
            User usuario,
            String username,
            ProductoCostoOrigen origen,
            String motivo,
            String referencia,
            CargaCostosLote lote
    ) {
        public ContextoCambio {
            if (origen == null) throw new IllegalArgumentException("El origen del costo es obligatorio");
        }

        public static ContextoCambio sistema(ProductoCostoOrigen origen) {
            return new ContextoCambio(null, "system", origen, null, null, null);
        }
    }

    public record ResultadoCambio(
            Producto producto,
            BigDecimal costoAnterior,
            BigDecimal costoNuevo,
            boolean actualizado,
            long version
    ) {}
}
