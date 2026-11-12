package exotic.app.planta.model.produccion.despeje;

import exotic.app.planta.model.organizacion.AreaOperativa;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Identidad estable de un formulario; cada familia mantiene sus propias ediciones. */
@Entity
@Table(name = "despeje_plantilla", uniqueConstraints =
        @UniqueConstraint(name = "uq_despeje_familia_area", columnNames = {"id", "area_operativa_id"}))
@Getter @Setter @NoArgsConstructor
public class PlantillaDespeje {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_operativa_id", nullable = false, updatable = false)
    private AreaOperativa areaOperativa;
    @Column(nullable = false, length = 200, updatable = false)
    private String nombre;
    @Version @Column(nullable = false)
    private long version;
}
