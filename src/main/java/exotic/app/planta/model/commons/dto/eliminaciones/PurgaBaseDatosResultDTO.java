package exotic.app.planta.model.commons.dto.eliminaciones;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurgaBaseDatosResultDTO {
    private boolean permitted;
    private boolean executed;
    private String message;
    private String environment;
    private int truncatedTablesCount;
    private List<String> truncatedTables = new ArrayList<>();
    private List<String> preservedTables = new ArrayList<>();
    private List<String> preservedUsers = new ArrayList<>();
}
