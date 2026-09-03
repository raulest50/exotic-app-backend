package exotic.app.planta.model.controles;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "control_magnitud")
@Getter @Setter @NoArgsConstructor
public class MagnitudControl {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 40)
    private String codigo;
    @Column(nullable = false, length = 120)
    private String nombre;
    @Column(nullable = false, length = 30)
    private String simbolo;
    @Column(nullable = false, length = 80)
    private String dimension;
    @Column(nullable = false)
    private boolean activo = true;
    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;
}
