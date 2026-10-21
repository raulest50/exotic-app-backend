package exotic.app.planta.model.producto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

/**
 * Catálogo mantenido mediante migraciones. Las referencias son kg, L y U.
 * U representa conteo: no establece una equivalencia con masa o volumen.
 */
@Entity
@Table(name = "unidad_medida_producto")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UnidadMedidaProducto {

    public enum Magnitud {
        MASA, VOLUMEN, CONTEO
    }

    @Id
    @Column(name = "codigo", nullable = false, updatable = false, length = 16)
    private String codigo;

    @Column(name = "simbolo", nullable = false, length = 16)
    private String simbolo;

    @Enumerated(EnumType.STRING)
    @Column(name = "magnitud", nullable = false, length = 16)
    private Magnitud magnitud;

    /** Cantidad en la unidad de referencia correspondiente a una unidad de este código. */
    @Column(name = "factor_a_referencia", nullable = false, precision = 24, scale = 12)
    private BigDecimal factorAReferencia;
}
