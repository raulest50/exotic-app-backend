package exotic.app.planta.model.monitoreoerrores;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Evidencia de una ocurrencia. Los datos sensibles deben depurarse antes de persistirla. */
@Entity
@Table(name = "monitoreo_evento_error")
@Getter
@Setter
@NoArgsConstructor
public class EventoError {
    /** Puede asignarse desde el emisor y conservarse en reintentos del mismo reporte. */
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_error_id", nullable = false, updatable = false)
    private GrupoError grupoError;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private OrigenError origen;

    /** Identificador funcional; no concede permisos ni amplía el catalogo de accesos. */
    @Column(length = 100, updatable = false)
    private String modulo;

    @Column(length = 128, updatable = false)
    private String tab;

    @Column(length = 160, updatable = false)
    private String paso;

    @Column(length = 200, updatable = false)
    private String accion;

    /** Referencia historica sin FK: el evento sobrevive a la eliminacion del usuario. */
    @Column(name = "usuario_id", updatable = false)
    private Long usuarioId;

    @Column(name = "ocurrido_en", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant ocurridoEn;

    @Column(name = "recibido_en", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant recibidoEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private AmbienteError ambiente;

    @Column(name = "version_aplicacion", length = 120, updatable = false)
    private String versionAplicacion;

    @Column(name = "correlacion_id", length = 128, updatable = false)
    private String correlacionId;

    @Column(name = "tipo_error", nullable = false, length = 500, updatable = false)
    private String tipoError;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String mensaje;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String archivo;

    @Column(updatable = false)
    private Integer linea;

    @Column(name = "stack_trace", columnDefinition = "TEXT", updatable = false)
    private String stackTrace;

    /** Endpoint, metodo HTTP y estado de respuesta, cuando existan. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> solicitud;

    /** Lista de snapshots depurados, cada uno con origen, etapa y contenido. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private List<Map<String, Object>> payloads;

    /** Referencias {tipo, id, rol}; no requieren que la entidad siga existiendo. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private List<Map<String, Object>> entidades;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "acciones_previas", columnDefinition = "jsonb", updatable = false)
    private List<Map<String, Object>> accionesPrevias;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "logs_relacionados", columnDefinition = "jsonb", updatable = false)
    private List<Map<String, Object>> logsRelacionados;

    @PrePersist
    void prepararRegistro() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (recibidoEn == null) {
            recibidoEn = Instant.now();
        }
    }
}
