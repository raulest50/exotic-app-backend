package exotic.app.planta.model.organizacion.personal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import exotic.app.planta.model.users.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    private String nombreContactoEmergencia;

    private String celularContactoEmergencia;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EstadoCivil estadoCivil;

    private Integer numeroHijos;

    private LocalDate fechaIngreso;

    private String numeroCuentaBancaria;

    private String banco;

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

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "idIntegrante", orphanRemoval = true)
    @JsonIgnore
    private List<DocTranDePersonal> documentos = new ArrayList<>();

    public enum Departamento {
        PRODUCCION,
        ADMINISTRATIVO,
    }

    public enum Estado {
        ACTIVO,
        INACTIVO,
    }

    public enum EstadoCivil {
        SOLTERO,
        CASADO,
        UNION_LIBRE,
        SEPARADO,
        DIVORCIADO,
        VIUDO,
    }

}
