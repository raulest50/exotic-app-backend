package exotic.app.planta.model.producto.costos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(
        name = "carga_costos_propagacion_item",
        uniqueConstraints = @UniqueConstraint(columnNames = {"lote_id", "producto_id"}))
@Getter
@Setter
@NoArgsConstructor
public class CargaCostosPropagacionItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false)
    private CargaCostosLote lote;

    @Column(name = "producto_id", nullable = false, length = 255)
    private String productoId;

    @Column(name = "producto_nombre", length = 200)
    private String productoNombre;

    @Column(name = "tipo_producto", nullable = false, length = 1)
    private String tipoProducto;

    @Column(nullable = false)
    private int nivel;

    @Column(name = "costo_anterior", nullable = false, precision = 19, scale = 6)
    private BigDecimal costoAnterior;

    @Column(name = "costo_nuevo", nullable = false, precision = 19, scale = 6)
    private BigDecimal costoNuevo;

    @Column(name = "costo_version_anterior", nullable = false)
    private long costoVersionAnterior;
}
