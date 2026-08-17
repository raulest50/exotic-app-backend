package exotic.app.planta.model.produccion.batchrecord;

import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Decisión inmutable emitida por Calidad sobre una revisión concreta. */
@Entity
@Table(name = "batch_record_decision_calidad")
@Getter
@Setter
@NoArgsConstructor
public class BatchRecordDecisionCalidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_record_id", nullable = false, updatable = false)
    private BatchRecord batchRecord;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private DecisionCalidadBatchRecord decision;

    @Column(nullable = false, length = 500, updatable = false)
    private String motivo;

    @Column(name = "decidida_en", nullable = false, updatable = false)
    private LocalDateTime decididaEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decidida_por_id", nullable = false, updatable = false)
    private User decididaPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_id")
    private BatchRecordRevision revision;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firma_id", unique = true)
    private BatchRecordFirma firma;

    @PrePersist
    private void validarCreacion() {
        if (batchRecord == null || decision == null || decididaPor == null
                || decididaEn == null || motivo == null || motivo.isBlank()) {
            throw new IllegalStateException("La decisión de Calidad está incompleta.");
        }
    }

    @PreRemove
    private void impedirEliminacion() {
        throw new IllegalStateException("Una decisión de Calidad no puede eliminarse.");
    }
}
