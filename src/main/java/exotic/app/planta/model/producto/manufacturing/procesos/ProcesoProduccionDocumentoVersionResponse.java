package exotic.app.planta.model.producto.manufacturing.procesos;

import java.time.LocalDateTime;

public record ProcesoProduccionDocumentoVersionResponse(
        Long id,
        Integer procesoId,
        Integer version,
        ProcesoProduccionDocumentoVersion.Estado estado,
        String nombreArchivoOriginal,
        String contentType,
        Long tamanoBytes,
        String sha256,
        LocalDateTime vigenteDesde,
        LocalDateTime vigenteHasta,
        LocalDateTime creadoEn,
        String creadoPor,
        String motivoCambio
) {

    public static ProcesoProduccionDocumentoVersionResponse from(
            ProcesoProduccionDocumentoVersion documento
    ) {
        return new ProcesoProduccionDocumentoVersionResponse(
                documento.getId(),
                documento.getProceso().getProcesoId(),
                documento.getVersion(),
                documento.getEstado(),
                documento.getNombreArchivoOriginal(),
                documento.getContentType(),
                documento.getTamanoBytes(),
                documento.getSha256(),
                documento.getVigenteDesde(),
                documento.getVigenteHasta(),
                documento.getCreadoEn(),
                documento.getCreadoPor(),
                documento.getMotivoCambio()
        );
    }
}
