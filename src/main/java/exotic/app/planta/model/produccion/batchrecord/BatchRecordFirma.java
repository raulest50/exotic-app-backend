package exotic.app.planta.model.produccion.batchrecord;

import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.firma.FirmaVisualUsuarioVersion;
import exotic.app.planta.model.produccion.SeguimientoOrdenAreaEvento;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacionEvento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Evidencia de una firma electrónica aplicada a contenido identificado por
 * hash. No almacena contraseñas, códigos de segundo factor ni otros secretos.
 * Los datos de identidad se copian como snapshot para preservar el significado
 * histórico aun si posteriormente cambia el perfil del usuario.
 */
@Entity
@Table(name = "batch_record_firma")
@Getter
@Setter
@NoArgsConstructor
public class BatchRecordFirma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_record_id", nullable = false, updatable = false)
    private BatchRecord batchRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "firmante_id", nullable = false, updatable = false)
    private User firmante;

    /** Etapa concreta firmada al declarar el cierre de un área. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_record_etapa_id", updatable = false)
    private BatchRecordEtapa etapa;

    /** Evento exacto que se firmó; permite múltiples ejecuciones históricas de una etapa. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seguimiento_evento_id", unique = true, updatable = false)
    private SeguimientoOrdenAreaEvento seguimientoEvento;

    /** Evento exacto de OF protegido por la firma de etapa. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_fabricacion_evento_id", unique = true, updatable = false)
    private OrdenFabricacionOperacionEvento ordenFabricacionEvento;

    /** Revisión documental exacta protegida por la firma, si su alcance es global. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_record_revision_id", updatable = false)
    private BatchRecordRevision revision;

    /** Versión visual vigente al firmar; es opcional y nunca autentica por sí misma. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firma_visual_version_id", updatable = false)
    private FirmaVisualUsuarioVersion firmaVisualVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private AlcanceFirmaBatchRecord alcance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private DecisionFirmaBatchRecord decision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private MetodoFirmaElectronica metodo;

    @Column(name = "firmado_en", nullable = false, updatable = false)
    private LocalDateTime firmadoEn;

    @Column(name = "autenticado_en", nullable = false, updatable = false)
    private LocalDateTime autenticadoEn;

    @Column(name = "hash_contenido_firmado", nullable = false, updatable = false, length = 64)
    private String hashContenidoFirmado;

    @Column(name = "algoritmo_hash", nullable = false, updatable = false, length = 20)
    private String algoritmoHash = "SHA-256";

    @Column(name = "username_firmante", nullable = false, updatable = false, length = 120)
    private String usernameFirmante;

    @Column(name = "nombre_firmante", nullable = false, updatable = false, length = 200)
    private String nombreFirmante;

    @Column(name = "cedula_firmante", nullable = false, updatable = false, length = 30)
    private String cedulaFirmante;

    /** Cargo o autoridad que habilitó al usuario para esta firma. */
    @Column(name = "rol_firmante", nullable = false, updatable = false, length = 120)
    private String rolFirmante;

    /** Texto mostrado y aceptado por el firmante. */
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String manifestacion;

    @Column(length = 500, updatable = false)
    private String comentario;

    @Column(name = "ip_origen", length = 64, updatable = false)
    private String ipOrigen;

    @Column(name = "user_agent", length = 500, updatable = false)
    private String userAgent;

    @PrePersist
    private void validarInvariantes() {
        if (batchRecord == null || firmante == null || alcance == null || decision == null || metodo == null) {
            throw new IllegalStateException("La firma electrónica está incompleta.");
        }
        boolean firmaEtapa = alcance == AlcanceFirmaBatchRecord.CIERRE_ETAPA_AREA;
        boolean tieneEventoEtapa = (seguimientoEvento != null) ^ (ordenFabricacionEvento != null);
        if (firmaEtapa != (etapa != null) || firmaEtapa != tieneEventoEtapa) {
            throw new IllegalStateException(
                    "Solo una firma de cierre de área puede y debe referenciar una etapa.");
        }
        boolean eventoCoincide = !firmaEtapa || (seguimientoEvento != null
                ? mismaEntidad(etapa.getSeguimientoEventoOrigen(), seguimientoEvento)
                : mismaEntidad(etapa.getOrdenFabricacionEventoOrigen(), ordenFabricacionEvento));
        if (firmaEtapa && (revision != null || !eventoCoincide)) {
            throw new IllegalStateException(
                    "La firma de etapa debe referenciar su evento operativo vigente y no una revisión global.");
        }
        if (!firmaEtapa && revision == null) {
            throw new IllegalStateException(
                    "Una firma global del expediente debe referenciar una revisión documental.");
        }
        if (etapa != null && etapa.getBatchRecord() != batchRecord) {
            Long recordId = batchRecord.getId();
            Long etapaRecordId = etapa.getBatchRecord() != null ? etapa.getBatchRecord().getId() : null;
            if (recordId == null || !recordId.equals(etapaRecordId)) {
                throw new IllegalStateException("La etapa firmada pertenece a otro batch record.");
            }
        }
        if (firmaEtapa && decision != DecisionFirmaBatchRecord.CONFIRMA) {
            throw new IllegalStateException("El cierre de un área se registra con decisión CONFIRMA.");
        }
        if (firmaEtapa && (etapa.getEstado() != EstadoBatchRecordEtapa.COMPLETADA
                || !mismoUsuario(firmante, etapa.getReportadaPor()))) {
            throw new IllegalStateException(
                    "El cierre de área solo puede firmarlo quien reportó una etapa completada.");
        }
        if (firmaEtapa && (etapa.getAreaOperativa().getResponsableArea() == null
                || !mismoUsuario(firmante, etapa.getAreaOperativa().getResponsableArea()))) {
            throw new IllegalStateException(
                    "El cierre de área solo puede firmarlo el responsable vigente del área operativa.");
        }
        if (firmadoEn == null || autenticadoEn == null || autenticadoEn.isAfter(firmadoEn)) {
            throw new IllegalStateException("Los tiempos de autenticación y firma no son válidos.");
        }
        if (!esHashSha256(hashContenidoFirmado) || !"SHA-256".equalsIgnoreCase(algoritmoHash)) {
            throw new IllegalStateException("La firma debe proteger contenido mediante un hash SHA-256 válido.");
        }
        String hashEsperado = etapa != null
                ? etapa.getContenidoSha256()
                : revision.getContenidoSha256();
        if (hashEsperado == null || !hashEsperado.equalsIgnoreCase(hashContenidoFirmado)) {
            throw new IllegalStateException(
                    "El hash firmado no corresponde al contenido vigente de la etapa o expediente.");
        }
        if (esBlanco(usernameFirmante) || esBlanco(nombreFirmante)
                || esBlanco(cedulaFirmante) || esBlanco(rolFirmante) || esBlanco(manifestacion)) {
            throw new IllegalStateException("La identidad y manifestación del firmante son obligatorias.");
        }
        String nombreEsperado = firmante.getNombreCompleto() != null
                && !firmante.getNombreCompleto().isBlank()
                ? firmante.getNombreCompleto()
                : firmante.getUsername();
        if (!usernameFirmante.equals(firmante.getUsername())
                || !nombreFirmante.equals(nombreEsperado)
                || !cedulaFirmante.equals(Long.toString(firmante.getCedula()))) {
            throw new IllegalStateException("El snapshot de identidad no corresponde al usuario firmante.");
        }
    }

    @PreUpdate
    private void impedirModificacion() {
        throw new IllegalStateException("Una firma electrónica registrada es inmutable.");
    }

    @PreRemove
    private void impedirEliminacion() {
        throw new IllegalStateException("Una firma electrónica registrada no puede eliminarse.");
    }

    private boolean esHashSha256(String value) {
        return value != null && value.matches("[0-9A-Fa-f]{64}");
    }

    private boolean esBlanco(String value) {
        return value == null || value.isBlank();
    }

    private boolean mismoUsuario(User primero, User segundo) {
        return primero == segundo
                || (primero != null
                && segundo != null
                && primero.getId() != null
                && primero.getId().equals(segundo.getId()));
    }

    private boolean mismaEntidad(Object primera, Object segunda) {
        if (primera == segunda) {
            return true;
        }
        if (primera instanceof SeguimientoOrdenAreaEvento primerSeguimiento
                && segunda instanceof SeguimientoOrdenAreaEvento segundoSeguimiento) {
            return primerSeguimiento.getId() != null
                    && primerSeguimiento.getId().equals(segundoSeguimiento.getId());
        }
        if (primera instanceof OrdenFabricacionOperacionEvento primerEvento
                && segunda instanceof OrdenFabricacionOperacionEvento segundoEvento) {
            return primerEvento.getId() != null
                    && primerEvento.getId().equals(segundoEvento.getId());
        }
        return false;
    }
}
