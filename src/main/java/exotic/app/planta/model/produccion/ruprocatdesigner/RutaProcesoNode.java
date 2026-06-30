package exotic.app.planta.model.produccion.ruprocatdesigner;

import exotic.app.planta.model.organizacion.AreaOperativa;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ruta_proceso_node")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RutaProcesoNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruta_proceso_cat_version_id", nullable = false)
    private RutaProcesoCatVersion rutaProcesoCatVersion;

    @Column(name = "frontend_id")
    private String frontendId;

    @Column(name = "posicion_x")
    private double posicionX;

    @Column(name = "posicion_y")
    private double posicionY;

    @ManyToOne
    @JoinColumn(name = "area_operativa_id")
    private AreaOperativa areaOperativa;

    private String label;

    @Column(name = "has_left_handle")
    private boolean hasLeftHandle = true;

    @Column(name = "has_right_handle")
    private boolean hasRightHandle = true;

    @Column(name = "duracion_estimada_minutos", nullable = false)
    private int duracionEstimadaMinutos = 0;

    @Column(name = "requiere_jornada_laboral", nullable = false)
    private boolean requiereJornadaLaboral = true;
}
