package exotic.app.planta.model.master.configs;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class MasterDirective {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * nombre de la configuracion
     */
    @Column(unique = true)
    private String nombre;

    /**
     * una descripcion corta de que hace
     */
    private String resumen;

    /**
     * Valor almacenado como string para soportar cualquier tipo de dato.
     * Debe interpretarse según el tipo especificado en el campo 'tipoDato'.
     */
    private String valor;

    /**
     * Especifica el tipo de dato almacenado en el campo 'valor'.
     * Facilita la conversión y validación del valor almacenado.
     */
    @Enumerated(EnumType.STRING)
    private TipoDato tipoDato;


    @Enumerated(EnumType.STRING)
    private GRUPO grupo;

    /**
     * descripcion mas detallada de que hace esta configuracion.
     */
    private String ayuda;

    public enum GRUPO {
        FLEXIBILIDAD_CONTROL,
        COMPRAS_ALMACEN,
    }

    /**
     * Tipos de datos soportados por el sistema de directivas.
     */
    public enum TipoDato {
        TEXTO,
        NUMERO,
        DECIMAL,
        BOOLEANO,
        FECHA,
        JSON
    }

}
