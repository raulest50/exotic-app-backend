package exotic.app.planta.model.organizacion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.manufacturing.procesos.RecursoProduccion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OptimisticLock;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "area_operativa")
@Getter
@Setter
public class AreaOperativa {

    public enum AlcanceEjecucion {
        SOLO_OP, SOLO_OF, AMBOS
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int areaId;

    @Column(unique = true)
    private String nombre;

    private String descripcion;

    /** Al habilitarlo, el área representa una única línea física exclusiva. */
    @Column(name = "requiere_despeje", nullable = false)
    private boolean requiereDespeje;
    @Column(name = "requiere_revision_despeje", nullable = false)
    private boolean requiereRevisionDespeje;
    @Column(name = "despeje_linea_exclusiva_confirmada", nullable = false)
    private boolean despejeLineaExclusivaConfirmada;
    @Column(name = "despeje_revision_contexto", nullable = false)
    private long despejeRevisionContexto;
    @Column(name = "despeje_antecedente_conocido", nullable = false)
    private boolean despejeAntecedenteConocido;
    @JsonIgnore @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "despeje_ultimo_lote_id")
    private Lote despejeUltimoLote;
    @Column(name = "despeje_ultimo_inicio_en")
    private java.time.LocalDateTime despejeUltimoInicioEn;
    @JsonIgnore @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "despeje_ocupante_seguimiento_id")
    private SeguimientoOrdenArea despejeOcupanteSeguimiento;
    @JsonIgnore @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "despeje_ocupante_operacion_id")
    private OrdenFabricacionOperacion despejeOcupanteOperacion;
    @Version @Column(name = "despeje_version", nullable = false)
    private long despejeVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibilidad_mps", nullable = false, length = 20)
    private VisibilidadMps visibilidadMps = VisibilidadMps.SOLO_OP;

    @Enumerated(EnumType.STRING)
    @Column(name = "alcance_mps", nullable = false, length = 20)
    private AlcanceMps alcanceMps = AlcanceMps.TODOS;

    /** Tipos de orden que ejecuta el area; independiente de la consulta del MPS. */
    @Enumerated(EnumType.STRING)
    @Column(name = "alcance_ejecucion", nullable = false, length = 20)
    private AlcanceEjecucion alcanceEjecucion = AlcanceEjecucion.AMBOS;

    @ManyToOne
    @JoinColumn(name = "responsable_id")
    private User responsableArea;

    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "area_operativa_categoria_habilitada",
            joinColumns = @JoinColumn(name = "area_operativa_id"),
            inverseJoinColumns = @JoinColumn(name = "categoria_id")
    )
    private Set<Categoria> categoriasHabilitadas = new LinkedHashSet<>();

    /** Productos de salida que esta area manufactura, independiente de sus insumos y rutas. */
    @JsonIgnore
    @OptimisticLock(excluded = true)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "area_operativa_semiterminado_manufacturado",
            joinColumns = @JoinColumn(name = "area_operativa_id"),
            inverseJoinColumns = @JoinColumn(name = "producto_id"))
    private Set<SemiTerminado> semiterminadosManufacturados = new LinkedHashSet<>();

    /** Tipos de recursos utilizables; no asigna equipos fisicos ni reserva capacidad. */
    @JsonIgnore
    @OptimisticLock(excluded = true)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "area_operativa_recurso_produccion",
            joinColumns = @JoinColumn(name = "area_operativa_id"),
            inverseJoinColumns = @JoinColumn(name = "recurso_produccion_id"))
    private Set<RecursoProduccion> recursosProduccion = new LinkedHashSet<>();
}
