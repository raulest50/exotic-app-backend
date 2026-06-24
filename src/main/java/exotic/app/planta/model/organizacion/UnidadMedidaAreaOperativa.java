package exotic.app.planta.model.organizacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "unidad_medida_area_operativa",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_umao_area_codigo",
                        columnNames = {"area_operativa_id", "codigo"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnidadMedidaAreaOperativa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "unidad_medida_area_operativa_id", unique = true, updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_operativa_id", nullable = false)
    private AreaOperativa areaOperativa;

    @Column(name = "codigo", nullable = false, length = 32)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "descripcion")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "dimension", nullable = false, length = 32)
    private DimensionUnidadAreaOperativa dimension;

    @Column(name = "unidad_referencia", nullable = false, length = 16)
    private String unidadReferencia;

    @Column(name = "factor_a_referencia", nullable = false, precision = 19, scale = 6)
    private BigDecimal factorAReferencia;

    @Column(name = "principal", nullable = false)
    private boolean principal;

    @Column(name = "discreta", nullable = false)
    private boolean discreta;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;
}
