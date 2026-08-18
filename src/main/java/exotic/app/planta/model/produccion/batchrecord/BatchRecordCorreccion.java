package exotic.app.planta.model.produccion.batchrecord;

import exotic.app.planta.model.produccion.SeguimientoOrdenAreaEvento;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacionEvento;
import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Corrección append-only: nunca sustituye ni oculta el evento original. */
@Entity
@Table(name = "batch_record_correccion")
@Getter
@Setter
@NoArgsConstructor
public class BatchRecordCorreccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_record_id", nullable = false, updatable = false)
    private BatchRecord batchRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_record_etapa_id", updatable = false)
    private BatchRecordEtapa etapa;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_correccion_id", unique = true, updatable = false)
    private SeguimientoOrdenAreaEvento eventoCorreccion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_revertido_id", updatable = false)
    private SeguimientoOrdenAreaEvento eventoRevertido;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_fabricacion_evento_correccion_id", unique = true, updatable = false)
    private OrdenFabricacionOperacionEvento ordenFabricacionEventoCorreccion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_fabricacion_evento_revertido_id", updatable = false)
    private OrdenFabricacionOperacionEvento ordenFabricacionEventoRevertido;

    @Column(name = "valor_anterior", nullable = false, length = 120, updatable = false)
    private String valorAnterior;

    @Column(name = "valor_nuevo", nullable = false, length = 120, updatable = false)
    private String valorNuevo;

    @Column(nullable = false, length = 500, updatable = false)
    private String motivo;

    @Column(name = "corregida_en", nullable = false, updatable = false)
    private LocalDateTime corregidaEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corregida_por_id", nullable = false, updatable = false)
    private User corregidaPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_id")
    private BatchRecordRevision revision;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firma_id", unique = true)
    private BatchRecordFirma firma;

    @PrePersist
    private void validarCreacion() {
        if (batchRecord == null
                || ((eventoCorreccion == null) == (ordenFabricacionEventoCorreccion == null))
                || corregidaPor == null
                || corregidaEn == null || esBlanco(valorAnterior)
                || esBlanco(valorNuevo) || esBlanco(motivo)) {
            throw new IllegalStateException("La evidencia de corrección está incompleta.");
        }
    }

    @PreRemove
    private void impedirEliminacion() {
        throw new IllegalStateException("Una corrección del expediente no puede eliminarse.");
    }

    private boolean esBlanco(String value) {
        return value == null || value.isBlank();
    }
}
