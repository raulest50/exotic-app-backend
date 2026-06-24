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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "capacidad_area_operativa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CapacidadAreaOperativa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "capacidad_area_operativa_id", unique = true, updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_operativa_id", nullable = false)
    private AreaOperativa areaOperativa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_medida_area_operativa_id", nullable = false)
    private UnidadMedidaAreaOperativa unidadMedida;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_capacidad", nullable = false, length = 32)
    private TipoCapacidadAreaOperativa tipoCapacidad;

    @Column(name = "cantidad", nullable = false, precision = 19, scale = 6)
    private BigDecimal cantidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodo", nullable = false, length = 32)
    private PeriodoCapacidadAreaOperativa periodo;

    @Column(name = "eficiencia", nullable = false, precision = 5, scale = 4)
    private BigDecimal eficiencia = BigDecimal.ONE;

    @Column(name = "vigente_desde")
    private LocalDate vigenteDesde;

    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;
}
