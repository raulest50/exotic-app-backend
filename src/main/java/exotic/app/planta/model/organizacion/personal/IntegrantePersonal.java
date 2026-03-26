package exotic.app.planta.model.organizacion.personal;

import jakarta.persistence.*;
import exotic.app.planta.model.users.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "integrante_personal")
public class IntegrantePersonal {

    /**
     * se deberia usar la cedula
     */
    @Id
    @Column(nullable = false)
    private long id;

    @Column(nullable = false)
    private String nombres;

    @Column(nullable = false)
    private String apellidos;

    @Column(nullable = false)
    private String celular;

    @Column(nullable = false)
    private String direccion;

    private String email;

    private String cargo;

    private Departamento departamento;

    private String centroDeCosto;

    private String centroDeProduccion;

    /**
     * en COP, se usa para el centro de costos
     */
    private int salario;

    private Estado estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private User usuario;

    /*@OneToMany(cascade = CascadeType.ALL, mappedBy = "idIntegrante")
    private List<DocTranDePersonal> documentos;*/

    public enum Departamento {
        PRODUCCION,
        ADMINISTRATIVO,
    }

    public enum Estado {
        ACTIVO,
        INACTIVO,
    }

}
