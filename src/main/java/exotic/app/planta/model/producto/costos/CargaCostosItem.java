package exotic.app.planta.model.producto.costos;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "carga_costos_item", uniqueConstraints = @UniqueConstraint(columnNames = {"lote_id", "producto_id"}))
@Getter
@Setter
@NoArgsConstructor
public class CargaCostosItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false)
    private CargaCostosLote lote;

    @Column(name = "fila_excel", nullable = false)
    private int filaExcel;

    @Column(name = "producto_id", nullable = false, length = 255)
    private String productoId;

    @Column(name = "producto_nombre", length = 200)
    private String productoNombre;

    @Column(name = "tipo_producto", nullable = false, length = 1)
    private String tipoProducto;

    @Column(name = "descripcion_excel", length = 500)
    private String descripcionExcel;

    @Column(name = "descripcion_coincide", nullable = false)
    private boolean descripcionCoincide;

    @Column(name = "costo_anterior", nullable = false, precision = 19, scale = 6)
    private BigDecimal costoAnterior;

    @Column(name = "costo_nuevo", nullable = false, precision = 19, scale = 6)
    private BigDecimal costoNuevo;

    @Column(name = "costo_version_anterior", nullable = false)
    private long costoVersionAnterior;
}
