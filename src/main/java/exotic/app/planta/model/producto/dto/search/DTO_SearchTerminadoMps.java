package exotic.app.planta.model.producto.dto.search;

import lombok.Data;

@Data
public class DTO_SearchTerminadoMps {

    private String searchTerm;
    private TipoBusqueda tipoBusqueda;
    private Integer categoriaId;
    private Integer page;
    private Integer size;

    public enum TipoBusqueda {
        ID,
        NOMBRE
    }
}
