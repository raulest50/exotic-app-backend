package exotic.app.planta.model.produccion.batchrecord;

import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Representación PDF versionada o anexo que forma parte del expediente. */
@Entity
@Table(
        name = "batch_record_documento",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_batch_record_documento_version",
                columnNames = {"batch_record_id", "tipo", "version_documento"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class BatchRecordDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_record_id", nullable = false, updatable = false)
    private BatchRecord batchRecord;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private TipoDocumentoBatchRecord tipo;

    @Column(name = "version_documento", nullable = false, updatable = false)
    private int versionDocumento;

    @Column(name = "nombre_archivo", nullable = false, length = 255, updatable = false)
    private String nombreArchivo;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(name = "tamano_bytes", nullable = false, updatable = false)
    private long tamanoBytes;

    @Column(nullable = false, length = 64, updatable = false)
    private String sha256;

    @Column(name = "storage_key", nullable = false, unique = true, length = 500, updatable = false)
    private String storageKey;

    @Column(name = "generado_en", nullable = false, updatable = false)
    private LocalDateTime generadoEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generado_por_id", nullable = false, updatable = false)
    private User generadoPor;

    @PrePersist
    private void validarInvariantes() {
        if (batchRecord == null || tipo == null || generadoPor == null || generadoEn == null
                || esBlanco(nombreArchivo) || esBlanco(contentType) || esBlanco(storageKey)) {
            throw new IllegalStateException("Los metadatos del documento del expediente están incompletos.");
        }
        if (versionDocumento <= 0 || tamanoBytes <= 0) {
            throw new IllegalStateException("La versión y el tamaño del documento deben ser mayores que cero.");
        }
        if (sha256 == null || !sha256.matches("[0-9A-Fa-f]{64}")) {
            throw new IllegalStateException("El documento debe registrar un hash SHA-256 válido.");
        }
        if (tipo == TipoDocumentoBatchRecord.EXPEDIENTE_PDF
                && !"application/pdf".equalsIgnoreCase(contentType)) {
            throw new IllegalStateException("El expediente PDF debe usar content type application/pdf.");
        }
    }

    @PreUpdate
    private void impedirModificacion() {
        throw new IllegalStateException(
                "Un documento registrado es inmutable; debe generarse una nueva versión.");
    }

    @PreRemove
    private void impedirEliminacion() {
        throw new IllegalStateException(
                "Un documento del expediente no se elimina; debe conservarse como evidencia histórica.");
    }

    private boolean esBlanco(String value) {
        return value == null || value.isBlank();
    }
}
