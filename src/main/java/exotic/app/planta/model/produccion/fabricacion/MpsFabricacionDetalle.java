package exotic.app.planta.model.produccion.fabricacion;

import exotic.app.planta.model.producto.SemiTerminado;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "mps_fabricacion_detalle")
@Getter
@Setter
public class MpsFabricacionDetalle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mps_id", nullable = false)
    private MpsFabricacionSemanal mps;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semiterminado_id", nullable = false)
    private SemiTerminado semiTerminado;
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal cantidad;
    @Column(name = "unidad_medida", nullable = false, length = 20)
    private String unidadMedida;
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;
    @Column(name = "fecha_final", nullable = false)
    private LocalDateTime fechaFinal;
    @Column(length = 2000)
    private String observaciones;
    @Column(nullable = false)
    private int posicion;
}
