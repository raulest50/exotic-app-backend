package exotic.app.planta.dto;

import lombok.Data;

@Data
public class SearchAreaOperativaDTO {
    private String searchType;    // "NOMBRE", "RESPONSABLE", "ID"
    private String nombre;
    private Long responsableId;
    private Integer areaId;
}
