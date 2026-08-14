package exotic.app.planta.model.produccion.batchrecord;

import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Desviación detectada durante la ejecución y su resolución documentada. */
@Entity
@Table(
        name = "batch_record_desviacion",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_batch_record_desviacion_codigo",
                columnNames = {"batch_record_id", "codigo"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class BatchRecordDesviacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_record_id", nullable = false)
    private BatchRecord batchRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_record_etapa_id")
    private BatchRecordEtapa etapa;

    @Column(nullable = false, length = 60)
    private String codigo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoBatchRecordDesviacion estado = EstadoBatchRecordDesviacion.ABIERTA;

    @Column(name = "detectada_en", nullable = false)
    private LocalDateTime detectadaEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "detectada_por_id", nullable = false)
    private User detectadaPor;

    @Column(name = "evaluacion_impacto", columnDefinition = "TEXT")
    private String evaluacionImpacto;

    @Column(columnDefinition = "TEXT")
    private String resolucion;

    @Column(name = "resuelta_en")
    private LocalDateTime resueltaEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resuelta_por_id")
    private User resueltaPor;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    @PreUpdate
    private void validarInvariantes() {
        if (batchRecord == null || codigo == null || codigo.isBlank()
                || descripcion == null || descripcion.isBlank()
                || estado == null || detectadaEn == null || detectadaPor == null) {
            throw new IllegalStateException(
                    "El expediente, código, descripción, estado, fecha y autor de la desviación son obligatorios.");
        }
        if (etapa != null && etapa.getBatchRecord() != batchRecord) {
            Long recordId = batchRecord.getId();
            Long etapaRecordId = etapa.getBatchRecord() != null ? etapa.getBatchRecord().getId() : null;
            if (recordId == null || !recordId.equals(etapaRecordId)) {
                throw new IllegalStateException("La etapa de la desviación pertenece a otro batch record.");
            }
        }
        boolean resuelta = estado == EstadoBatchRecordDesviacion.RESUELTA
                || estado == EstadoBatchRecordDesviacion.CERRADA;
        if (resuelta && (resueltaEn == null || resueltaPor == null
                || resolucion == null || resolucion.isBlank()
                || evaluacionImpacto == null || evaluacionImpacto.isBlank())) {
            throw new IllegalStateException(
                    "Una desviación resuelta requiere impacto, fecha, responsable y resolución.");
        }
        if (!resuelta && (resueltaEn != null || resueltaPor != null)) {
            throw new IllegalStateException("Una desviación abierta no puede tener datos de resolución.");
        }
        if (resueltaEn != null && resueltaEn.isBefore(detectadaEn)) {
            throw new IllegalStateException("Una desviación no puede resolverse antes de detectarse.");
        }
    }
}
