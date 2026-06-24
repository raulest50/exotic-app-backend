package exotic.app.planta.model.produccion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import exotic.app.planta.model.producto.Terminado;
import jakarta.persistence.CascadeType;
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
        name = "mps_semanal_item",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mps_sem_item_dia_terminado", columnNames = {"mps_dia_id", "terminado_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
public class MpsSemanalItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, updatable = false, nullable = false)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "mps_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_mps_sem_item_mps")
    )
    private MasterProductionScheduleSemanal mpsSemanal;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "mps_dia_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_mps_sem_item_dia")
    )
    private MpsSemanalDia mpsDia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "terminado_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_mps_sem_item_terminado")
    )
    private Terminado terminado;

    @Column(name = "terminado_nombre", nullable = false, length = 200)
    private String terminadoNombre;

    @Column(name = "categoria_id")
    private Integer categoriaId;

    @Column(name = "categoria_nombre", length = 200)
    private String categoriaNombre;

    @Column(name = "lote_size", nullable = false)
    private int loteSize;

    @Column(name = "tiempo_dias_fabricacion", nullable = false)
    private int tiempoDiasFabricacion;

    @Column(name = "numero_lotes", nullable = false)
    private int numeroLotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoMpsSemanalItem estado = EstadoMpsSemanalItem.ACTIVO;

    @Column(name = "cantidad_total", nullable = false)
    private double cantidadTotal;

    @Column(name = "fecha_lanzamiento", nullable = false)
    private LocalDate fechaLanzamiento;

    @Column(name = "fecha_final_planificada", nullable = false)
    private LocalDate fechaFinalPlanificada;

    @Column(name = "observacion", columnDefinition = "TEXT")
    private String observacion;

    @Column(name = "warning", columnDefinition = "TEXT")
    private String warning;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @OneToMany(mappedBy = "mpsItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("loteOrdinal ASC")
    private List<MpsSemanalLotePlanificado> lotesPlanificados = new ArrayList<>();
}
