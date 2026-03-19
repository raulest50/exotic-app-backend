package exotic.app.planta.model.compras;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


/**
 * Entity representing a supplier (Proveedor) in the system.
 * <p>
 * This entity uses a surrogate key pattern where:
 * - The primary key (pk) is an auto-incremented Long used for internal database relationships
 * - The business identifier (id) is a unique String (NIT) used for business operations and API interactions
 * <p>
 * This design separates internal database concerns from business logic, allowing:
 * - Correction of business IDs without breaking database relationships
 * - Consistent API interfaces that use business identifiers
 * - Improved data integrity and maintainability
 */
@Entity
@Table(name = "proveedores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Proveedor {

    /**
     * Internal surrogate primary key.
     * <p>
     * This auto-incremented value is used for all database relationships instead of the business ID.
     * It should never be exposed in APIs or to end users.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk", updatable = false, nullable = false)
    private Long pk;

    /**
     * Business identifier (NIT or identification number).
     * <p>
     * This is the natural key used for business operations and API interactions.
     * While unique, it is not used as the primary key to allow for corrections
     * without breaking database relationships.
     */
    @Column(name = "id", unique = true, updatable = true, nullable = false)
    private String id;

    /**
     * 0: cedula de ciudadania
     * 1: nit
     */
    private int tipoIdentificacion;

    private String nombre;
    private String direccion;

    /**
     * 0: Regimen comun
     * 1: Regimen simplificado
     * 2: Regimen especial
     */
    private int regimenTributario;

    private String ciudad;
    private String departamento;

    @OneToMany(mappedBy = "proveedor", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<ContactoProveedor> contactos = new ArrayList<>();

    private String url;
    private String observacion;

    @CreationTimestamp
    private LocalDateTime fechaRegistro;

    /**
     * 0: credito
     * 1: contado
     * condicion de pago is persisted both in orden compra and proveedor. this
     * way it is possible to change condicion de pago in time and also keeo a history
     * of condiciones de pago over time in case it is changed.
     */
    private String condicionPago;

    /**
     * categorias:
     * 0: Servicios Operativos
     * 1: Materias Primas
     * 2: Materiales de Empaque
     * 3: Servicios Administrativos
     * 4: Equipos y Otros Servicios
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "categorias")
    private int[] categorias;

    private String rutUrl;
    private String camaraUrl;

}
