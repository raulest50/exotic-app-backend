package exotic.app.planta.model.activos.fijos.compras;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import exotic.app.planta.model.commons.divisas.Divisas.DIVISAS;
import exotic.app.planta.model.empresa.EmpresaIdentidadLegalVersion;
import exotic.app.planta.model.empresa.EmpresaLogoDocumentalVersion;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import exotic.app.planta.model.compras.Proveedor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Representa una orden de compra para activos fijos.
 */
@Entity
@Table(name = "orden_compra_activo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrdenCompraActivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "orden_compra_activo_id", unique = true, updatable = false, nullable = false)
    private int ordenCompraActivoId;

    @CreationTimestamp
    private LocalDateTime fechaEmision;

    private LocalDateTime fechaVencimiento;

    /**
     * Reference to the supplier (Proveedor) using the surrogate key.
     * This relationship uses the internal pk field rather than the business identifier
     * to maintain referential integrity even if the business ID changes.
     */
    @ManyToOne
    @JoinColumn(name = "proveedor_pk", referencedColumnName = "pk")
    private Proveedor proveedor;

    private double subTotal;
    private double iva;
    private double totalPagar;

    /**
     * 0: credito
     * 1: contado
     */
    private String condicionPago;

    private String tiempoEntrega;

    private int plazoPago;

    /**
     * la cotizacion principal, la que se selecciono para hacer la compra
     */
    private String cotizacionUrl;


    /**
     * -1: cancelada
     *  0: pendiente liberacion
     *  1: pendiente envio
     *  2: pendiente recepcion
     *  3: cerrada con éxito
     */
    private int estado;

    /**
     * Snapshot de la identidad documental usada al enviar la OCA al proveedor.
     * Ambas asociaciones se fijan juntas durante la transición 1 -> 2.
     */
    @ManyToOne
    @JoinColumn(name = "empresa_identidad_legal_version_id")
    private EmpresaIdentidadLegalVersion empresaIdentidadLegalVersion;

    @ManyToOne
    @JoinColumn(name = "empresa_logo_documental_version_id")
    private EmpresaLogoDocumentalVersion empresaLogoDocumentalVersion;

    /**
     * para soportar ordenes de compra en dolares.
     */
    private DIVISAS divisa;

    private double trm;

    /**
     * Referencia a la factura de compra asociada a esta orden
     */
    private Integer facturaCompraActivoId;

    /**
     * Lista de ítems de la orden de compra
     */
    @OneToMany(mappedBy = "ordenCompraActivo", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<ItemOrdenCompraActivo> itemsOrdenCompra = new ArrayList<>();


}
