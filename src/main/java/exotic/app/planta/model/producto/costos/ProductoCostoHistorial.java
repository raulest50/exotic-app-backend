package exotic.app.planta.model.producto.costos;

import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "producto_costo_historial", uniqueConstraints = @UniqueConstraint(columnNames = {"producto_id", "version"}))
@Getter
@Setter
@NoArgsConstructor
public class ProductoCostoHistorial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "producto_id", nullable = false, length = 255)
    private String productoId;

    @Column(name = "producto_nombre", length = 200)
    private String productoNombre;

    @Column(name = "tipo_producto", nullable = false, length = 1)
    private String tipoProducto;

    @Column(nullable = false)
    private long version;

    @Column(name = "costo_anterior", precision = 19, scale = 6)
    private BigDecimal costoAnterior;

    @Column(name = "costo_nuevo", nullable = false, precision = 19, scale = 6)
    private BigDecimal costoNuevo;

    @Column(name = "cambiado_en", nullable = false)
    private LocalDateTime cambiadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private User usuario;

    @Column(name = "usuario_username", nullable = false, length = 120)
    private String usuarioUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ProductoCostoOrigen origen;

    @Column(length = 500)
    private String motivo;

    @Column(length = 255)
    private String referencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carga_costos_lote_id")
    private CargaCostosLote cargaCostosLote;
}
