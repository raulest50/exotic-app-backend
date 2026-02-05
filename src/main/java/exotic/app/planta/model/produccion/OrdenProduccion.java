package exotic.app.planta.model.produccion;


import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionCompleto;
import exotic.app.planta.model.ventas.Vendedor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "ordenes_produccion")
@Getter
@Setter
@NoArgsConstructor
public class OrdenProduccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "orden_id", unique = true, updatable = false, nullable = false)
    private int ordenId;

    @ManyToOne
    @JoinColumn(name = "producto_id")
    private Producto producto;

    // Agregar a OrdenProduccion
    @OneToOne
    @JoinColumn(name = "proceso_completo_id")
    @com.fasterxml.jackson.annotation.JsonManagedReference(value = "orden-proceso")
    private ProcesoProduccionCompleto procesoProduccionCompleto;


    //@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    //@JoinColumn(name = "orden_prod_id")
    @OneToMany(mappedBy = "ordenProduccion", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<OrdenSeguimiento> ordenesSeguimiento = new ArrayList<>();

    /**
     * Estados de la orden de producción:
     * 0: ABIERTA - Orden creada, sin dispensaciones
     * 11: PRIMERA_DISPENSACION - Se realizó la primera dispensación de materiales
     * 12: SEGUNDA_DISPENSACION - Se realizó la segunda dispensación de materiales
     * 13: TERCERA_DISPENSACION - Se realizó la tercera dispensación de materiales
     * ... (continuar según necesidad: 14, 15, etc.)
     * 2: TERMINADA - Orden finalizada exitosamente (se devolvió mercancía a almacén)
     * -1: CANCELADA - Orden cancelada
     */
    private int estadoOrden;

    private String observaciones;

    /**
     * Cantidad total planificada a producir. Puede incluir fracciones y nunca debe ser
     * inferior a una unidad para garantizar lotes viables.
     */
    @Column(name = "cantidad_producir", nullable = false)
    private double cantidadProducir = 1.0; // Valor por defecto: 1.0 (al menos una unidad)

    /**
     * instante en el que se crea la orden de produccion en el sistema.
     * debe ser asignada automaticamente por el backend
     */
    @CreationTimestamp
    private LocalDateTime fechaCreacion;

    /**
     * Instante planificado para autorizar el inicio de la orden. No puede ubicarse en el
     * pasado y facilita la coordinación con ventas para asegurar recursos y prioridades.
     */
    @Column(name = "fecha_lanzamiento")
    private LocalDateTime fechaLanzamiento;

    /**
     * Momento objetivo en el que la orden debería estar finalizada según el plan maestro.
     * Facilita el seguimiento de compromisos con clientes y la detección temprana de desvíos.
     */
    @Column(name = "fecha_final_planificada")
    private LocalDateTime fechaFinalPlanificada;

    /**
     * instante en el que realmente se inicia, o almenos que se reporta
     */
    private LocalDateTime fechaInicio;

    /**
     * instante en el que realmente se termina, o almenos que se reporta
     */
    private LocalDateTime fechaFinal;

    /**
     * Número de pedido comercial que origina la orden. Permite trazar el vínculo con ventas
     * y responder consultas comerciales sobre la fabricación de cada compromiso.
     */
    @Column(name = "numero_pedido_comercial")
    private String numeroPedidoComercial;

    /**
     * Área operativa encargada de ejecutar la orden. Favorece la coordinación entre equipos
     * cuando existen múltiples células de producción.
     */
    @Column(name = "area_operativa")
    private String areaOperativa;

    /**
     * Departamento operativo responsable de supervisar la orden. Permite asignar
     * responsabilidades y escalar desvíos a la gerencia adecuada.
     */
    @Column(name = "departamento_operativo")
    private String departamentoOperativo;

    @ManyToOne
    @JoinColumn(name = "responsable_id")
    private Vendedor vendedorResponsable;

    @OneToOne
    @JoinColumn(name = "planificacion_id")
    @JsonManagedReference(value = "orden-planificacion")
    private PlanificacionProduccion planificacionProduccion;

    /**
     * Número de lote asignado a esta orden (redundante con Lote.batchNumber para consultas rápidas).
     */
    @Column(name = "lote_asignado", length = 50)
    private String loteAsignado;

    public OrdenProduccion(Producto producto, String observaciones, double cantidadProducir) {
        this.producto = producto;
        this.observaciones = observaciones;
        this.estadoOrden = 0;
        setCantidadProducir(cantidadProducir);
        this.ordenesSeguimiento = new ArrayList<>();
    }

    // Mantener constructor anterior para compatibilidad
    public OrdenProduccion(Producto producto, String observaciones) {
        this(producto, observaciones, 1);
    }

    public double getCantidadProducir() {
        return cantidadProducir;
    }

    public void setCantidadProducir(double cantidadProducir) {
        this.cantidadProducir = cantidadProducir >= 1.0 ? cantidadProducir : 1.0;
    }

}
