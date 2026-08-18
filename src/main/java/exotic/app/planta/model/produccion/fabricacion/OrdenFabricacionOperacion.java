package exotic.app.planta.model.produccion.fabricacion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionDocumentoVersion;
import exotic.app.planta.model.produccion.EstadoSeguimientoOrdenArea;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordEtapa;
import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Proyeccion operativa propia de una OF; no reutiliza el seguimiento exclusivo de OP. */
@Entity
@Table(name = "orden_fabricacion_operacion")
@Getter
@Setter
@NoArgsConstructor
public class OrdenFabricacionOperacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_fabricacion_id", nullable = false)
    private OrdenFabricacion ordenFabricacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_operativa_id", nullable = false)
    private AreaOperativa areaOperativa;

    /** Version exacta del POE vigente al emitir la OF. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poe_documento_version_id")
    private ProcesoProduccionDocumentoVersion poeDocumentoVersion;

    @Column(name = "frontend_node_id", nullable = false, length = 255)
    private String frontendNodeId;

    /** Identificador congelado con fines de referencia, no de resolucion dinamica. */
    @Column(name = "proceso_produccion_id")
    private Integer procesoProduccionId;

    @Column(name = "proceso_nombre", nullable = false, length = 200)
    private String procesoNombre;

    @Column(name = "posicion_secuencia", nullable = false)
    private int posicionSecuencia;

    @Column(nullable = false)
    private int estado;

    @Column(name = "fecha_estado_actual", nullable = false)
    private LocalDateTime fechaEstadoActual;

    @Column(name = "fecha_visible")
    private LocalDateTime fechaVisible;

    @Column(name = "fecha_completado")
    private LocalDateTime fechaCompletado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_reporta_id")
    private User usuarioReporta;

    @Column(length = 500)
    private String observaciones;

    @OneToOne(mappedBy = "ordenFabricacionOperacion", fetch = FetchType.LAZY)
    private BatchRecordEtapa batchRecordEtapa;

    @OneToMany(mappedBy = "operacion", fetch = FetchType.LAZY)
    @OrderBy("fechaEvento ASC, id ASC")
    private List<OrdenFabricacionOperacionEvento> eventos = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private long version;

    public EstadoSeguimientoOrdenArea getEstadoEnum() {
        return EstadoSeguimientoOrdenArea.fromCode(estado);
    }

    public void setEstadoEnum(EstadoSeguimientoOrdenArea estado) {
        this.estado = estado.getCode();
    }
}
