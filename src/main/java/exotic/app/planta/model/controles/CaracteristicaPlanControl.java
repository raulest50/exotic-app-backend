package exotic.app.planta.model.controles;

import exotic.app.planta.model.calidad.ControlProcesoCaracteristica;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "control_plan_caracteristica", uniqueConstraints =
        @UniqueConstraint(name = "uq_control_caracteristica_orden", columnNames = {"version_id", "orden"}))
@Getter @Setter @NoArgsConstructor
public class CaracteristicaPlanControl {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private VersionPlanControl version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "magnitud_id", nullable = false)
    private MagnitudControl magnitud;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidad_id")
    private UnidadControl unidad;
    @Column(nullable = false, length = 120)
    private String nombre;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private TipoCaracteristicaControl tipo;
    @Column(nullable = false) private Integer orden;
    @Column(name = "cantidad_muestras", nullable = false) private Integer cantidadMuestras;
    @Column(name = "unidades_por_muestra", nullable = false) private Integer unidadesPorMuestra;
    @Column(name = "escala_visible", nullable = false) private Integer escalaVisible;
    @Column(precision = 20, scale = 8) private BigDecimal objetivo;
    @Column(name = "limite_inferior", precision = 20, scale = 8) private BigDecimal limiteInferior;
    @Column(name = "limite_superior", precision = 20, scale = 8) private BigDecimal limiteSuperior;
    @Column(name = "valor_booleano_esperado") private Boolean valorBooleanoEsperado;
    @Column(name = "magnitud_codigo_snapshot", nullable = false, length = 40) private String magnitudCodigoSnapshot;
    @Column(name = "magnitud_nombre_snapshot", nullable = false, length = 120) private String magnitudNombreSnapshot;
    @Column(name = "magnitud_simbolo_snapshot", nullable = false, length = 30) private String magnitudSimboloSnapshot;
    @Column(name = "unidad_codigo_snapshot", length = 40) private String unidadCodigoSnapshot;
    @Column(name = "unidad_nombre_snapshot", length = 120) private String unidadNombreSnapshot;
    @Column(name = "unidad_simbolo_snapshot", length = 30) private String unidadSimboloSnapshot;
    @Column(name = "legado_sin_limites", nullable = false) private boolean legadoSinLimites;
    @Column(name = "requiere_depuracion", nullable = false) private boolean requiereDepuracion;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "legacy_caracteristica_id", unique = true)
    private ControlProcesoCaracteristica legacyCaracteristica;
}
