package exotic.app.planta.model.producto.costos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "receta_costos_revision")
@Getter
@NoArgsConstructor
public class RecetaCostosRevision {
    @Id
    private Short id;

    @Column(nullable = false)
    private long version;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
}
