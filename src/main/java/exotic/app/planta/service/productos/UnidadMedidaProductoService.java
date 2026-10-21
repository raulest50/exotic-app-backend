package exotic.app.planta.service.productos;

import exotic.app.planta.model.producto.UnidadMedidaProducto;
import exotic.app.planta.model.producto.dto.UnidadMedidaProductoDTO;
import exotic.app.planta.repo.producto.UnidadMedidaProductoRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UnidadMedidaProductoService {

    private final UnidadMedidaProductoRepo unidadMedidaProductoRepo;

    public List<UnidadMedidaProductoDTO> listar() {
        return unidadMedidaProductoRepo.findAllByOrderByCodigoAsc().stream()
                .map(UnidadMedidaProductoDTO::from)
                .toList();
    }

    public List<UnidadMedidaProductoDTO> listarCompatibles(String codigo) {
        UnidadMedidaProducto origen = obtenerUnidad(codigo);
        return unidadMedidaProductoRepo.findByMagnitudOrderByCodigoAsc(origen.getMagnitud()).stream()
                .map(UnidadMedidaProductoDTO::from)
                .toList();
    }

    /**
     * Convierte cantidades no negativas dentro de una misma magnitud.
     * La multiplicación es exacta; la división usa 34 cifras significativas (DECIMAL128).
     * No aplica redondeos de inventario ni infiere densidades o contenidos por unidad.
     */
    public BigDecimal convertir(BigDecimal cantidad, String codigoOrigen, String codigoDestino) {
        if (cantidad == null || cantidad.signum() < 0) {
            throw new IllegalArgumentException("La cantidad debe ser un número mayor o igual a cero.");
        }

        UnidadMedidaProducto origen = obtenerUnidad(codigoOrigen);
        UnidadMedidaProducto destino = obtenerUnidad(codigoDestino);
        if (origen.getMagnitud() != destino.getMagnitud()) {
            throw new IllegalArgumentException(
                    "No se puede convertir entre magnitudes diferentes: "
                            + origen.getCodigo() + " y " + destino.getCodigo() + "."
            );
        }
        if (origen.getCodigo().equals(destino.getCodigo())) {
            return cantidad;
        }

        return cantidad.multiply(origen.getFactorAReferencia())
                .divide(destino.getFactorAReferencia(), MathContext.DECIMAL128);
    }

    private UnidadMedidaProducto obtenerUnidad(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("Debe indicar un código de unidad de medida.");
        }
        String normalizado = codigo.strip().toUpperCase(Locale.ROOT);
        return unidadMedidaProductoRepo.findById(normalizado)
                .orElseThrow(() -> new IllegalArgumentException("Unidad de medida desconocida: " + normalizado));
    }
}
