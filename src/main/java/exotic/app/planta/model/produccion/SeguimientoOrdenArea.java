package exotic.app.planta.model.produccion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "seguimiento_orden_area")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SeguimientoOrdenArea {

    public static final int ESTADO_PENDIENTE = 0;
    public static final int ESTADO_VISIBLE = 1;
    public static final int ESTADO_COMPLETADO = 2;
    public static final int ESTADO_OMITIDO = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_produccion_id", nullable = false)
    private OrdenProduccion ordenProduccion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruta_proceso_node_id", nullable = false)
    private RutaProcesoNode rutaProcesoNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_operativa_id", nullable = false)
    private AreaOperativa areaOperativa;

    @Column(nullable = false)
    private int estado = ESTADO_PENDIENTE;

    @Column(name = "posicion_secuencia")
    private Integer posicionSecuencia;

    @CreationTimestamp
    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_visible")
    private LocalDateTime fechaVisible;

    @Column(name = "fecha_completado")
    private LocalDateTime fechaCompletado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_reporta_id")
    private User usuarioReporta;

    @Column(length = 500)
    private String observaciones;
}
