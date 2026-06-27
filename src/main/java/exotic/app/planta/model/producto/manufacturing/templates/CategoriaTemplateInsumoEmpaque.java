package exotic.app.planta.model.producto.manufacturing.templates;

import exotic.app.planta.model.producto.Material;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "categoria_template_insumo_empaque")
@Getter
@Setter
public class CategoriaTemplateInsumoEmpaque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "case_pack_id", nullable = false)
    private CategoriaTemplateCasePack casePack;

    @ManyToOne(optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "cantidad", nullable = false)
    private double cantidad;

    @Column(name = "uom", length = 12)
    private String uom;
}
