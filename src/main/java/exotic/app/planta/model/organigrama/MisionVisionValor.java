package exotic.app.planta.model.organigrama;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "mision_vision_valor",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mision_vision_valor_version_orden",
                columnNames = {"mision_vision_version_id", "orden"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MisionVisionValor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "mision_vision_version_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_mision_vision_valor_version")
    )
    private MisionVisionVersion misionVisionVersion;

    @Column(nullable = false)
    private Integer orden;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(name = "descripcion_html", nullable = false, columnDefinition = "TEXT")
    private String descripcionHtml;
}
