package exotic.app.planta.model.users;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "users") // 'user' can be a reserved keyword in some DBs
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private long cedula;

    @Column(unique = true) // username must be unique
    private String username;

    private String nombreCompleto;

    private String password;  // always stored as encoded (hashed) password

    private String email;

    // opcional
    private String cel;

    //opcional
    private String direccion;

    //opcional
    private LocalDate fechaNacimiento;

    /**
     * 1: activo
     * 2: inactivo
     */
    private int estado;

    @OneToMany(mappedBy = "user", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonManagedReference("userModuloAccesos")
    private Set<ModuloAcceso> moduloAccesos = new HashSet<>();

}
