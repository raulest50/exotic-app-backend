package exotic.app.planta.model.produccion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "mps_semanal_dia",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mps_sem_dia_fecha", columnNames = {"mps_id", "fecha"}),
                @UniqueConstraint(name = "uk_mps_sem_dia_day_index", columnNames = {"mps_id", "day_index"})
        }
)
@Getter
@Setter
@NoArgsConstructor
public class MpsSemanalDia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, updatable = false, nullable = false)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "mps_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_mps_sem_dia_mps")
    )
    private MasterProductionScheduleSemanal mpsSemanal;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @OneToMany(mappedBy = "mpsDia", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC")
    private List<MpsSemanalItem> items = new ArrayList<>();
}
