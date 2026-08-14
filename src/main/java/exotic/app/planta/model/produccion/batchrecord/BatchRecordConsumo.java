package exotic.app.planta.model.produccion.batchrecord;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Consumo real incorporado al lote de resultado. Varias filas permiten que un
 * terminado mezcle lotes de granel; una misma fuente puede aparecer en varios
 * expedientes, permitiendo también dividir un granel entre varios terminados.
 */
@Entity
@Table(name = "batch_record_consumo")
@Getter
@Setter
@NoArgsConstructor
public class BatchRecordConsumo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_record_id", nullable = false)
    private BatchRecord batchRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    /** Nulo únicamente para insumos de consumo directo sin lote físico. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_origen_id")
    private Lote loteOrigen;

    /** Movimiento que respalda el consumo, si este afectó inventario. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movimiento_id", unique = true)
    private Movimiento movimiento;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal cantidad;

    @Column(name = "unidad_medida", nullable = false, length = 20)
    private String unidadMedida;

    @Column(name = "registrado_en", nullable = false)
    private LocalDateTime registradoEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registrado_por_id", nullable = false)
    private User registradoPor;

    @Column(length = 500)
    private String observaciones;

    @PrePersist
    @PreUpdate
    private void validarInvariantes() {
        if (batchRecord == null || producto == null || registradoPor == null || registradoEn == null) {
            throw new IllegalStateException(
                    "El expediente, producto, usuario y fecha del consumo son obligatorios.");
        }
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new IllegalStateException("La cantidad consumida debe ser mayor que cero.");
        }
        if (unidadMedida == null || unidadMedida.isBlank()) {
            throw new IllegalStateException("La unidad de medida del consumo es obligatoria.");
        }
        if (loteOrigen != null
                && loteOrigen.getProducto() != null
                && !mismoProducto(producto, loteOrigen.getProducto())) {
            throw new IllegalStateException("El lote de origen pertenece a otro producto.");
        }
        if (movimiento != null) {
            if (!mismoProducto(producto, movimiento.getProducto())) {
                throw new IllegalStateException("El movimiento pertenece a otro producto.");
            }
            if (!mismoLote(loteOrigen, movimiento.getLote())) {
                throw new IllegalStateException("El movimiento corresponde a otro lote de origen.");
            }
        }
    }

    private boolean mismoProducto(Producto primero, Producto segundo) {
        return primero == segundo
                || (primero != null
                && segundo != null
                && primero.getProductoId() != null
                && primero.getProductoId().equals(segundo.getProductoId()));
    }

    private boolean mismoLote(Lote primero, Lote segundo) {
        return primero == segundo
                || (primero != null
                && segundo != null
                && primero.getId() != null
                && primero.getId().equals(segundo.getId()));
    }
}
