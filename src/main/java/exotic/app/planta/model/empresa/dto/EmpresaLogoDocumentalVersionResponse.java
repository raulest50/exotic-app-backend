package exotic.app.planta.model.empresa.dto;

import exotic.app.planta.model.empresa.EmpresaLogoDocumentalVersion;

import java.time.LocalDateTime;

public record EmpresaLogoDocumentalVersionResponse(
        Long id,
        Integer version,
        EmpresaLogoDocumentalVersion.Estado estado,
        String nombreArchivoOriginal,
        String contentType,
        Long tamanoBytes,
        Integer anchoPx,
        Integer altoPx,
        String sha256,
        LocalDateTime vigenteDesde,
        LocalDateTime vigenteHasta,
        LocalDateTime creadoEn,
        String creadoPor,
        String motivoCambio
) {

    public static EmpresaLogoDocumentalVersionResponse from(EmpresaLogoDocumentalMetadata metadata) {
        return new EmpresaLogoDocumentalVersionResponse(
                metadata.getId(),
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
                metadata.getCreadoPor(),
                metadata.getMotivoCambio()
        );
    }

    public static EmpresaLogoDocumentalVersionResponse from(EmpresaLogoDocumentalVersion version) {
        return new EmpresaLogoDocumentalVersionResponse(
                version.getId(),
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
                version.getCreadoPor(),
                version.getMotivoCambio()
        );
    }
}
