package exotic.app.planta.model.produccion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(
        name = "semana_mps",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_semana_mps_codigo", columnNames = "codigo"),
                @UniqueConstraint(name = "uk_semana_mps_standard_anio_numero", columnNames = {"standard", "anio_semana", "numero_semana"}),
                @UniqueConstraint(name = "uk_semana_mps_standard_start_date", columnNames = {"standard", "start_date"})
        }
)
@Getter
@Setter
@NoArgsConstructor
public class SemanaMPS {

    public static final String STANDARD_ISO_8601_MONDAY_SATURDAY = "ISO_8601_MONDAY_SATURDAY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, updatable = false, nullable = false)
    private Long id;

    @Column(name = "codigo", nullable = false, length = 20)
    private String codigo;

    @Column(name = "anio_semana", nullable = false)
    private int anioSemana;

    @Column(name = "numero_semana", nullable = false)
    private int numeroSemana;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "standard", nullable = false, length = 40)
    private String standard = STANDARD_ISO_8601_MONDAY_SATURDAY;

    @JsonIgnore
    @OneToOne(mappedBy = "semanaMps", fetch = FetchType.LAZY)
    private MasterProductionScheduleSemanal mpsSemanal;
}
