package exotic.app.planta.model.produccion.fabricacion;

import exotic.app.planta.model.produccion.EstadoMpsSemanalObservacion;
import exotic.app.planta.model.produccion.TipoMpsSemanalObservacion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** Evidencia de revision del MPS OF, independiente de las observaciones OP. */
@Entity
@Table(name = "mps_fabricacion_observacion")
@Getter
@Setter
public class MpsFabricacionObservacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "observacion_id")
    private Long observacionId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mps_id", nullable = false)
    private MpsFabricacionSemanal mps;
    @Column(name = "revision_mps", nullable = false)
    private int revisionMps;
    @Column(name = "autor_username", nullable = false, length = 100)
    private String autorUsername;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensaje;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private TipoMpsSemanalObservacion tipo = TipoMpsSemanalObservacion.BLOQUEANTE;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private EstadoMpsSemanalObservacion estado = EstadoMpsSemanalObservacion.ABIERTA;
    @Column(name = "respuesta_correccion", columnDefinition = "TEXT")
    private String respuestaCorreccion;
    @Column(name = "atendida_por_username", length = 100)
    private String atendidaPorUsername;
    @Column(name = "fecha_atencion")
    private LocalDateTime fechaAtencion;
    @Column(name = "cerrada_por_username", length = 100)
    private String cerradaPorUsername;
    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;
}
