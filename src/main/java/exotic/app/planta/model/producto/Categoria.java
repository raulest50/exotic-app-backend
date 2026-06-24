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

    @Column(name = "tiempo_dias_fabricacion", nullable = false)
    @Getter(AccessLevel.NONE)
    private Integer tiempoDiasFabricacion = 0;

    @Column(name = "capacidad_productiva_diaria", nullable = true)
    @Getter(AccessLevel.NONE)
    private Integer capacidadProductivaDiaria;

    /**
     * @return the lote size assigned to the category, or 0 if it has not been set
     */
    public Integer getLoteSize() {
        return loteSize !=null ? loteSize : 0;
    }

    /**
     * @return los dias de fabricacion configurados para la categoria, o 0 si no se han definido
     */
    public Integer getTiempoDiasFabricacion() {
        return tiempoDiasFabricacion != null ? tiempoDiasFabricacion : 0;
    }

    /**
     * @return la capacidad productiva diaria configurada para la categoria, o 0 si no se ha definido
     */
    public Integer getCapacidadProductivaDiaria() {
        return capacidadProductivaDiaria != null ? capacidadProductivaDiaria : 0;
    }

}
