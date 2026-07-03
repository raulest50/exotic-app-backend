package exotic.app.planta.model.calidad;

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

@Entity
@Table(name = "calidad_control_proceso_caracteristica")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ControlProcesoCaracteristica {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plantilla_id", nullable = false)
    private ControlProcesoPlantilla plantilla;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCaracteristicaControlProceso tipo;

    @Column(length = 30)
    private String unidad;

    @Column(nullable = false)
    private Integer orden;

    @Column(name = "cantidad_muestras", nullable = false)
    private Integer cantidadMuestras;

    @Column(name = "unidades_por_muestra", nullable = false)
    private Integer unidadesPorMuestra;

    @Column(name = "limite_inferior")
    private Double limiteInferior;

    @Column(name = "limite_superior")
    private Double limiteSuperior;
}
