package exotic.app.planta.model.produccion.fabricacion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "orden_fabricacion_operacion_dependencia")
@Getter
@Setter
@NoArgsConstructor
public class OrdenFabricacionOperacionDependencia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operacion_predecesora_id", nullable = false)
    private OrdenFabricacionOperacion predecesora;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operacion_sucesora_id", nullable = false)
    private OrdenFabricacionOperacion sucesora;
}
