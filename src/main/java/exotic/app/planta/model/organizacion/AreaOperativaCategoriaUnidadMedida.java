package exotic.app.planta.model.organizacion;

import exotic.app.planta.model.producto.Categoria;
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

import java.math.BigDecimal;

@Entity
@Table(
        name = "area_operativa_categoria_unidad_medida",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_aocum_area_categoria",
                        columnNames = {"area_operativa_id", "categoria_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AreaOperativaCategoriaUnidadMedida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "area_operativa_categoria_unidad_medida_id", unique = true, updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_operativa_id", nullable = false)
    private AreaOperativa areaOperativa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_medida_area_operativa_id", nullable = false)
    private UnidadMedidaAreaOperativa unidadMedida;

    @Column(name = "factor_lote", nullable = false, precision = 19, scale = 6)
    private BigDecimal factorLote;
}
