package exotic.app.planta.model.produccion.batchrecord;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "batch_record_projection_task")
@Getter
@Setter
@NoArgsConstructor
public class BatchRecordProjectionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_produccion_id", unique = true)
    private OrdenProduccion ordenProduccion;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_fabricacion_id", unique = true)
    private OrdenFabricacion ordenFabricacion;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false, unique = true)
    private Lote lote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitado_por_id", nullable = false)
    private User solicitadoPor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ObjetivoProyeccionBatchRecord objetivo = ObjetivoProyeccionBatchRecord.ACTIVO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTareaProyeccionBatchRecord estado = EstadoTareaProyeccionBatchRecord.PENDIENTE;

    @Column(name = "cantidad_obtenida", precision = 18, scale = 4)
    private BigDecimal cantidadObtenida;

    @Column(name = "limpiar_cantidad_obtenida", nullable = false)
    private boolean limpiarCantidadObtenida;

    @Column(nullable = false)
    private int intentos;

    /** Secuencia funcional para no perder solicitudes recibidas mientras se procesa. */
    @Column(name = "solicitud_version", nullable = false)
    private long solicitudVersion;

    @Column(name = "proximo_intento_en", nullable = false)
    private LocalDateTime proximoIntentoEn;

    @Column(name = "ultimo_error", columnDefinition = "TEXT")
    private String ultimoError;

    @CreationTimestamp
    @Column(name = "solicitado_en", nullable = false, updatable = false)
    private LocalDateTime solicitadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Column(name = "completado_en")
    private LocalDateTime completadoEn;

    @PrePersist
    @PreUpdate
    private void validarInvariantes() {
        if ((ordenProduccion == null) == (ordenFabricacion == null)) {
            throw new IllegalStateException(
                    "La tarea documental debe pertenecer exactamente a una OP o una OF.");
        }
        if (lote == null || solicitadoPor == null || objetivo == null || estado == null) {
            throw new IllegalStateException(
                    "Lote, solicitante, objetivo y estado son obligatorios en la tarea documental.");
        }
        if (intentos < 0) {
            throw new IllegalStateException("Los intentos documentales no pueden ser negativos.");
        }
        if (solicitudVersion < 0) {
            throw new IllegalStateException("La versión de solicitud no puede ser negativa.");
        }
        if (cantidadObtenida != null && cantidadObtenida.signum() < 0) {
            throw new IllegalStateException("La cantidad documental no puede ser negativa.");
        }
    }
}
