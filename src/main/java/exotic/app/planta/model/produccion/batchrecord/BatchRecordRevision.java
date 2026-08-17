package exotic.app.planta.model.produccion.batchrecord;

import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Fotografía estructurada e inmutable del expediente. No contiene el PDF: el
 * documento se reconstruye bajo demanda a partir de este JSON canónico.
 */
@Entity
@Table(
        name = "batch_record_revision",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_batch_record_revision_numero",
                columnNames = {"batch_record_id", "numero"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class BatchRecordRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_record_id", nullable = false, updatable = false)
    private BatchRecord batchRecord;

    @Column(nullable = false, updatable = false)
    private int numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private TipoRevisionBatchRecord tipo;

    @Column(name = "contenido_canonico", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String contenidoCanonico;

    @Column(name = "contenido_sha256", nullable = false, length = 64, updatable = false)
    private String contenidoSha256;

    @Column(name = "esquema_version", nullable = false, length = 30, updatable = false)
    private String esquemaVersion;

    @Column(name = "plantilla_pdf_version", nullable = false, length = 30, updatable = false)
    private String plantillaPdfVersion;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private LocalDateTime creadaEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creada_por_id", nullable = false, updatable = false)
    private User creadaPor;

    @Column(name = "creada_por_username", nullable = false, length = 120, updatable = false)
    private String creadaPorUsername;

    @Column(name = "creada_por_nombre", nullable = false, length = 200, updatable = false)
    private String creadaPorNombre;

    @Column(name = "creada_por_cedula", nullable = false, length = 30, updatable = false)
    private String creadaPorCedula;

    @Column(length = 500, updatable = false)
    private String motivo;

    @PrePersist
    private void validarCreacion() {
        if (batchRecord == null || tipo == null || creadaPor == null || creadaEn == null
                || numero <= 0 || esBlanco(contenidoCanonico)
                || esBlanco(esquemaVersion) || esBlanco(plantillaPdfVersion)
                || esBlanco(creadaPorUsername) || esBlanco(creadaPorNombre)
                || esBlanco(creadaPorCedula)
                || contenidoSha256 == null
                || !contenidoSha256.matches("[0-9A-Fa-f]{64}")) {
            throw new IllegalStateException("La revisión estructurada del expediente está incompleta.");
        }
    }

    @PreUpdate
    private void impedirModificacion() {
        throw new IllegalStateException("Una revisión del expediente es inmutable.");
    }

    @PreRemove
    private void impedirEliminacion() {
        throw new IllegalStateException("Una revisión del expediente no puede eliminarse.");
    }

    private boolean esBlanco(String value) {
        return value == null || value.isBlank();
    }
}
