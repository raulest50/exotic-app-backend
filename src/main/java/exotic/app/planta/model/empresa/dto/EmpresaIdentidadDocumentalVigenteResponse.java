package exotic.app.planta.model.empresa.dto;

import exotic.app.planta.model.empresa.EmpresaIdentidadLegalVersion;

public record EmpresaIdentidadDocumentalVigenteResponse(
        String revision,
        IdentidadLegal identidadLegal,
        Logo logo
) {

    public static EmpresaIdentidadDocumentalVigenteResponse from(
            EmpresaIdentidadLegalVersion identidad,
            EmpresaLogoDocumentalMetadata logo
    ) {
        return new EmpresaIdentidadDocumentalVigenteResponse(
                "identidad-" + identidad.getId() + "-logo-" + logo.getId(),
                new IdentidadLegal(
                        identidad.getId(),
                        identidad.getVersion(),
                        identidad.getRazonSocial(),
                        identidad.getNombreComercial(),
                        identidad.getTipoIdentificacion(),
                        identidad.getNumeroIdentificacion(),
                        identidad.getDigitoVerificacion(),
                        identidad.getTelefonoPrincipal(),
                        identidad.getEmailPrincipal()
                ),
                new Logo(
                        logo.getId(),
                        logo.getVersion(),
                        logo.getSha256(),
                        logo.getContentType(),
                        logo.getTamanoBytes(),
                        logo.getAnchoPx(),
                        logo.getAltoPx(),
                        "/api/empresa-logo-documental/versiones/" + logo.getId() + "/imagen"
                )
        );
    }

    public record IdentidadLegal(
            Long id,
            Integer version,
            String razonSocial,
            String nombreComercial,
            String tipoIdentificacion,
            String numeroIdentificacion,
            String digitoVerificacion,
            String telefonoPrincipal,
            String emailPrincipal
    ) {
    }

    public record Logo(
            Long id,
            Integer version,
            String sha256,
            String contentType,
            Long tamanoBytes,
            Integer anchoPx,
            Integer altoPx,
            String imagenUrl
    ) {
    }
}
