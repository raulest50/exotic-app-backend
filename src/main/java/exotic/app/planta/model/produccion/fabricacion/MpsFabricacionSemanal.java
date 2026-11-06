package exotic.app.planta.model.produccion.fabricacion;

import exotic.app.planta.model.produccion.EstadoMpsSemanal;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoMpsSemanal estado = EstadoMpsSemanal.BORRADOR;
    @Column(name = "revision_numero", nullable = false)
    private int revisionNumero = 1;
    @Column(name = "aprobado_por_username", length = 100)
    private String aprobadoPorUsername;
    @Column(name = "fecha_aprobacion")
    private LocalDateTime fechaAprobacion;
    @Column(name = "emitido_por_username", length = 100)
    private String emitidoPorUsername;
    @Column(name = "fecha_emision")
    private LocalDateTime fechaEmision;

    @OneToMany(mappedBy = "mps", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("posicion ASC")
    private List<MpsFabricacionDetalle> detalles = new ArrayList<>();
}
