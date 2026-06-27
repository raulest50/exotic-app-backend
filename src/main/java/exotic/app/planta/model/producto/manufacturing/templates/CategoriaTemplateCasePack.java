package exotic.app.planta.model.producto.manufacturing.templates;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categoria_template_case_pack")
@Getter
@Setter
public class CategoriaTemplateCasePack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "template_id", unique = true, nullable = false)
    private CategoriaManufacturingTemplate template;

    @Column(name = "units_per_case", nullable = false)
    private Integer unitsPerCase;

    @Column(name = "ean14")
    private String ean14;

    @Column(name = "largo_cm")
    private Double largoCm;

    @Column(name = "ancho_cm")
    private Double anchoCm;

    @Column(name = "alto_cm")
    private Double altoCm;

    @Column(name = "gross_weight_kg")
    private Double grossWeightKg;

    @Column(name = "default_for_shipping")
    private Boolean defaultForShipping;

    @OneToMany(mappedBy = "casePack", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CategoriaTemplateInsumoEmpaque> insumosEmpaque = new ArrayList<>();
}
