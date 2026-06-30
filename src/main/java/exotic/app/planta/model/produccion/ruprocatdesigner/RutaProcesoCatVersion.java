package exotic.app.planta.model.produccion.ruprocatdesigner;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ruta_proceso_cat_version")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RutaProcesoCatVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruta_proceso_cat_id", nullable = false)
    private RutaProcesoCat rutaProcesoCat;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private Estado estado;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDateTime vigenteDesde;

    @Column(name = "vigente_hasta")
    private LocalDateTime vigenteHasta;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "creado_por", length = 120)
    private String creadoPor;

    @Column(name = "motivo_cambio", columnDefinition = "TEXT")
    private String motivoCambio;

    @OneToMany(mappedBy = "rutaProcesoCatVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<RutaProcesoNode> nodes = new ArrayList<>();

    @OneToMany(mappedBy = "rutaProcesoCatVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<RutaProcesoEdge> edges = new ArrayList<>();

    public enum Estado {
        VIGENTE,
        RETIRADA
    }
}
