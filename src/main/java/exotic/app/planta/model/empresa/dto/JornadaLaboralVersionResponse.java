package exotic.app.planta.model.empresa.dto;

import exotic.app.planta.model.empresa.JornadaLaboralVersion;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class JornadaLaboralVersionResponse {

    private Long id;
    private Integer version;
    private JornadaLaboralVersion.Estado estado;
    private LocalDateTime vigenteDesde;
    private LocalDateTime vigenteHasta;
    private LocalDateTime creadoEn;
    private String creadoPor;
    private String motivoCambio;
    private List<JornadaLaboralBloqueResponse> bloques;

    public static JornadaLaboralVersionResponse fromEntity(JornadaLaboralVersion version) {
        return JornadaLaboralVersionResponse.builder()
                .id(version.getId())
                .version(version.getVersion())
                .estado(version.getEstado())
                .vigenteDesde(version.getVigenteDesde())
                .vigenteHasta(version.getVigenteHasta())
                .creadoEn(version.getCreadoEn())
                .creadoPor(version.getCreadoPor())
                .motivoCambio(version.getMotivoCambio())
                .bloques(version.getBloques().stream()
                        .map(JornadaLaboralBloqueResponse::fromEntity)
                        .toList())
                .build();
    }
}
