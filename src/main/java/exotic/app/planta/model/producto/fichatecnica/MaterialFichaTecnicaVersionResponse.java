package exotic.app.planta.model.producto.fichatecnica;

import java.time.LocalDateTime;

public record MaterialFichaTecnicaVersionResponse(
        Long id,
        Integer version,
        MaterialFichaTecnicaVersion.Estado estado,
        String nombreArchivoOriginal,
        Long tamanoBytes,
        LocalDateTime vigenteDesde,
        LocalDateTime vigenteHasta,
        LocalDateTime creadoEn,
        String creadoPor,
        String motivoCambio,
        boolean disponible
) {

    public static MaterialFichaTecnicaVersionResponse from(
            MaterialFichaTecnicaVersion entity,
            boolean disponible,
            Long tamanoBytes
    ) {
        return new MaterialFichaTecnicaVersionResponse(
                entity.getId(),
                entity.getVersion(),
                entity.getEstado(),
                entity.getNombreArchivoOriginal(),
                tamanoBytes,
                entity.getVigenteDesde(),
                entity.getVigenteHasta(),
                entity.getCreadoEn(),
                entity.getCreadoPor(),
                entity.getMotivoCambio(),
                disponible
        );
    }
}
