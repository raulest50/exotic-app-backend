package exotic.app.planta.model.commons.dto.exportacion;

import java.time.LocalDateTime;
import java.util.List;

public record ExportacionProveedoresConContactosDTO(
        int schemaVersion,
        LocalDateTime exportedAt,
        List<ProveedorExportDTO> proveedores
) {

    public record ProveedorExportDTO(
            String id,
            int tipoIdentificacion,
            String nombre,
            String direccion,
            int regimenTributario,
            String ciudad,
            String departamento,
            String url,
            String observacion,
            String condicionPago,
            int[] categorias,
            List<ContactoExportDTO> contactos
    ) {
    }

    public record ContactoExportDTO(
            String fullName,
            String cargo,
            String cel,
            String email
    ) {
    }
}
