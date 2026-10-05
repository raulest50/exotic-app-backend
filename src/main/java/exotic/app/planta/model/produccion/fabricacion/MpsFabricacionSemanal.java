package exotic.app.planta.model.produccion.fabricacion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Programa de propuestas. Guardarlo no emite ordenes de fabricacion. */
@Entity
@Table(name = "mps_fabricacion_semanal")
@Getter
@Setter
public class MpsFabricacionSemanal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "week_start_date", nullable = false, unique = true, updatable = false)
    private LocalDate weekStartDate;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
    @Column(name = "creado_por", nullable = false, updatable = false)
    private String creadoPor;
    @Column(name = "actualizado_por", nullable = false)
    private String actualizadoPor;

    @OneToMany(mappedBy = "mps", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("posicion ASC")
    private List<MpsFabricacionDetalle> detalles = new ArrayList<>();
}
