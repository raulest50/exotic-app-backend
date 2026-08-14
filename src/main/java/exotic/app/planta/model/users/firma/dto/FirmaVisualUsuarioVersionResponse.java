package exotic.app.planta.model.users.firma.dto;

import exotic.app.planta.model.users.firma.FirmaVisualUsuarioVersion;

import java.time.LocalDateTime;

public record FirmaVisualUsuarioVersionResponse(
        Long id,
        Long usuarioId,
        Integer version,
        FirmaVisualUsuarioVersion.Estado estado,
        String nombreArchivoOriginal,
        String contentType,
        Long tamanoBytes,
        Integer anchoPx,
        Integer altoPx,
        String sha256,
        LocalDateTime vigenteDesde,
        LocalDateTime vigenteHasta,
        LocalDateTime creadoEn,
        String configuradaPorUsername,
        String configuradaPorNombre,
        String motivoCambio,
        String retiradaPorUsername,
        String retiradaPorNombre,
        String motivoRetiro
) {

    public static FirmaVisualUsuarioVersionResponse from(FirmaVisualUsuarioMetadata metadata) {
        return new FirmaVisualUsuarioVersionResponse(
                metadata.getId(),
                metadata.getUsuarioId(),
                metadata.getVersion(),
                metadata.getEstado(),
                metadata.getNombreArchivoOriginal(),
                metadata.getContentType(),
                metadata.getTamanoBytes(),
                metadata.getAnchoPx(),
                metadata.getAltoPx(),
                metadata.getSha256(),
                metadata.getVigenteDesde(),
                metadata.getVigenteHasta(),
                metadata.getCreadoEn(),
                metadata.getConfiguradaPorUsername(),
                metadata.getConfiguradaPorNombre(),
                metadata.getMotivoCambio(),
                metadata.getRetiradaPorUsername(),
                metadata.getRetiradaPorNombre(),
                metadata.getMotivoRetiro()
        );
    }

    public static FirmaVisualUsuarioVersionResponse from(FirmaVisualUsuarioVersion version) {
        return new FirmaVisualUsuarioVersionResponse(
                version.getId(),
                version.getTitular().getId(),
                version.getVersion(),
                version.getEstado(),
                version.getNombreArchivoOriginal(),
                version.getContentType(),
                version.getTamanoBytes(),
                version.getAnchoPx(),
                version.getAltoPx(),
                version.getSha256(),
                version.getVigenteDesde(),
                version.getVigenteHasta(),
                version.getCreadoEn(),
                version.getConfiguradaPorUsername(),
                version.getConfiguradaPorNombre(),
                version.getMotivoCambio(),
                version.getRetiradaPorUsername(),
                version.getRetiradaPorNombre(),
                version.getMotivoRetiro()
        );
    }
}
