package exotic.app.planta.model.produccion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "mps_semanal_lote_planificado",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mps_sem_lote_item_ordinal", columnNames = {"mps_item_id", "lote_ordinal"})
        }
)
@Getter
@Setter
@NoArgsConstructor
public class MpsSemanalLotePlanificado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, updatable = false, nullable = false)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "mps_item_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_mps_sem_lote_item")
    )
    private MpsSemanalItem mpsItem;

    @Column(name = "lote_ordinal", nullable = false)
    private int loteOrdinal;

    @Column(name = "cantidad_planificada", nullable = false)
    private double cantidadPlanificada;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private EstadoMpsSemanalLotePlanificado estado = EstadoMpsSemanalLotePlanificado.PENDIENTE_ODP;

    @JsonIgnore
    @OneToOne(mappedBy = "mpsLotePlanificado", fetch = FetchType.LAZY)
    private OrdenProduccion ordenProduccion;
}
