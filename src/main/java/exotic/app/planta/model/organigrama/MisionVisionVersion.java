package exotic.app.planta.model.organigrama;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "mision_vision_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MisionVisionVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(name = "mision_html", nullable = false, columnDefinition = "TEXT")
    private String misionHtml;

    @Column(name = "vision_html", nullable = false, columnDefinition = "TEXT")
    private String visionHtml;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDateTime vigenteDesde;

    @Column(name = "vigente_hasta")
    private LocalDateTime vigenteHasta;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "creado_por", length = 120)
    private String creadoPor;

    @Column(name = "motivo_cambio", nullable = false, columnDefinition = "TEXT")
    private String motivoCambio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "origen_version_id",
            foreignKey = @ForeignKey(name = "fk_mision_vision_origen_version")
    )
    private MisionVisionVersion origenVersion;

    @OneToMany(mappedBy = "misionVisionVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<MisionVisionValor> valores = new ArrayList<>();

    public enum Estado {
        VIGENTE,
        RETIRADA
    }
}
