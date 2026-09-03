package exotic.app.planta.model.controles;

import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "control_plan")
@Getter @Setter @NoArgsConstructor
public class PlanControl {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 60, updatable = false)
    private String codigo;
    @Column(nullable = false, length = 160, updatable = false)
    private String nombre;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AmbitoControl ambito;
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_id", updatable = false)
    private User creadoPor;
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL)
    @OrderBy("numero DESC")
    private List<VersionPlanControl> versiones = new ArrayList<>();
}
