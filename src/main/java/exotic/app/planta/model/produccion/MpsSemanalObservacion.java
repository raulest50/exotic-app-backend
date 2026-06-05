package exotic.app.planta.model.produccion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "mps_semanal_observacion",
        indexes = {
                @Index(name = "idx_mps_sem_obs_mps_id", columnList = "mps_id"),
                @Index(name = "idx_mps_sem_obs_estado", columnList = "estado"),
                @Index(name = "idx_mps_sem_obs_mps_estado", columnList = "mps_id, estado"),
                @Index(name = "idx_mps_sem_obs_mps_revision", columnList = "mps_id, revision_mps")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class MpsSemanalObservacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "observacion_id", unique = true, updatable = false, nullable = false)
    private Long observacionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "mps_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_mps_semanal_observacion_mps")
    )
    private MasterProductionScheduleSemanal mpsSemanal;

    @Column(name = "revision_mps", nullable = false)
    private Integer revisionMps = 1;

    @Column(name = "autor_username", nullable = false, length = 100)
    private String autorUsername;

    @Column(name = "mensaje", nullable = false, columnDefinition = "TEXT")
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
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

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;
}
