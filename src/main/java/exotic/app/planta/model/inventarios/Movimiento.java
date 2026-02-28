package exotic.app.planta.model.inventarios;


import com.fasterxml.jackson.annotation.JsonBackReference;
import exotic.app.planta.model.producto.manufacturing.procesos.AreaProduccion;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import exotic.app.planta.model.producto.manufacturing.receta.Insumo;
import exotic.app.planta.model.compras.ItemOrdenCompra;
import exotic.app.planta.model.producto.Producto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Representa un movimiento individual de inventario para un producto específico en un almacén.
 *
 */
@Entity
@Table(name = "movimientos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movimiento_id", unique = true, updatable = false, nullable = false)
    private int movimientoId;

    // puede ser positivo o negativo
    private double cantidad;

    @ManyToOne
    @JoinColumn(name = "transaccion_id")
    @JsonBackReference
    private TransaccionAlmacen transaccionAlmacen;

    // aplica para los 3 tipos de productos
    @NotNull
    @ManyToOne
    @JoinColumn(name = "producto_id")
    private Producto producto;

    /**
     * Tipo de movimiento que describe la causa o naturaleza del movimiento de inventario.
     *
     * @see TipoMovimiento
     */
    private TipoMovimiento tipoMovimiento;

    /**
     * Almacén donde se realiza este movimiento específico.
     *
     * <p><strong>Nota importante:</strong> Este campo es individual por movimiento. Una misma
     * {@link TransaccionAlmacen} puede contener varios movimientos, cada uno con un almacén diferente.
     * Por ejemplo, en una transferencia entre almacenes:
     * <ul>
     *   <li>Movimiento 1: cantidad = -10, almacen = {@code GENERAL} (salida)</li>
     *   <li>Movimiento 2: cantidad = +10, almacen = {@code AVERIAS} (entrada)</li>
     * </ul>
     *
     * @see Almacen
     * @see TransaccionAlmacen
     */
    private Almacen almacen;

    /** Lote asociado a este movimiento */
    @ManyToOne
    @JoinColumn(name = "lote_id")
    private Lote lote;


    /**
     * creado para movimientos relacionados con averias, para reportar de que area operativa
     * se reporta la averia.
     */
    @ManyToOne
    @JoinColumn(name = "area_produccion_id")
    private AreaProduccion areaProduccion;

    @CreationTimestamp
    private LocalDateTime fechaMovimiento;

    /**
     * Tipos de movimientos de inventario según su causa o naturaleza.
     *
     * <p>Define la razón por la cual se está realizando el movimiento de inventario,
     * lo cual es importante para reportes, auditorías y análisis de flujo de materiales.
     *
     * <h3>Convención de uso con almacenes:</h3>
     * <ul>
     *   <li>{@link #COMPRA}: Típicamente ingreso a {@code GENERAL}</li>
     *   <li>{@link #CONSUMO}: Típicamente salida de {@code GENERAL} para producción</li>
     *   <li>{@link #BACKFLUSH}: Típicamente ingreso a {@code GENERAL} de producto terminado</li>
     *   <li>{@link #PERDIDA}: Típicamente ingreso a {@code AVERIAS}</li>
     *   <li>{@link #BAJA}: Típicamente salida de {@code AVERIAS} para eliminación definitiva</li>
     *   <li>{@link #VENTA}: Típicamente salida de {@code GENERAL} para despacho</li>
     * </ul>
     *
     * @see TransaccionAlmacen.TipoEntidadCausante
     */
    public enum TipoMovimiento {
        /** Ingreso de material por compra - asociado a Orden de Compra (OCM) */
        COMPRA,

        /** Salida definitiva de material (eliminación física) - típicamente desde AVERIAS */
        BAJA,

        /** Consumo de material en proceso productivo - asociado a Orden de Producción (OP) */
        CONSUMO,

        /** Ingreso de producto terminado o semi-terminado al finalizar producción */
        BACKFLUSH,

        /** Salida para venta de producto terminado al cliente */
        VENTA,

        /** Ingreso a almacén de averías por daño, defecto o pérdida */
        PERDIDA
    }

    /**
     * Tipos de almacenes disponibles en el sistema para gestionar diferentes categorías de inventario.
     *
     * <p>Cada almacén tiene un propósito específico en el flujo de materiales de la planta.
     * Los productos pueden moverse entre almacenes mediante transacciones de tipo
     * {@link TransaccionAlmacen.TipoEntidadCausante#OTA OTA} (Orden de Transferencia de Almacén).
     *
     * <h3>Flujos Típicos:</h3>
     * <ul>
     *   <li><strong>Ingreso de compras:</strong> Proveedor → {@code GENERAL}</li>
     *   <li><strong>Producción normal:</strong> Consumo desde {@code GENERAL} → Producto a {@code GENERAL}</li>
     *   <li><strong>Producto defectuoso:</strong> {@code GENERAL} → {@code AVERIAS}</li>
     *   <li><strong>Control de calidad:</strong> {@code GENERAL} → {@code CALIDAD} → {@code GENERAL} o {@code AVERIAS}</li>
     *   <li><strong>Devolución de cliente:</strong> Cliente → {@code DEVOLUCIONES} → {@code GENERAL} o {@code AVERIAS}</li>
     *   <li><strong>Scrap de producción:</strong> Producción → {@code AVERIAS} directamente</li>
     * </ul>
     *
     * <p><strong>Nota:</strong> Una {@link TransaccionAlmacen} puede contener movimientos a
     * diferentes almacenes. Ver
     * <a href="../../../../../docs/adr/001-transaccion-almacen-multiples-ubicaciones.md">
     * ADR 001
     * </a> para más detalles.
     *
     * @see TransaccionAlmacen.TipoEntidadCausante#OTA
     * @see Movimiento#almacen
     */
    public enum Almacen {
        /**
         * Almacén principal donde se reciben compras, se dispensa material para producción,
         * y se ingresa el producto terminado (backflush).
         */
        GENERAL,

        /**
         * Almacén de productos con averías, daños o defectos.
         * Incluye scrap de órdenes de producción y productos dañados por eventos fortuitos.
         */
        AVERIAS,

        /**
         * Almacén temporal para productos en control de calidad.
         * Los productos aquí están retenidos hasta aprobación o rechazo.
         */
        CALIDAD,

        /**
         * Almacén para productos terminados devueltos por clientes o materiales
         * para devolverle al proveedor.
         */
        DEVOLUCIONES
    }

    /**
     * Constructor para se usado preferiblemente solo por
     * @param insumo
     */
    public Movimiento(Insumo insumo){
        cantidad = insumo.getCantidadRequerida();
        producto = insumo.getProducto();
        tipoMovimiento = TipoMovimiento.CONSUMO;
    }

    /**
     * Constructor para ser usado preferiblemente solo por el Constructor DocumentoIngreso(OrdenCompraMateriales).
     * usarlo en otras clases solo en casos donde realmente sea muy necesario o beneficioso.
     * @param item
     */
    Movimiento(ItemOrdenCompra item){
        this.cantidad = item.getCantidad();
        this.producto = item.getMaterial();
        this.tipoMovimiento = TipoMovimiento.COMPRA;
    }


}
