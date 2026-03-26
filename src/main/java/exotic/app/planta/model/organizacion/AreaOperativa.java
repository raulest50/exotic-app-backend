package exotic.app.planta.model.organizacion;

import jakarta.persistence.*;
import exotic.app.planta.model.users.User;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "area_operativa")
@Getter
@Setter
public class AreaOperativa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int areaId;

    @Column(unique = true)
    private String nombre;

    private String descripcion;

    @ManyToOne
    @JoinColumn(name = "responsable_id")
    private User responsableArea;
    
}
