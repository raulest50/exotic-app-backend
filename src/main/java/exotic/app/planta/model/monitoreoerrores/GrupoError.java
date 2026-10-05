package exotic.app.planta.model.monitoreoerrores;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Agrupa eventos de la misma firma; los modulos afectados se obtienen de sus eventos. */
@Entity
@Table(name = "monitoreo_grupo_error", uniqueConstraints = @UniqueConstraint(
        name = "uq_monitoreo_grupo_error_firma", columnNames = "firma"))
@Getter
@Setter
@NoArgsConstructor
public class GrupoError {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** SHA-256 del tipo de excepcion y su clase/metodo de origen, sin datos variables. */
    @Column(nullable = false, length = 64, updatable = false)
    private String firma;

    @Column(nullable = false, length = 500)
    private String titulo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoGrupoError estado = EstadoGrupoError.ABIERTO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SeveridadError severidad = SeveridadError.MEDIA;

    /** Acumulado historico; una futura purga de eventos no debe borrar este resumen. */
    @Column(name = "total_eventos", nullable = false)
    private long totalEventos;

    @Column(name = "primera_aparicion", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant primeraAparicion;

    @Column(name = "ultima_aparicion", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant ultimaAparicion;

    @Column(name = "version_correccion", length = 120)
    private String versionCorreccion;

    @Version
    @Column(nullable = false)
    private long version;
}
