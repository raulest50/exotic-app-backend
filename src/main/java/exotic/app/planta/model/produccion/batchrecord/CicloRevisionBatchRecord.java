package exotic.app.planta.model.produccion.batchrecord;

import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Ciclo inmutable en identidad que conserva cada envío y decisión de Calidad. */
@Entity
@Table(
        name = "batch_record_ciclo_revision",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_batch_record_ciclo_numero",
                columnNames = {"batch_record_id", "numero"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class CicloRevisionBatchRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_record_id", nullable = false, updatable = false)
    private BatchRecord batchRecord;

    @Column(nullable = false, updatable = false)
    private long numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private OrigenCicloRevisionBatchRecord origen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoCicloRevisionBatchRecord estado = EstadoCicloRevisionBatchRecord.EN_REVISION;

    @Column(name = "enviado_en", nullable = false, updatable = false)
    private LocalDateTime enviadoEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enviado_por_id", nullable = false, updatable = false)
    private User enviadoPor;

    @Column(name = "motivo_envio", nullable = false, length = 500, updatable = false)
    private String motivoEnvio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_envio_id")
    private BatchRecordRevision revisionEnvio;

    @Column(name = "cerrado_en")
    private LocalDateTime cerradoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cerrado_por_id")
    private User cerradoPor;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    @PreUpdate
    private void validarInvariantes() {
        if (batchRecord == null || numero <= 0 || origen == null || estado == null
                || enviadoEn == null || enviadoPor == null || esBlanco(motivoEnvio)) {
            throw new IllegalStateException("El ciclo de revisión de Calidad está incompleto.");
        }
        boolean sinCierreInferible = estado == EstadoCicloRevisionBatchRecord.EN_REVISION
                || estado == EstadoCicloRevisionBatchRecord.MIGRADO_INCOMPLETO;
        if (sinCierreInferible != (cerradoEn == null && cerradoPor == null)) {
            throw new IllegalStateException(
                    "Un ciclo abierto o migrado incompleto no puede tener cierre; uno decidido debe conservarlo.");
        }
        if (cerradoEn != null && cerradoEn.isBefore(enviadoEn)) {
            throw new IllegalStateException("El ciclo no puede cerrarse antes de su envío.");
        }
    }

    @PreRemove
    private void impedirEliminacion() {
        throw new IllegalStateException("Un ciclo de revisión no puede eliminarse.");
    }

    private boolean esBlanco(String value) {
        return value == null || value.isBlank();
    }
}
