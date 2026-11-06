package exotic.app.planta.model.produccion.despeje;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "despeje_plantilla_item", uniqueConstraints = {
        @UniqueConstraint(name = "uq_despeje_item_orden", columnNames = {"plantilla_id", "orden"}),
        @UniqueConstraint(name = "uq_despeje_item_version", columnNames = {"id", "plantilla_id"})})
@Getter @Setter @NoArgsConstructor
public class ItemPlantillaDespeje {
    public enum Tipo { SI_NO_NA, NUMERO }
    public enum Valor { SI, NO, NA }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plantilla_id", nullable = false, updatable = false)
    private PlantillaDespejeVersion plantilla;
    @Column(nullable = false)
    private int orden;
    @Column(nullable = false, length = 1000)
    private String texto;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Tipo tipo;
    @Column(nullable = false)
    private boolean obligatorio = true;
    @Column(name = "permite_na", nullable = false)
    private boolean permiteNa;
    @Enumerated(EnumType.STRING) @Column(name = "respuesta_conforme", length = 2)
    private Valor respuestaConforme;
    @Column(precision = 24, scale = 8)
    private BigDecimal minimo;
    @Column(precision = 24, scale = 8)
    private BigDecimal maximo;
    @PrePersist @PreUpdate @PreRemove
    private void preservarPublicacion() {
        if (plantilla.getEstado() != PlantillaDespejeVersion.Estado.BORRADOR)
            throw new IllegalStateException("Los ítems publicados son inmutables.");
    }
}
