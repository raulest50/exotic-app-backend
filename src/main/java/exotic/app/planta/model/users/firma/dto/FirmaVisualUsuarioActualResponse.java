package exotic.app.planta.model.users.firma.dto;

public record FirmaVisualUsuarioActualResponse(
        Long usuarioId,
        boolean configurada,
        FirmaVisualUsuarioVersionResponse vigente
) {

    public static FirmaVisualUsuarioActualResponse sinConfigurar(Long usuarioId) {
        return new FirmaVisualUsuarioActualResponse(usuarioId, false, null);
    }

    public static FirmaVisualUsuarioActualResponse configurada(FirmaVisualUsuarioMetadata metadata) {
        return new FirmaVisualUsuarioActualResponse(
                metadata.getUsuarioId(),
                true,
                FirmaVisualUsuarioVersionResponse.from(metadata)
        );
    }
}
