package exotic.app.planta.model.users;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "tab_accesos")
public class TabAcceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "modulo_acceso_id", nullable = false)
    @JsonBackReference("moduloAccesoTabs")
    private ModuloAcceso moduloAcceso;

    @Column(name = "tab_id", nullable = false, length = 128)
    private String tabId;

    @Column(nullable = false)
    private int nivel;
}
