package exotic.app.planta.model.organigrama.dto;

import exotic.app.planta.model.organigrama.MisionVisionVersion;

import java.time.LocalDateTime;
import java.util.List;

public record MisionVisionVersionResponse(
        Long id,
        Integer version,
        MisionVisionVersion.Estado estado,
        String misionHtml,
        String visionHtml,
        List<MisionVisionValorResponse> valores,
        LocalDateTime vigenteDesde,
        LocalDateTime vigenteHasta,
        LocalDateTime creadoEn,
        String creadoPor,
        String motivoCambio,
        Integer origenVersion
) {
    public static MisionVisionVersionResponse fromEntity(MisionVisionVersion version) {
        return new MisionVisionVersionResponse(
                version.getId(),
                version.getVersion(),
                version.getEstado(),
                version.getMisionHtml(),
                version.getVisionHtml(),
                version.getValores().stream()
                        .map(MisionVisionValorResponse::fromEntity)
                        .toList(),
                version.getVigenteDesde(),
                version.getVigenteHasta(),
                version.getCreadoEn(),
                version.getCreadoPor(),
                version.getMotivoCambio(),
                version.getOrigenVersion() != null ? version.getOrigenVersion().getVersion() : null
        );
    }
}
