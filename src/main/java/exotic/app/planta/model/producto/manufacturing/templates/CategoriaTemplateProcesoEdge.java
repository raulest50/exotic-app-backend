package exotic.app.planta.model.producto.manufacturing.templates;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "categoria_template_proceso_edge")
@Getter
@Setter
public class CategoriaTemplateProcesoEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private CategoriaManufacturingTemplate template;

    @Column(name = "frontend_id", nullable = false)
    private String frontendId;

    @Column(name = "source_frontend_id", nullable = false)
    private String sourceFrontendId;

    @Column(name = "target_frontend_id", nullable = false)
    private String targetFrontendId;
}
