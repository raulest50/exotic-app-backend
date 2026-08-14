package exotic.app.planta.model.users.firma.dto;

import exotic.app.planta.model.users.firma.FirmaVisualUsuarioVersion;

import java.time.LocalDateTime;

/** Proyección que evita cargar el BYTEA al consultar vigencia e historial. */
public interface FirmaVisualUsuarioMetadata {

    Long getId();

    Long getUsuarioId();

    Integer getVersion();

    FirmaVisualUsuarioVersion.Estado getEstado();

    String getNombreArchivoOriginal();

    String getContentType();

    Long getTamanoBytes();

    Integer getAnchoPx();

    Integer getAltoPx();

    String getSha256();

    LocalDateTime getVigenteDesde();

    LocalDateTime getVigenteHasta();

    LocalDateTime getCreadoEn();

    String getConfiguradaPorUsername();

    String getConfiguradaPorNombre();

    String getMotivoCambio();

    String getRetiradaPorUsername();

    String getRetiradaPorNombre();

    String getMotivoRetiro();
}
