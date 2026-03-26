package exotic.app.planta.model.activos.fijos.gestion;

import jakarta.persistence.*;
import exotic.app.planta.model.activos.fijos.ActivoFijo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import exotic.app.planta.model.contabilidad.AsientoContable;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Representa un documento para dar de baja a un activoFijo.
 */
@Entity
@Table(name = "documento_baja_activo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentoBajaActivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "baja_id", unique = true, updatable = false, nullable = false)
    private Long bajaId;

    /**
     * ActivoFijo que se da de baja
     */
    @ManyToOne
    @JoinColumn(name = "activo_id", nullable = false)
    private ActivoFijo activoFijo;

    /**
     * Fecha de la baja
     */
    @CreationTimestamp
    private LocalDateTime fechaBaja;

    /**
     * Motivo de la baja
     * 1: Obsolescencia
     * 2: Daño irreparable
     * 3: Venta
     * 4: Donación
     * 5: Otro
     */
    private int motivoBaja;

    /**
     * Descripción detallada del motivo de baja
     */
    @Column(length = 500)
    private String descripcionMotivo;

    /**
     * Valor contable al momento de la baja
     */
    private BigDecimal valorContableBaja;

    /**
     * Método de disposición
     * 1: Destrucción
     * 2: Venta
     * 3: Donación
     * 4: Otro
     */
    private int metodoDisposicion;

    /**
     * Descripción del método de disposición
     */
    @Column(length = 500)
    private String descripcionDisposicion;

    /**
     * Responsable que autoriza la baja
     */
    @ManyToOne
    @JoinColumn(name = "responsable_id", nullable = false)
    private IntegrantePersonal responsable;

    /**
     * Asiento contable generado por la baja
     */
    @OneToOne
    @JoinColumn(name = "asiento_contable_id")
    private AsientoContable asientoContable;

    /**
     * Estado del documento de baja
     * 0: Pendiente aprobación
     * 1: Aprobado
     * 2: Rechazado
     */
    private int estado;

    /**
     * Observaciones adicionales
     */
    @Column(length = 500)
    private String observaciones;
}