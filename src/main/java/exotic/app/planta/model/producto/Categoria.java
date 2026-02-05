package exotic.app.planta.model.producto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "categoria")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Categoria {
    @Id
    private int categoriaId;

    @Column(unique = true)
    private String categoriaNombre;

    private String categoriaDescripcion;

    /**
     * para asignarle a cada categoria un tamano de lote por defecto,
     * caracteristica importante pedida por el cliente para restringir
     * tamano de lote en la creacion manual de ordenes deproduccion.
     */
    @Column(nullable = true)
    @Getter(AccessLevel.NONE)
    private Integer loteSize;

    /**
     * @return the lote size assigned to the category, or 0 if it has not been set
     */
    public Integer getLoteSize() {
        return loteSize !=null ? loteSize : 0;
    }

}