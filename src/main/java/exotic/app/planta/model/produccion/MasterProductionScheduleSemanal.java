package exotic.app.planta.model.produccion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "master_production_schedule_semanal")
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

    @JsonIgnore
    @OneToMany(mappedBy = "mpsSemanal")
    private List<OrdenProduccion> ordenesProduccion = new ArrayList<>();
}
