package exotic.app.planta.model.produccion.batchrecord;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.produccion.ActorTipoEventoSeguimiento;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.SeguimientoOrdenAreaEvento;
import exotic.app.planta.model.produccion.TipoEventoSeguimiento;
import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Evidencia de una etapa ejecutada dentro del expediente. Para las órdenes de
 * producción actuales puede apuntar al evento originado en AreaOperativaPanel;
 * para una orden de fabricación funciona sin depender de esa ruta específica.
 */
@Entity
@Table(
        name = "batch_record_etapa",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_batch_record_etapa_secuencia",
                columnNames = {"batch_record_id", "secuencia"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class BatchRecordEtapa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_record_id", nullable = false)
    private BatchRecord batchRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_operativa_id", nullable = false)
    private AreaOperativa areaOperativa;

    /** Evento fuente del panel operativo, si la etapa se originó allí. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seguimiento_evento_origen_id", unique = true)
    private SeguimientoOrdenAreaEvento seguimientoEventoOrigen;

    /** Nombre del proceso o etapa congelado para el expediente. */
    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(nullable = false)
    private int secuencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoBatchRecordEtapa estado = EstadoBatchRecordEtapa.PENDIENTE;

    @Column(name = "iniciada_en")
    private LocalDateTime iniciadaEn;

    @Column(name = "completada_en")
    private LocalDateTime completadaEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reportada_por_id")
    private User reportadaPor;

    @Column(name = "contenido_sha256", length = 64)
    private String contenidoSha256;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    @PreUpdate
    private void validarInvariantes() {
        if (batchRecord == null || areaOperativa == null || nombre == null || nombre.isBlank()) {
            throw new IllegalStateException("El expediente, área y nombre de la etapa son obligatorios.");
        }
        if (secuencia < 0 || estado == null) {
            throw new IllegalStateException("La secuencia y estado de la etapa no son válidos.");
        }
        if (completadaEn != null && iniciadaEn != null && completadaEn.isBefore(iniciadaEn)) {
            throw new IllegalStateException("La etapa no puede completarse antes de iniciarse.");
        }
        if (estado == EstadoBatchRecordEtapa.COMPLETADA
                && (iniciadaEn == null || completadaEn == null || reportadaPor == null)) {
            throw new IllegalStateException(
                    "Una etapa completada requiere inicio, terminación y usuario responsable.");
        }
        if (contenidoSha256 != null && !contenidoSha256.matches("[0-9A-Fa-f]{64}")) {
            throw new IllegalStateException("El hash de la etapa debe ser un SHA-256 válido.");
        }
        validarEventoOrigen();
    }

    private void validarEventoOrigen() {
        if (seguimientoEventoOrigen == null) {
            return;
        }
        if (seguimientoEventoOrigen.getSeguimientoOrdenArea() == null
                || !mismaArea(
                seguimientoEventoOrigen.getSeguimientoOrdenArea().getAreaOperativa(), areaOperativa)) {
            throw new IllegalStateException("El evento fuente pertenece a otra área operativa.");
        }
        if (batchRecord.getOrdenProduccion() == null
                || seguimientoEventoOrigen.getSeguimientoOrdenArea().getOrdenProduccion().getOrdenId()
                != batchRecord.getOrdenProduccion().getOrdenId()) {
            throw new IllegalStateException(
                    "El evento del panel no corresponde a la orden de producción del expediente.");
        }
        if (seguimientoEventoOrigen.getActorTipo() != ActorTipoEventoSeguimiento.USER
                || seguimientoEventoOrigen.getTipoEvento() != TipoEventoSeguimiento.OPERATIVO
                || seguimientoEventoOrigen.getEstadoDestino() != SeguimientoOrdenArea.ESTADO_COMPLETADO
                || seguimientoEventoOrigen.getUsuario() == null
                || estado != EstadoBatchRecordEtapa.COMPLETADA) {
            throw new IllegalStateException(
                    "La etapa solo puede originarse en un reporte operativo de terminación autenticado.");
        }
        if (seguimientoEventoOrigen.getUsuario() != null
                && reportadaPor != null
                && !mismoUsuario(seguimientoEventoOrigen.getUsuario(), reportadaPor)) {
            throw new IllegalStateException(
                    "El usuario de la etapa no corresponde al autor del evento operativo.");
        }
        if (completadaEn == null
                || !completadaEn.equals(seguimientoEventoOrigen.getFechaEvento())) {
            throw new IllegalStateException(
                    "La terminación de la etapa debe conservar la fecha del evento operativo.");
        }
    }

    private boolean mismoUsuario(User primero, User segundo) {
        return primero == segundo
                || (primero != null
                && segundo != null
                && primero.getId() != null
                && primero.getId().equals(segundo.getId()));
    }

    private boolean mismaArea(AreaOperativa primera, AreaOperativa segunda) {
        return primera == segunda
                || (primera != null
                && segunda != null
                && primera.getAreaId() != 0
                && primera.getAreaId() == segunda.getAreaId());
    }
}
