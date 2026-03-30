package exotic.app.planta.model.users.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAssignmentStatusDTO {
    @JsonProperty("isAreaResponsable")
    private boolean areaResponsable;
    private Integer areaResponsableId;
    private String areaResponsableNombre;
    @JsonProperty("hasModuloAccesos")
    private boolean hasModuloAccesos;
    @JsonProperty("canReceiveModuloAccesos")
    private boolean canReceiveModuloAccesos;
    @JsonProperty("canBeAreaResponsable")
    private boolean canBeAreaResponsable;

    public boolean hasModuloAccesos() {
        return hasModuloAccesos;
    }

    public boolean canReceiveModuloAccesos() {
        return canReceiveModuloAccesos;
    }

    public boolean canBeAreaResponsable() {
        return canBeAreaResponsable;
    }
}
