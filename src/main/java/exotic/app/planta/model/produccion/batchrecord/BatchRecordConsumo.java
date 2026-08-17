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
 * Evento inmutable del libro de materiales del lote. Las cantidades positivas
 * incorporan material y las exclusiones por avería son negativas. Su suma por
 * producto/lote representa el consumo neto y conserva la trazabilidad al
 * movimiento fuente.
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
    @JoinColumn(name = "batch_record_id", nullable = false, updatable = false)
    private BatchRecord batchRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false, updatable = false)
    private Producto producto;

    /** Nulo únicamente para insumos de consumo directo sin lote físico. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_origen_id", updatable = false)
    private Lote loteOrigen;

    /** Movimiento que respalda el consumo, si este afectó inventario. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movimiento_id", unique = true, updatable = false)
    private Movimiento movimiento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private TipoRegistroConsumoBatchRecord tipo;

    @Column(nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal cantidad;

    @Column(name = "unidad_medida", nullable = false, length = 20, updatable = false)
    private String unidadMedida;

    @Column(name = "registrado_en", nullable = false, updatable = false)
    private LocalDateTime registradoEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registrado_por_id", nullable = false, updatable = false)
    private User registradoPor;

    @Column(length = 500, updatable = false)
    private String observaciones;

    @PrePersist
    private void validarInvariantes() {
        if (batchRecord == null || producto == null || registradoPor == null || registradoEn == null) {
            throw new IllegalStateException(
                    "El expediente, producto, usuario y fecha del consumo son obligatorios.");
        }
        if (cantidad == null || cantidad.signum() == 0) {
            throw new IllegalStateException("La cantidad del registro de consumo no puede ser cero.");
        }
        if (tipo == null) {
            throw new IllegalStateException("El tipo del registro de consumo es obligatorio.");
        }
        if (tipo == TipoRegistroConsumoBatchRecord.EXCLUSION_AVERIA && cantidad.signum() >= 0) {
            throw new IllegalStateException("Una exclusión por avería debe registrar cantidad negativa.");
        }
        if (tipo != TipoRegistroConsumoBatchRecord.EXCLUSION_AVERIA && cantidad.signum() <= 0) {
            throw new IllegalStateException("Una dispensación o reposición debe registrar cantidad positiva.");
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

    @PreUpdate
    private void impedirModificacion() {
        throw new IllegalStateException(
                "Un registro histórico de consumo no puede modificarse.");
    }

    @PreRemove
    private void impedirEliminacion() {
        throw new IllegalStateException(
                "Un registro histórico de consumo no puede eliminarse.");
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
