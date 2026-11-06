package exotic.app.planta.model.produccion.despeje;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "despeje_respuesta", uniqueConstraints =
        @UniqueConstraint(name = "uq_despeje_respuesta_item", columnNames = {"ejecucion_id", "item_id"}))
@Getter @Setter @NoArgsConstructor
public class RespuestaDespeje {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ejecucion_id", nullable = false, updatable = false)
    private DespejeLineaEjecucion ejecucion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false, updatable = false)
    private ItemPlantillaDespeje item;
    /** Permite las FK compuestas que garantizan la misma versión de formulario. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plantilla_id", nullable = false, updatable = false)
    private PlantillaDespejeVersion plantilla;
    @Enumerated(EnumType.STRING) @Column(length = 2)
    private ItemPlantillaDespeje.Valor valor;
    @Column(precision = 24, scale = 8)
    private BigDecimal numero;
    @Column(columnDefinition = "TEXT")
    private String observacion;
    @PrePersist @PreUpdate @PreRemove
    private void validarBorrador() {
        if (ejecucion.getEstado() != DespejeLineaEjecucion.Estado.BORRADOR)
            throw new IllegalStateException("Las respuestas enviadas son inmutables.");
    }
}
