package exotic.app.planta.model.controles;

import exotic.app.planta.model.calidad.ControlProcesoEjecucion;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.produccion.batchrecord.BatchRecord;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordEtapa;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordFirma;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordRevision;
import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "control_requerido")
@Getter @Setter @NoArgsConstructor
public class ControlRequerido {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "version_id", nullable = false)
    private VersionPlanControl versionPlan;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "aplicabilidad_id")
    private AplicabilidadPlanControl aplicabilidad;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "batch_record_id")
    private BatchRecord batchRecord;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "batch_record_etapa_id")
    private BatchRecordEtapa batchRecordEtapa;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private OrigenControlRequerido origen;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private EstadoControlRequerido estado = EstadoControlRequerido.PENDIENTE;
    @Column(name = "ciclo_revision_numero") private Integer cicloRevisionNumero;
    @Column(name = "requiere_repeticion", nullable = false) private boolean requiereRepeticion;
    @Column(name = "requiere_revalidacion", nullable = false) private boolean requiereRevalidacion;
    @Column(name = "creado_en", nullable = false, updatable = false) private LocalDateTime creadoEn;
    @Column(name = "agregado_excepcionalmente", nullable = false) private boolean agregadoExcepcionalmente;
    @Column(name = "motivo_adicion", length = 500) private String motivoAdicion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "agregado_por_id") private User agregadoPor;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "revision_adicion_id")
    private BatchRecordRevision revisionAdicion;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "firma_adicion_id")
    private BatchRecordFirma firmaAdicion;
    @Column(name = "plan_codigo_snapshot", nullable = false, length = 60) private String planCodigoSnapshot;
    @Column(name = "plan_nombre_snapshot", nullable = false, length = 160) private String planNombreSnapshot;
    @Enumerated(EnumType.STRING) @Column(name = "ambito_snapshot", nullable = false, length = 20)
    private AmbitoControl ambitoSnapshot;
    @Column(name = "version_numero_snapshot", nullable = false) private Integer versionNumeroSnapshot;
    @Column(name = "producto_id_snapshot") private String productoIdSnapshot;
    @Column(name = "producto_nombre_snapshot", length = 255) private String productoNombreSnapshot;
    @Column(name = "categoria_id_snapshot") private Integer categoriaIdSnapshot;
    @Column(name = "categoria_nombre_snapshot", length = 255) private String categoriaNombreSnapshot;
    @Enumerated(EnumType.STRING) @Column(name = "tipo_orden_snapshot", nullable = false, length = 10)
    private TipoOrdenControl tipoOrdenSnapshot;
    @Enumerated(EnumType.STRING) @Column(name = "punto_aplicacion_snapshot", nullable = false, length = 30)
    private PuntoAplicacionControl puntoAplicacionSnapshot;
    @Column(name = "area_operativa_id_snapshot") private Integer areaOperativaIdSnapshot;
    @Column(name = "area_operativa_nombre_snapshot", length = 255) private String areaOperativaNombreSnapshot;
    @Column(name = "proceso_id_snapshot") private Integer procesoIdSnapshot;
    @Column(name = "proceso_nombre_snapshot", length = 255) private String procesoNombreSnapshot;
    @Column(name = "manufacturing_version_id_snapshot") private Long manufacturingVersionIdSnapshot;
    @Column(name = "ruta_version_id_snapshot") private Long rutaVersionIdSnapshot;
    @Column(name = "ruta_nodo_id_snapshot") private Long rutaNodoIdSnapshot;
    @Column(name = "orden_fabricacion_operacion_id_snapshot")
    private Long ordenFabricacionOperacionIdSnapshot;
    @Column(name = "frontend_node_id_snapshot", length = 255)
    private String frontendNodeIdSnapshot;
    @Column(name = "nodo_nombre_snapshot", length = 255)
    private String nodoNombreSnapshot;
    @Enumerated(EnumType.STRING) @Column(name = "momento_snapshot", nullable = false, length = 30)
    private MomentoControl momentoSnapshot;
    @Enumerated(EnumType.STRING) @Column(name = "punto_exigencia_snapshot", nullable = false, length = 30)
    private PuntoExigenciaControl puntoExigenciaSnapshot;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "legacy_ejecucion_id", unique = true)
    private ControlProcesoEjecucion legacyEjecucion;
    @OneToMany(mappedBy = "controlRequerido") @OrderBy("fechaRegistro DESC, id DESC")
    private List<EjecucionControl> ejecuciones = new ArrayList<>();
}
