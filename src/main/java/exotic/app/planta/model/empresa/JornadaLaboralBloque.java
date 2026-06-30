package exotic.app.planta.model.empresa;

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

import java.time.LocalTime;

@Entity
@Table(
        name = "jornada_laboral_bloque",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_jornada_laboral_bloque_dia_orden",
                        columnNames = {"jornada_laboral_version_id", "dia_semana", "orden"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JornadaLaboralBloque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "jornada_laboral_version_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_jornada_laboral_bloque_version")
    )
    private JornadaLaboralVersion jornadaLaboralVersion;

    @Column(name = "dia_semana", nullable = false)
    private Integer diaSemana;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;
}
