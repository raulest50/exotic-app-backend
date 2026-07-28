package exotic.app.planta.model.empresa.dto;

import exotic.app.planta.model.empresa.EmpresaLogoDocumentalVersion;

import java.time.LocalDateTime;

/**
 * Proyeccion liviana de una version de logo. Excluye deliberadamente el BYTEA
 * para que las consultas de metadatos e historial no carguen la imagen.
 */
public interface EmpresaLogoDocumentalMetadata {

    Long getId();

    Integer getVersion();

    EmpresaLogoDocumentalVersion.Estado getEstado();

    String getNombreArchivoOriginal();

    String getContentType();

    Long getTamanoBytes();

    Integer getAnchoPx();

    Integer getAltoPx();

    String getSha256();

    LocalDateTime getVigenteDesde();

    LocalDateTime getVigenteHasta();

    LocalDateTime getCreadoEn();

    String getCreadoPor();

    String getMotivoCambio();
}
