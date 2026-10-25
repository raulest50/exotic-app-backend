package exotic.app.planta.model.producto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.AllArgsConstructor;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
@Table(name="productos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo_producto", discriminatorType = DiscriminatorType.STRING)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "tipo_producto"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = Material.class, name = "M"),
        @JsonSubTypes.Type(value = SemiTerminado.class, name = "S"),
        @JsonSubTypes.Type(value = Terminado.class, name = "T")
})
public abstract class Producto {

    @Id
    //@GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "producto_id", unique = true, updatable = false, nullable = false)
    private String productoId;

    @Column(length = 200)
    private String nombre;

    private String observaciones;

    @DecimalMin(value = "0.0", inclusive = true, message = "El costo no puede ser negativo")
    @Digits(integer = 13, fraction = 6, message = "El costo excede la precisión permitida")
    @Column(nullable = false, precision = 19, scale = 6)
    @Setter(AccessLevel.NONE)
    @JsonProperty(access = JsonProperty.Access.READ_WRITE)
    private BigDecimal costo = BigDecimal.ZERO;

    @Column(name = "costo_version", nullable = false)
    @JsonIgnore
    @Setter(AccessLevel.NONE)
    private long costoVersion = 0L;

    /**
     * Permite asignar el costo antes del primer guardado. Los productos ya
     * versionados solo pueden cambiar de costo mediante ProductoCostoService.
     */
    public void asignarCostoInicial(BigDecimal costoInicial) {
        if (costoVersion != 0L) {
            throw new IllegalStateException("El costo de un producto versionado no puede asignarse directamente");
        }
        this.costo = costoInicial == null ? BigDecimal.ZERO : costoInicial;
    }

    /**
     * valores vigentes: 0%, 5% y 19%
     */
    private double ivaPercentual;

    @CreationTimestamp
    private LocalDateTime fechaCreacion;

    /**
     * Unidad del producto en inventario (KG, G, L, ML o U).
     * Para terminados y semiterminados también expresa cantidadSalidaReceta:
     * la salida de fabricación y el inventario comparten una única unidad.
     * Los insumos pueden declarar cantidades en sus propias unidades compatibles.
     */
    @Column(name = "tipo_unidades", length = 4)
    private String tipoUnidades;

    /**
     * En Process Designer v2, contenido por envase solo para terminados en U;
     * se expresa en unidadContenidoPorEnvase y no convierte la salida de receta.
     * En las demás recetas explícitas vale 0 (no aplica). Se conserva en Producto
     * por compatibilidad con los usos históricos de materiales y del diseñador v1.
     */
    @Min(value=0, message = "La Cantidad por unidad no puede ser negativa") // Cantidad por unidad
    private double cantidadUnidad;

    /** Unidad del contenido por envase de un terminado en U; null cuando no aplica en v2. */
    @Column(length = 16)
    private String unidadContenidoPorEnvase;

    public String getTipo_producto() {
        if (this instanceof Material) {
            return "M";
        } else if (this instanceof SemiTerminado) {
            return "S";
        } else if (this instanceof Terminado) {
            return "T";
        } else {
            return "Unknown";
        }
    }

    private double stockMinimo;

    /**
     * Se agrego para modelar insumos como el agua, el cual
     * no se guarda en almacen y por tanto no se inventarea.
     *
     * Tambien estaba pensando en usar este atributo tambien para
     * los semiterminados, ya que estos no se retornan al almacen.
     *
     * true: si pasa por almacen.
     * false: no pasa por alamacen
     * 
     * IMPORTANTE: Al ser un campo boolean primitivo, Lombok genera el método
     * isInventareable() en lugar de getInventareable(). Siempre use
     * isInventareable() para acceder a esta propiedad.
     *
    */
    @Column
    private boolean inventareable = true;

    /**
     * Prefijo opcional usado para generar lotes internos.
     * En terminados identifica lotes de producción; en materiales identifica lotes internos de recepción.
     */
    @Column(name = "prefijo_lote", unique = true, nullable = true, length = 20)
    private String prefijoLote;

}
