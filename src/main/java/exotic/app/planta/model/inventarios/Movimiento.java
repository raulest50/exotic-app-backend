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


    public enum TipoMovimiento {
        // ═══════════════════════════════════════════════════
        // ENTRADAS
        // ═══════════════════════════════════════════════════

        COMPRA, // Ingreso de material por compra - asociado a Orden de Compra (OCM)

        BACKFLUSH, // Ingreso de producto terminado o semi-terminado al finalizar producción

        AJUSTE_POSITIVO, // Aumento de inventario por corrección


        // ═══════════════════════════════════════════════════
        // SALIDAS
        // ═══════════════════════════════════════════════════

        CONSUMO, // Consumo de material en proceso productivo - asociado a Orden de Producción (OP)

        VENTA, // Salida para venta de producto terminado al cliente

        DISPENSACION, // Salida de material para uso en producción o mantenimiento

        BAJA, // Salida definitiva de material (eliminación física) - típicamente desde AVERIAS

        AJUSTE_NEGATIVO, // Reducción de inventario por corrección o pérdida (shrinkage)


        // ═══════════════════════════════════════════════════
        // MOVIMIENTOS ESPECIALES
        // ═══════════════════════════════════════════════════
        /** Movimiento de material dañado, defectuoso o scrap hacia AVERIAS */
        AVERIA,

        /** Movimiento entre almacenes (OTA) */
        TRANSFERENCIA,

        /** Devolución de producto terminado por cliente hacia DEVOLUCIONES */
        DEVOLUCION_CLIENTE,

        /** Salida de Material a proveedor (aun falta determinar si desde almacen averias o almacen devoluciones) */
        DEVOLUCION_A_PROVEEDOR
    }


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
        tipoMovimiento = TipoMovimiento.DISPENSACION;
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
