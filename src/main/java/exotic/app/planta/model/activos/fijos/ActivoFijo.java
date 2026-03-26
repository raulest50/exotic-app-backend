package exotic.app.planta.model.activos.fijos;

import exotic.app.planta.model.activos.fijos.gestion.*;
import jakarta.persistence.*;
import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import exotic.app.planta.model.producto.manufacturing.procesos.RecursoProduccion;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonBackReference;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "activo")
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ActivoFijo {

    // Enum para representar los tipos de activos
    public enum TipoActivo {
        PRODUCCION,
        MOBILIARIO,
        EQUIPO
    }

    // Enum para unidades de capacidad (movido desde ActivoProduccion)
    public enum UnidadesCapacidad {
        L, // litros
        KG, // kilogramo
        TON, // tonelada
        M3, // metro cubico
        W, // potencia watts
        HP, // horse power
    }

    // llave primaria
    @Id
    private String id;

    @Column(nullable = false)
    private String nombre;

    /**
     * diferente de proveedor. ej:
     * un proveedor puede vender laptop Dell pero no es el fabricante
     */
    private String brand;

    /**
     * URL a la ficha técnica del activo
     */
    private String url;

    /**
     * 0: activo
     * 1: obsoleto
     * 2: dado de baja
     */
    private int estado;

    private LocalDateTime fechaCodificacion;
    private LocalDateTime fechaBaja;

    // *** ATRIBUTOS PARA MODELAMIENTO CONTABLE - DEPRECIACION, PATRIMONIO, ETC.
    private BigDecimal valorAdquisicion;
    private BigDecimal valorResidual;
    private Integer vidaUtilMeses;
    private String metodoDespreciacion; // "LINEAL", "SUMA_DIGITOS", etc.
    @OneToMany(mappedBy = "activoFijo", cascade = CascadeType.ALL)
    private List<DepreciacionActivo> depreciaciones;


    // Campo para identificar el tipo de activo
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoActivo tipoActivo;

    // *** ATRIBUTOS ESPECIFICOS PARA ACTIVOS PRODUCCION
    private UnidadesCapacidad unidadesCapacidad;
    private Double capacidad;

    @Column(name = "ubicacion")
    private String ubicacion;

    // En la clase ActivoFijo
    @ManyToOne
    @JoinColumn(name = "tipo_recurso_id")
    @JsonBackReference(value = "recurso-activo")
    private RecursoProduccion tipoRecurso;

    /**
     * para modelar la disponibilidad del activo de produccion
     * 0: libre y listo para ser usado en un ProcesoProduccion
     * 1: ocupado por un proceso de produccion
     * -2: fuera de operacion, requiere mantenimiento
     * 1: en mantenimiento
     */
    private int estadoOpercional;

    // *** ATRIBUTOS PARA GESTION DOCUMENTAL
    @OneToMany(mappedBy = "activoFijo", cascade = CascadeType.ALL)
    private List<TrasladoActivo> traslados;

    @OneToMany(mappedBy = "activoFijo", cascade = CascadeType.ALL)
    private List<MantenimientoActivo> mantenimientos;

    @OneToOne(mappedBy = "activoFijo", cascade = CascadeType.ALL)
    @JsonBackReference(value = "activo-incorporacion")
    private IncorporacionActivoLine lineaIncorporacion;

    @OneToMany(mappedBy = "activoFijo", cascade = CascadeType.ALL)
    private List<DocumentoBajaActivo> documentosBaja;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsable_id")
    private IntegrantePersonal responsable;


    // *** ATRIBUTOS EXPERIMENTALES
    private Double tiempoCicloEstandar; // Tiempo estándar para procesar una unidad
    private Double tiempoSetupEstandar; // Tiempo estándar para configuración
    private Double eficienciaOperativa; // Porcentaje de eficiencia (0-100)
    private Double disponibilidadPorTurno; // Horas disponibles por turno (ej: 7.5)


}
