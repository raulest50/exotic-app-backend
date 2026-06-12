package exotic.app.planta.model.compras;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import exotic.app.planta.model.empresa.EmpresaIdentidadLegalVersion;
import exotic.app.planta.model.empresa.EmpresaLogoDocumentalVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.persistence.*;
import exotic.app.planta.model.commons.divisas.Divisas.DIVISAS;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;


import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orden_compra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrdenCompraMateriales {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "orden_compra_id", unique = true, updatable = false, nullable = false)
    private int ordenCompraId;

    @CreationTimestamp
    private LocalDateTime fechaEmision;

    /**
     * Fecha en que la OCM fue marcada como enviada al proveedor.
     * <p>
     * Es un dato operacional del flujo de compras y sirve como inicio preferente
     * para metricas informativas de lead time. Si esta fecha no existe en OCMs
     * historicas, BI puede usar fechaEmision como fallback explicito.
     */
    @Column(name = "fecha_envio_proveedor")
    private LocalDateTime fechaEnvioProveedor;

    @ManyToOne
    @JoinColumn(name = "empresa_identidad_legal_version_id")
    private EmpresaIdentidadLegalVersion empresaIdentidadLegalVersion;

    @ManyToOne
    @JoinColumn(name = "empresa_logo_documental_version_id")
    private EmpresaLogoDocumentalVersion empresaLogoDocumentalVersion;

    private LocalDateTime fechaVencimiento;

    /**
     * Reference to the supplier (Proveedor) using the surrogate key.
     * This relationship uses the internal pk field rather than the business identifier
     * to maintain referential integrity even if the business ID changes.
     */
    @Valid
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "proveedor_pk", referencedColumnName = "pk", nullable = false)
    private Proveedor proveedor;

    @OneToMany(mappedBy = "ordenCompraMateriales", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<ItemOrdenCompra> itemsOrdenCompra;

    private long subTotal;
    private long ivaCOP;
    private long totalPagar;

    /**
     * 0: credito
     * 1: contado
     */
    private String condicionPago;

    private String tiempoEntrega;

    private int plazoPago;

    /**
     * -1: cancelada
     *  0: pendiente liberacion
     *  1: pendiente envio
     *  2: pendiente ingreso almacen
     *  3: cerrada con éxito
     */
    private int estado;

    /**
     * Plain column to store the FacturaCompra ID supplied by the provider.
     */
    private Integer facturaCompraId;

    private DIVISAS divisas;

    private double trm;

    @Column(name = "observaciones")
    private String observaciones;

    /**
     * Porcentaje estimado de materiales recibidos para esta orden de compra.
     * Este campo es calculado dinámicamente y no se persiste en la base de datos.
     * 
     * El porcentaje se calcula como: (Total cantidad recibida / Total cantidad ordenada) * 100
     * 
     * Valores posibles:
     * - 0.0: No se ha recibido nada
     * - 0.0 a 100.0: Porcentaje de recepción normal
     * - > 100.0: Se ha recibido más de lo ordenado (caso válido y aceptable)
     * - null: No se ha calculado aún (cuando la orden se obtiene de otros endpoints)
     * 
     * Este campo solo se calcula y asigna cuando se consultan OCMs pendientes a través
     * del endpoint /ingresos_almacen/ocms_pendientes_ingreso
     * 
     * @see IngresoAlmacenService#consultarOCMsPendientesRecepcion
     */
    @Transient
    private Double porcentajeRecibido;

    /**
     * Limite efectivo de recepciones parciales para esta OCM.
     * Calculado desde el limite operativo propio del proveedor y no persistido.
     */
    @Transient
    private Integer limiteRecepcionesParcialesEfectivo;

}
