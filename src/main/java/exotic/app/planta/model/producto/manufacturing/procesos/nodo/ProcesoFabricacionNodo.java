package exotic.app.planta.model.producto.manufacturing.procesos.nodo;

import com.fasterxml.jackson.annotation.JsonBackReference;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionCompleto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "proceso_fabricacion_nodo")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo_nodo", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
public abstract class ProcesoFabricacionNodo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "frontend_id", nullable = false)
    private String frontendId;

    @Column(name = "posicion_x", nullable = false)
    private double posicionX;

    @Column(name = "posicion_y", nullable = false)
    private double posicionY;

    private String label;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_completo_id", nullable = false)
    @JsonBackReference("proceso-completo-nodes")
    private ProcesoProduccionCompleto procesoProduccionCompleto;
}
