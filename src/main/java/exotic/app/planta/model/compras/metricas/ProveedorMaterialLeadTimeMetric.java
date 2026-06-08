package exotic.app.planta.model.compras.metricas;

import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.model.producto.Material;
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
 * Derived lead-time metric for a supplier-material pair.
 * <p>
 * This metric is an auxiliary analytical snapshot. It is not a planning master
 * value and must not be used as an approved MRP lead time without an explicit
 * planning process.
 */
@Entity
@Table(
        name = "proveedor_material_lead_time_metric",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_proveedor_material_lead_time_metric_pair",
                        columnNames = {"proveedor_pk", "producto_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProveedorMaterialLeadTimeMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_pk", referencedColumnName = "pk", nullable = false)
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", referencedColumnName = "producto_id", nullable = false)
    private Material material;

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
