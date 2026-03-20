package exotic.app.planta.model.notificaciones;

import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MaestraNotificacion {

    @Id
    private int id;

    private String nombre;

    private String descripcion;

    /**
     * conjunto de usuarios que recibiran la notificacion.
     * hay que sacar los correos de aca.
     */
    @ManyToMany
    @JoinTable(
            name = "maestra_notificacion_users",
            joinColumns = @JoinColumn(name = "notificacion_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> usersGroup = new ArrayList<>();




}
