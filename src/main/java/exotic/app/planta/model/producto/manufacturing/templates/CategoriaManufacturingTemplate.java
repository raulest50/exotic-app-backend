package exotic.app.planta.model.producto.manufacturing.templates;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.producto.Categoria;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categoria_manufacturing_template")
@Getter
@Setter
public class CategoriaManufacturingTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "categoria_id", unique = true, nullable = false)
    private Categoria categoria;

    @Column(name = "rendimiento_teorico", nullable = false)
    private double rendimientoTeorico;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC, id ASC")
    private List<CategoriaTemplateInsumo> insumos = new ArrayList<>();

    @OneToOne(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private CategoriaTemplateCasePack casePack;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CategoriaTemplateProcesoNode> nodes = new ArrayList<>();

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CategoriaTemplateProcesoEdge> edges = new ArrayList<>();

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;

    @PrePersist
    protected void onCreate() {
        fechaCreacion = AppTime.now();
        fechaModificacion = AppTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        fechaModificacion = AppTime.now();
    }
}
