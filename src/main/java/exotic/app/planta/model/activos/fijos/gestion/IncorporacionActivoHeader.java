package exotic.app.planta.model.activos.fijos.gestion;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import exotic.app.planta.model.activos.fijos.compras.FacturaCompraActivo;
import exotic.app.planta.model.contabilidad.AsientoContable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Representa el encabezado de un documento de incorporación de activos.
 * Está relacionado con una factura de compra de activos.
 */
@Entity
@Table(name = "incorporacion_activo_header")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IncorporacionActivoHeader {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "incorporacion_id", unique = true, updatable = false, nullable = false)
    private Long incorporacionId;

    /**
     * Fecha de incorporación de los activos
     */
    @CreationTimestamp
    private LocalDateTime fechaIncorporacion;

    /**
     * Factura de compra relacionada con esta incorporación
     */
    @ManyToOne
    @JoinColumn(name = "factura_compra_activo_id")
    private FacturaCompraActivo facturaCompraActivo;

    /**
     * Responsable de la incorporación
     */
    @ManyToOne
    @JoinColumn(name = "responsable_id")
    private IntegrantePersonal responsable;

    /**
     * Observaciones sobre la incorporación
     */
    @Column(length = 500)
    private String observaciones;

    /**
     * Estado de la incorporación
     * 0: En proceso
     * 1: Completada
     * 2: Cancelada
     */
    private int estado;

    /**
     * Estado contable de la incorporación
     */
    @Enumerated(EnumType.STRING)
    private EstadoContable estadoContable = EstadoContable.PENDIENTE;

    /**
     * Referencia al asiento contable asociado a esta incorporación
     */
    @OneToOne
    @JoinColumn(name = "asiento_contable_id")
    private AsientoContable asientoContable;

    /**
     * Líneas de la incorporación (detalle de activos)
     */
    @OneToMany(mappedBy = "incorporacionHeader", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<IncorporacionActivoLine> lineasIncorporacion = new ArrayList<>();

    /**
     * Estados posibles para la contabilización de una incorporación
     */
    public enum EstadoContable {
        PENDIENTE,      // No ha sido contabilizada
        CONTABILIZADA,  // Ya tiene asiento contable
        NO_APLICA       // No requiere contabilización
    }
}
