package exotic.app.planta.model.produccion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "area_operativa_ruido_muestra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AreaOperativaRuidoMuestra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_operativa_id", nullable = false)
    private AreaOperativa areaOperativa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario;

    @Column(name = "fecha_muestra", nullable = false)
    private LocalDateTime fechaMuestra;

    @Column(name = "ruido_db", nullable = false)
    private Double ruidoDb;

    @Column(name = "rms", nullable = false)
    private Double rms;

    @Column(name = "duracion_ms", nullable = false)
    private Integer duracionMs;

    @Column(name = "sample_rate", nullable = false)
    private Integer sampleRate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
