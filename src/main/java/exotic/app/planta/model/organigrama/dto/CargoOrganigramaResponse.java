package exotic.app.planta.model.organigrama.dto;

import exotic.app.planta.model.organigrama.Cargo;

public record CargoOrganigramaResponse(
        String idCargo,
        String tituloCargo,
        String descripcionCargo,
        String departamento,
        String usuario,
        double posicionX,
        double posicionY,
        int nivel,
        String urlDocManualFunciones
) {
    public static CargoOrganigramaResponse fromEntity(Cargo cargo) {
        return new CargoOrganigramaResponse(
                cargo.getIdCargo(),
                cargo.getTituloCargo(),
                cargo.getDescripcionCargo(),
                cargo.getDepartamento(),
                cargo.getUsuario() != null ? cargo.getUsuario().getUsername() : null,
                cargo.getPosicionX(),
                cargo.getPosicionY(),
                cargo.getNivel(),
                publicManualLocation(cargo.getUrlDocManualFunciones())
        );
    }

    private static String publicManualLocation(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        String normalized = location.trim();
        if (normalized.regionMatches(true, 0, "http://", 0, 7)
                || normalized.regionMatches(true, 0, "https://", 0, 8)) {
            return normalized;
        }
        // El cliente solo necesita conocer que existe un archivo local; la ruta
        // física del servidor nunca debe formar parte del contrato público.
        return "ARCHIVO_LOCAL";
    }
}
