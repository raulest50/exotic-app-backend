package exotic.app.planta.model.producto.dto.search;

import lombok.Data;
import java.util.List;

@Data
public class ProductoSearchCriteria {
    private String search;
    private String searchType;
    private List<String> categories;
    private Integer page;
    private Integer size;

    // Constantes públicas estáticas para las categorías
    public static final String CATEGORIA_MATERIA_PRIMA = "materia prima";
    public static final String CATEGORIA_MATERIAL_EMPAQUE = "material empaque";
    public static final String CATEGORIA_SEMITERMINADO = "semiterminado";
    public static final String CATEGORIA_TERMINADO = "terminado";


}
