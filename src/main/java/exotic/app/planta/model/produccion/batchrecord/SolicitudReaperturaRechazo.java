package exotic.app.planta.model.produccion.batchrecord;

import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Doble autorización auditada para reabrir excepcionalmente un rechazo. */
@Entity
@Table(name = "batch_record_solicitud_reapertura_rechazo")
@Getter
@Setter
@NoArgsConstructor
public class SolicitudReaperturaRechazo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_record_id", nullable = false, updatable = false)
    private BatchRecord batchRecord;

    @Column(name = "ciclo_revision_numero", nullable = false, updatable = false)
    private long cicloRevisionNumero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSolicitudReaperturaRechazo estado = EstadoSolicitudReaperturaRechazo.PENDIENTE;

    @Column(name = "solicitada_en", nullable = false, updatable = false)
    private LocalDateTime solicitadaEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitada_por_id", nullable = false, updatable = false)
    private User solicitadaPor;

    @Column(name = "motivo", nullable = false, length = 500, updatable = false)
    private String motivo;

    @Column(name = "evidencia", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String evidencia;

    @Column(name = "alcance", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String alcance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_solicitud_id")
    private BatchRecordRevision revisionSolicitud;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firma_solicitud_id", unique = true)
    private BatchRecordFirma firmaSolicitud;

    @Column(name = "aprobada_en")
    private LocalDateTime aprobadaEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aprobada_por_id")
    private User aprobadaPor;

    @Column(name = "motivo_aprobacion", length = 500)
    private String motivoAprobacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_aprobacion_id")
    private BatchRecordRevision revisionAprobacion;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firma_aprobacion_id", unique = true)
    private BatchRecordFirma firmaAprobacion;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    @PreUpdate
    private void validarInvariantes() {
        if (batchRecord == null || cicloRevisionNumero <= 0 || estado == null
                || solicitadaEn == null || solicitadaPor == null
                || esBlanco(motivo) || esBlanco(evidencia) || esBlanco(alcance)) {
            throw new IllegalStateException("La solicitud de reapertura está incompleta.");
        }
        if (estado == EstadoSolicitudReaperturaRechazo.PENDIENTE
                && (aprobadaEn != null || aprobadaPor != null || motivoAprobacion != null
                || revisionAprobacion != null || firmaAprobacion != null)) {
            throw new IllegalStateException("Una solicitud pendiente no puede tener aprobación.");
        }
        if (estado == EstadoSolicitudReaperturaRechazo.APROBADA
                && (aprobadaEn == null || aprobadaPor == null || esBlanco(motivoAprobacion))) {
            throw new IllegalStateException("La aprobación de reapertura está incompleta.");
        }
        if (aprobadaPor != null && mismoUsuario(solicitadaPor, aprobadaPor)) {
            throw new IllegalStateException(
                    "La reapertura debe aprobarla un usuario distinto del solicitante.");
        }
    }

    @PreRemove
    private void impedirEliminacion() {
        throw new IllegalStateException("Una solicitud de reapertura no puede eliminarse.");
    }

    private boolean mismoUsuario(User primero, User segundo) {
        return primero == segundo || (primero != null && segundo != null
                && primero.getId() != null && primero.getId().equals(segundo.getId()));
    }

    private boolean esBlanco(String value) {
        return value == null || value.isBlank();
    }
}
