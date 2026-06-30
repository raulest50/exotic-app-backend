package exotic.app.planta.model.empresa;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "jornada_laboral_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JornadaLaboralVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
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

    @OneToMany(mappedBy = "jornadaLaboralVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("diaSemana ASC, orden ASC")
    private List<JornadaLaboralBloque> bloques = new ArrayList<>();

    public enum Estado {
        VIGENTE,
        RETIRADA
    }
}
