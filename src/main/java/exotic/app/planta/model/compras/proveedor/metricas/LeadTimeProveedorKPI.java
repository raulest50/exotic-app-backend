package exotic.app.planta.model.compras.proveedor.metricas;

import exotic.app.planta.model.compras.Proveedor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Auxiliary supplier-level lead-time KPI aggregated across materials.
 * <p>
 * This metric is useful for BI summaries and supplier monitoring. It is not a
 * material-specific lead time and must not be used as an approved MRP planning
 * master value.
 */
@Entity
@Table(
        name = "lead_time_proveedor_kpi",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_lead_time_proveedor_kpi_proveedor",
                        columnNames = {"proveedor_pk"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LeadTimeProveedorKPI {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_pk", referencedColumnName = "pk", nullable = false)
    private Proveedor proveedor;

    @Column(name = "fecha_corte", nullable = false)
    private LocalDate fechaCorte;

    @Column(name = "ventana_dias", nullable = false)
    private Integer ventanaDias;

    @Column(name = "lead_time_mediano_dias", nullable = false)
    private Double leadTimeMedianoDias;

    @Column(name = "observaciones", nullable = false)
    private Integer observaciones;

    @Column(name = "ordenes_consideradas", nullable = false)
    private Integer ordenesConsideradas;

    @Column(name = "calculado_en", nullable = false)
    private LocalDateTime calculadoEn;
}
