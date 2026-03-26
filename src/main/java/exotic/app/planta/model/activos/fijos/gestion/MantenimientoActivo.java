package exotic.app.planta.model.activos.fijos.gestion;

import jakarta.persistence.*;
import exotic.app.planta.model.activos.fijos.ActivoFijo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registro de mantenimientos realizados o programados para un activoFijo.
 */
@Entity
@Table(name = "mantenimiento_activo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MantenimientoActivo {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "activo_id", nullable = false)
    private ActivoFijo activoFijo;
    
    /**
     * Fecha y hora del mantenimiento
     */
    @Column(nullable = false)
    private LocalDateTime fechaMantenimiento;
    
    /**
     * Tipo de mantenimiento
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMantenimiento tipo;
    
    /**
     * Descripción del mantenimiento realizado
     */
    @Column(nullable = false, length = 500)
    private String descripcion;
    
    /**
     * Costo del mantenimiento
     */
    private BigDecimal costo;
    
    /**
     * Responsable del mantenimiento
     */
    @ManyToOne
    @JoinColumn(name = "responsable_id", nullable = false)
    private IntegrantePersonal responsable;
    
    /**
     * Estado del mantenimiento
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoMantenimiento estado;
    
    /**
     * Observaciones adicionales
     */
    @Column(length = 500)
    private String observaciones;
    
    /**
     * Tipos de mantenimiento
     */
    public enum TipoMantenimiento {
        PREVENTIVO,
        CORRECTIVO,
        PREDICTIVO
    }
    
    /**
     * Estados posibles de un mantenimiento
     */
    public enum EstadoMantenimiento {
        PROGRAMADO,
        EN_PROCESO,
        COMPLETADO,
        CANCELADO
    }
}