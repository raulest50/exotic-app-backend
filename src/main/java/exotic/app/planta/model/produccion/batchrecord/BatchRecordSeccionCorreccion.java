package exotic.app.planta.model.produccion.batchrecord;

import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Sección documental concreta devuelta por Calidad y pendiente de subsanación. */
@Entity
@Table(
        name = "batch_record_seccion_correccion",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_batch_record_seccion_ciclo",
                columnNames = {"batch_record_id", "ciclo_revision_numero", "seccion"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class BatchRecordSeccionCorreccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_record_id", nullable = false, updatable = false)
    private BatchRecord batchRecord;

    @Column(name = "ciclo_revision_numero", nullable = false, updatable = false)
    private long cicloRevisionNumero;

    @Column(nullable = false, length = 120, updatable = false)
    private String seccion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSeccionCorreccionBatchRecord estado =
            EstadoSeccionCorreccionBatchRecord.PENDIENTE;

    @Column(name = "solicitada_en", nullable = false, updatable = false)
    private LocalDateTime solicitadaEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitada_por_id", nullable = false, updatable = false)
    private User solicitadaPor;

    @Column(name = "atendida_en")
    private LocalDateTime atendidaEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendida_por_id")
    private User atendidaPor;

    @Column(name = "justificacion", length = 500)
    private String justificacion;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    @PreUpdate
    private void validarInvariantes() {
        if (batchRecord == null || cicloRevisionNumero <= 0 || esBlanco(seccion)
                || estado == null || solicitadaEn == null || solicitadaPor == null) {
            throw new IllegalStateException("La sección documental devuelta está incompleta.");
        }
        if (estado == EstadoSeccionCorreccionBatchRecord.PENDIENTE
                && (atendidaEn != null || atendidaPor != null || justificacion != null)) {
            throw new IllegalStateException("Una sección pendiente no puede tener atención.");
        }
        if (estado == EstadoSeccionCorreccionBatchRecord.ATENDIDA
                && (atendidaEn == null || atendidaPor == null || esBlanco(justificacion))) {
            throw new IllegalStateException("La atención de la sección está incompleta.");
        }
    }

    @PreRemove
    private void impedirEliminacion() {
        throw new IllegalStateException("Una sección devuelta no puede eliminarse.");
    }

    private boolean esBlanco(String value) {
        return value == null || value.isBlank();
    }
}
