package exotic.app.planta.model.produccion.fabricacion;

import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import exotic.app.planta.model.produccion.EstadoDispensacionMateriales;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.PoliticaDispensacionInicio;
import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Orden independiente para fabricar un semiterminado que debe recibir lote
 * intermedio. La orden fija la versión de manufactura antes de su liberación;
 * el detalle de lo realmente ejecutado pertenece al BatchRecord.
 */
@Entity
@Table(name = "orden_fabricacion")
@Getter
@Setter
@NoArgsConstructor
public class OrdenFabricacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "orden_fabricacion_id")
    private Long ordenFabricacionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semiterminado_id", nullable = false)
    private SemiTerminado semiTerminado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manufacturing_version_id", nullable = false)
    private ManufacturingVersions manufacturingVersion;

    /** OP que genero automaticamente esta OF. Nula para ordenes independientes. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_produccion_origen_id")
    private OrdenProduccion ordenProduccionOrigen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoOrdenFabricacion estado = EstadoOrdenFabricacion.BORRADOR;

    @Column(name = "cantidad_planificada", nullable = false, precision = 18, scale = 4)
    private BigDecimal cantidadPlanificada;

    /** Unidad de medida congelada al crear la orden. */
    @Column(name = "unidad_medida", nullable = false, length = 20)
    private String unidadMedida;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_lanzamiento")
    private LocalDateTime fechaLanzamiento;

    /** Instante real en que la orden quedo disponible para ejecucion. */
    @Column(name = "liberada_en")
    private LocalDateTime liberadaEn;

    @Column(name = "fecha_final_planificada")
    private LocalDateTime fechaFinalPlanificada;

    @Column(name = "fecha_inicio")
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_final")
    private LocalDateTime fechaFinal;

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_dispensacion_inicio", nullable = false, length = 30)
    private PoliticaDispensacionInicio politicaDispensacionInicio = PoliticaDispensacionInicio.BLOQUEANTE;

    @Column(name = "fecha_aplicacion_politica_dispensacion")
    private LocalDateTime fechaAplicacionPoliticaDispensacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_dispensacion_materiales", nullable = false, length = 40)
    private EstadoDispensacionMateriales estadoDispensacionMateriales = EstadoDispensacionMateriales.PENDIENTE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creada_por_id", nullable = false)
    private User creadaPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsable_id")
    private User responsable;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    private void validarCreacion() {
        if (semiTerminado == null || !semiTerminado.isRequiereOrdenFabricacion()) {
            throw new IllegalStateException(
                    "La orden de fabricación requiere un semiterminado configurado para orden separada.");
        }
        validarInvariantes();
    }

    @PreUpdate
    private void validarActualizacion() {
        validarInvariantes();
    }

    private void validarInvariantes() {
        if (semiTerminado == null || estado == null || creadaPor == null
                || politicaDispensacionInicio == null || estadoDispensacionMateriales == null) {
            throw new IllegalStateException(
                    "El semiterminado, estado y usuario creador de la orden son obligatorios.");
        }
        if (manufacturingVersion == null
                || manufacturingVersion.getProducto() == null
                || !semiTerminado.getProductoId().equals(
                        manufacturingVersion.getProducto().getProductoId())) {
            throw new IllegalStateException(
                    "La versión de manufactura no corresponde al semiterminado de la orden.");
        }
        if (cantidadPlanificada == null || cantidadPlanificada.signum() <= 0) {
            throw new IllegalStateException("La cantidad planificada debe ser mayor que cero.");
        }
        if (unidadMedida == null || unidadMedida.isBlank()) {
            throw new IllegalStateException("La unidad de medida es obligatoria.");
        }
        if (fechaFinalPlanificada != null && fechaLanzamiento != null
                && fechaFinalPlanificada.isBefore(fechaLanzamiento)) {
            throw new IllegalStateException(
                    "La fecha final planificada no puede ser anterior al lanzamiento.");
        }
        if (fechaFinal != null && fechaInicio != null && fechaFinal.isBefore(fechaInicio)) {
            throw new IllegalStateException("La fabricación no puede terminar antes de iniciar.");
        }
    }
}
