package exotic.app.planta.model.organigrama.dto;

import exotic.app.planta.model.organigrama.MisionVisionVersion;

import java.time.LocalDateTime;

public record MisionVisionVersionSummaryResponse(
        Long id,
        Integer version,
        MisionVisionVersion.Estado estado,
        LocalDateTime vigenteDesde,
        LocalDateTime vigenteHasta,
        LocalDateTime creadoEn,
        String creadoPor,
        String motivoCambio,
        Integer origenVersion
) {
    public static MisionVisionVersionSummaryResponse fromEntity(MisionVisionVersion version) {
        return new MisionVisionVersionSummaryResponse(
                version.getId(),
                version.getVersion(),
                version.getEstado(),
                version.getVigenteDesde(),
                version.getVigenteHasta(),
                version.getCreadoEn(),
                version.getCreadoPor(),
                version.getMotivoCambio(),
                version.getOrigenVersion() != null ? version.getOrigenVersion().getVersion() : null
        );
    }
}
