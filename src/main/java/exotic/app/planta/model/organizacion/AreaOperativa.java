package exotic.app.planta.model.organizacion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

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

    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "area_operativa_categoria_habilitada",
            joinColumns = @JoinColumn(name = "area_operativa_id"),
            inverseJoinColumns = @JoinColumn(name = "categoria_id")
    )
    private Set<Categoria> categoriasHabilitadas = new LinkedHashSet<>();
}
