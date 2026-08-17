package exotic.app.planta.model.inventarios;

import jakarta.persistence.*;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.UnidadTiempoVencimiento;
import lombok.*;

import java.time.LocalDate;

/**
 * Representa un lote (batch) de material o producto terminado,
 * con referencia opcional a orden de compra o de producción.
 */
@Entity
@Table(name = "lote")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Lote {

    /** PK interno */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Código único de lote (interno) */
    @Column(name = "batch_number", nullable = false, unique = true)
    private String batchNumber;

    /** Fecha de fabricación o recepción */
    @Column(name = "production_date")
    private LocalDate productionDate;

    /** Fecha de expiración, si aplica */
    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    /** Snapshot de la cantidad de vida util vigente cuando se creo el lote. */
    @Column(name = "vida_util_cantidad_aplicada")
    private Integer vidaUtilCantidadAplicada;

    /** Snapshot de la unidad de vida util vigente cuando se creo el lote. */
    @Enumerated(EnumType.STRING)
    @Column(name = "vida_util_unidad_aplicada", length = 10)
    private UnidadTiempoVencimiento vidaUtilUnidadAplicada;

    /**
     * Producto al que pertenece el lote. Se mantiene opcional para lotes
     * históricos cuya identidad todavía se deduce de sus movimientos u orden
     * de origen. Los lotes nuevos de manufactura deben asignarlo explícitamente.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_calidad", nullable = false, length = 30)
    private EstadoCalidadLote estadoCalidad = EstadoCalidadLote.SIN_CLASIFICAR;

    /**
     * Relación opcional con la orden de compra que origina este lote.
     * Sólo uno de los tres orígenes (compra, producción o fabricación) debe estar presente.
     */
    @ManyToOne
    @JoinColumn(name = "orden_compra_id")
    private OrdenCompraMateriales ordenCompraMateriales;

    /**
     * Relación opcional con la orden de producción que genera este lote de FG.
     * Sólo uno de los tres orígenes (compra, producción o fabricación) debe estar presente.
     */
    @ManyToOne
    @JoinColumn(name = "orden_produccion_id")
    private OrdenProduccion ordenProduccion;

    /** Orden de fabricación que produjo este lote intermedio o a granel. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_fabricacion_id")
    private OrdenFabricacion ordenFabricacion;

}
