package exotic.app.planta.model.produccion.batchrecord;

import exotic.app.planta.model.calidad.ControlProcesoEjecucion;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Expediente digital de la ejecución de un lote. Cada expediente corresponde
 * a una orden (de producción o de fabricación) y a un lote de resultado.
 * La orden describe lo autorizado; este agregado conserva lo ocurrido.
 */
@Entity
@Table(name = "batch_record")
@Getter
@Setter
@NoArgsConstructor
public class BatchRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String codigo;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_produccion_id", unique = true)
    private OrdenProduccion ordenProduccion;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_fabricacion_id", unique = true)
    private OrdenFabricacion ordenFabricacion;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_resultado_id", nullable = false, unique = true)
    private Lote loteResultado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_resultado_id", nullable = false)
    private Producto productoResultado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manufacturing_version_id", nullable = false)
    private ManufacturingVersions manufacturingVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoBatchRecord estado = EstadoBatchRecord.BORRADOR;

    /** Revisión funcional del expediente; no reemplaza el @Version técnico. */
    @Column(name = "revision_documental", nullable = false)
    private int revisionDocumental = 1;

    @Column(name = "cantidad_planificada", nullable = false, precision = 18, scale = 4)
    private BigDecimal cantidadPlanificada;

    @Column(name = "cantidad_obtenida", precision = 18, scale = 4)
    private BigDecimal cantidadObtenida;

    /** Unidad de medida congelada para el expediente y su representación PDF. */
    @Column(name = "unidad_medida", nullable = false, length = 20)
    private String unidadMedida;

    /**
     * Hash SHA-256 de la representación canónica vigente del expediente. Se
     * completa antes de una revisión o firma para detectar alteraciones.
     */
    @Column(name = "contenido_sha256", length = 64)
    private String contenidoSha256;

    /** Requerimientos normalizados y congelados al emitir la orden. */
    @Column(name = "requerimientos_materiales_json", columnDefinition = "TEXT")
    private String requerimientosMaterialesJson;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creado_por_id", nullable = false)
    private User creadoPor;

    @Column(name = "iniciado_en")
    private LocalDateTime iniciadoEn;

    @Column(name = "enviado_revision_en")
    private LocalDateTime enviadoRevisionEn;

    /** Número del último ciclo enviado a Calidad; cero mientras no exista envío. */
    @Column(name = "ciclo_revision_actual", nullable = false)
    private long cicloRevisionActual;

    @Column(name = "cerrado_en")
    private LocalDateTime cerradoEn;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @OneToMany(mappedBy = "batchRecord", fetch = FetchType.LAZY)
    @OrderBy("registradoEn ASC, id ASC")
    private List<BatchRecordConsumo> consumos = new ArrayList<>();

    @OneToMany(mappedBy = "batchRecord", fetch = FetchType.LAZY)
    @OrderBy("secuencia ASC, id ASC")
    private List<BatchRecordEtapa> etapas = new ArrayList<>();

    @OneToMany(mappedBy = "batchRecord", fetch = FetchType.LAZY)
    @OrderBy("fechaRegistro ASC, id ASC")
    private List<ControlProcesoEjecucion> controlesProceso = new ArrayList<>();

    @OneToMany(mappedBy = "batchRecord", fetch = FetchType.LAZY)
    @OrderBy("detectadaEn ASC, id ASC")
    private List<BatchRecordDesviacion> desviaciones = new ArrayList<>();

    @OneToMany(mappedBy = "batchRecord", fetch = FetchType.LAZY)
    @OrderBy("firmadoEn ASC, id ASC")
    private List<BatchRecordFirma> firmas = new ArrayList<>();

    @OneToMany(mappedBy = "batchRecord", fetch = FetchType.LAZY)
    @OrderBy("numero ASC")
    private List<BatchRecordRevision> revisiones = new ArrayList<>();

    @OneToMany(mappedBy = "batchRecord", fetch = FetchType.LAZY)
    @OrderBy("corregidaEn ASC, id ASC")
    private List<BatchRecordCorreccion> correcciones = new ArrayList<>();

    @OneToMany(mappedBy = "batchRecord", fetch = FetchType.LAZY)
    @OrderBy("decididaEn ASC, id ASC")
    private List<BatchRecordDecisionCalidad> decisionesCalidad = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @PrePersist
    @PreUpdate
    private void validarInvariantes() {
        if ((ordenProduccion == null) == (ordenFabricacion == null)) {
            throw new IllegalStateException(
                    "El batch record debe pertenecer exactamente a una orden de producción o fabricación.");
        }
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalStateException("El código del batch record es obligatorio.");
        }
        if (estado == null || creadoPor == null) {
            throw new IllegalStateException("El estado y el usuario creador del batch record son obligatorios.");
        }
        if (loteResultado == null || productoResultado == null || manufacturingVersion == null) {
            throw new IllegalStateException(
                    "El lote, producto y versión de manufactura son obligatorios en el batch record.");
        }
        if (!mismoProducto(productoResultado, manufacturingVersion.getProducto())) {
            throw new IllegalStateException(
                    "La versión de manufactura no corresponde al producto de resultado.");
        }
        if (!mismoProducto(productoResultado, loteResultado.getProducto())) {
            throw new IllegalStateException("El lote de resultado pertenece a otro producto.");
        }
        if (ordenProduccion != null) {
            if (!(productoResultado instanceof Terminado)) {
                throw new IllegalStateException(
                        "Una orden de producción con batch record debe generar un producto terminado.");
            }
            if (!mismoProducto(productoResultado, ordenProduccion.getProducto())) {
                throw new IllegalStateException("El producto no corresponde a la orden de producción.");
            }
            if (ordenProduccion.getManufacturingVersion() == null
                    || !mismaVersion(manufacturingVersion, ordenProduccion.getManufacturingVersion())) {
                throw new IllegalStateException(
                        "La versión de manufactura no corresponde a la fijada en la orden de producción.");
            }
            if (loteResultado.getOrdenProduccion() == null
                    || (loteResultado.getOrdenProduccion() != ordenProduccion
                    && loteResultado.getOrdenProduccion().getOrdenId() != ordenProduccion.getOrdenId())) {
                throw new IllegalStateException("El lote no corresponde a la orden de producción.");
            }
        }
        if (ordenFabricacion != null) {
            if (!mismoProducto(productoResultado, ordenFabricacion.getSemiTerminado())) {
                throw new IllegalStateException("El producto no corresponde a la orden de fabricación.");
            }
            if (!mismaVersion(manufacturingVersion, ordenFabricacion.getManufacturingVersion())) {
                throw new IllegalStateException(
                        "La versión de manufactura no corresponde a la fijada en la orden de fabricación.");
            }
            if (loteResultado.getOrdenFabricacion() == null
                    || !mismaOrdenFabricacion(
                    loteResultado.getOrdenFabricacion(), ordenFabricacion)) {
                throw new IllegalStateException("El lote no corresponde a la orden de fabricación.");
            }
        }
        if (cantidadPlanificada == null || cantidadPlanificada.signum() <= 0) {
            throw new IllegalStateException("La cantidad planificada debe ser mayor que cero.");
        }
        if (revisionDocumental <= 0 || unidadMedida == null || unidadMedida.isBlank()) {
            throw new IllegalStateException("La revisión y unidad de medida del expediente son obligatorias.");
        }
        if (cicloRevisionActual < 0) {
            throw new IllegalStateException("El número del ciclo de revisión no puede ser negativo.");
        }
        if (cantidadObtenida != null && cantidadObtenida.signum() < 0) {
            throw new IllegalStateException("La cantidad obtenida no puede ser negativa.");
        }
        if (contenidoSha256 != null && !contenidoSha256.matches("[0-9A-Fa-f]{64}")) {
            throw new IllegalStateException("El hash del expediente debe ser un SHA-256 válido.");
        }
        boolean sometidoRevision = estado == EstadoBatchRecord.APROBADO
                || estado == EstadoBatchRecord.RECHAZADO
                || estado == EstadoBatchRecord.CERRADO;
        if (sometidoRevision && (cantidadObtenida == null || contenidoSha256 == null)) {
            throw new IllegalStateException(
                    "Un expediente sometido a revisión requiere cantidad obtenida y hash de contenido.");
        }
        if (enviadoRevisionEn != null && iniciadoEn != null
                && enviadoRevisionEn.isBefore(iniciadoEn)) {
            throw new IllegalStateException("El expediente no puede enviarse a revisión antes de iniciarse.");
        }
        if (cerradoEn != null && enviadoRevisionEn != null
                && cerradoEn.isBefore(enviadoRevisionEn)) {
            throw new IllegalStateException("El expediente no puede cerrarse antes de enviarse a revisión.");
        }
        if (estado == EstadoBatchRecord.CERRADO && cerradoEn == null) {
            throw new IllegalStateException("Un expediente cerrado requiere fecha de cierre.");
        }
    }

    @PreRemove
    private void impedirEliminacion() {
        throw new IllegalStateException(
                "Un batch record no se elimina; debe conservarse con estado ANULADO.");
    }

    private boolean mismoProducto(Producto primero, Producto segundo) {
        return primero != null
                && segundo != null
                && primero.getProductoId() != null
                && primero.getProductoId().equals(segundo.getProductoId());
    }

    private boolean mismaVersion(ManufacturingVersions primera, ManufacturingVersions segunda) {
        return primera == segunda
                || (primera != null
                && segunda != null
                && primera.getId() != null
                && primera.getId().equals(segunda.getId()));
    }

    private boolean mismaOrdenFabricacion(OrdenFabricacion primera, OrdenFabricacion segunda) {
        return primera == segunda
                || (primera != null
                && segunda != null
                && primera.getOrdenFabricacionId() != null
                && primera.getOrdenFabricacionId().equals(segunda.getOrdenFabricacionId()));
    }
}
