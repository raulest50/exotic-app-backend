package exotic.app.planta.model.controles;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "control_plan_aplicabilidad")
@Getter @Setter @NoArgsConstructor
public class AplicabilidadPlanControl {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private VersionPlanControl version;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "producto_id")
    private Producto producto;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "categoria_id")
    private Categoria categoria;
    @Enumerated(EnumType.STRING) @Column(name = "tipo_orden", nullable = false, length = 10)
    private TipoOrdenControl tipoOrden;
    @Enumerated(EnumType.STRING) @Column(name = "punto_aplicacion", nullable = false, length = 30)
    private PuntoAplicacionControl puntoAplicacion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "area_operativa_id")
    private AreaOperativa areaOperativa;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "proceso_id")
    private ProcesoProduccion proceso;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private MomentoControl momento;
    @Enumerated(EnumType.STRING) @Column(name = "punto_exigencia", nullable = false, length = 30)
    private PuntoExigenciaControl puntoExigencia;
    @Column(name = "legado_global", nullable = false)
    private boolean legadoGlobal;
    @ManyToMany
    @JoinTable(name = "control_plan_aplicabilidad_exclusion",
            joinColumns = @JoinColumn(name = "aplicabilidad_id"),
            inverseJoinColumns = @JoinColumn(name = "producto_id"))
    private Set<Producto> productosExcluidos = new LinkedHashSet<>();
}
