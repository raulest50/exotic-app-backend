package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO seguro con los campos del Terminado necesarios para el wizard de ingreso al almacén.
 * Usa solo escalares y un DTO anidado para la categoría, evitando proxies lazy de Hibernate.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TerminadoInfoDTO {

    private String productoId;
    private String nombre;
    private String tipoUnidades;
    private Double cantidadUnidad;
    private String fotoUrl;
    private String prefijoLote;
    private double costo;
    private double ivaPercentual;
    private int status;
    private String observaciones;

    /** Categoría del producto terminado; null si no tiene categoría asignada. */
    private CategoriaInfoDTO categoria;

    /**
     * DTO anidado con los datos de la Categoría necesarios para el wizard.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoriaInfoDTO {
        private int categoriaId;
        private String categoriaNombre;
        private String categoriaDescripcion;
        private int loteSize;
    }
}
