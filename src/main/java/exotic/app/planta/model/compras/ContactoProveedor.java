package exotic.app.planta.model.compras;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "contacto_proveedor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContactoProveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contacto_id", unique = true, updatable = false, nullable = false)
    private int contactoId;

    @ManyToOne
    @JoinColumn(name = "proveedor_pk", referencedColumnName = "pk")
    @JsonBackReference
    private Proveedor proveedor;

    private String fullName;
    private String cargo;
    private String cel;
    private String email;

}
