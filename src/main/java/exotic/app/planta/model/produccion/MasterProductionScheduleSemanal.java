package exotic.app.planta.model.produccion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "master_production_schedule_semanal",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mps_week_start_date", columnNames = "week_start_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class MasterProductionScheduleSemanal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mps_id", unique = true, updatable = false, nullable = false)
    private Integer mpsId;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_end_date", nullable = false)
    private LocalDate weekEndDate;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoMpsSemanal estado = EstadoMpsSemanal.BORRADOR;

    @Column(name = "fecha_aprobacion")
    private LocalDateTime fechaAprobacion;

    @Column(name = "aprobado_por_username", length = 100)
    private String aprobadoPorUsername;

    @Column(name = "fecha_generacion_odps")
    private LocalDateTime fechaGeneracionOdps;

    @Column(name = "generado_por_username", length = 100)
    private String generadoPorUsername;

    @Column(name = "snapshot_json", columnDefinition = "TEXT")
    private String snapshotJson;

    @JsonIgnore
    @OneToMany(mappedBy = "mpsSemanal")
    private List<OrdenProduccion> ordenesProduccion = new ArrayList<>();
}
