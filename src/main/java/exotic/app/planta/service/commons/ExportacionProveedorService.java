package exotic.app.planta.service.commons;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.commons.dto.exportacion.ExportacionProveedoresConContactosDTO;
import exotic.app.planta.model.compras.ContactoProveedor;
import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.repo.compras.ProveedorRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportacionProveedorService {

    private final ProveedorRepo proveedorRepo;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public byte[] exportarProveedoresJsonConContactos() {
        List<Proveedor> proveedores = proveedorRepo.findAll();

        ExportacionProveedoresConContactosDTO exportacion = new ExportacionProveedoresConContactosDTO(
                1,
                LocalDateTime.now(),
                proveedores.stream().map(this::mapProveedor).toList()
        );

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(exportacion);
        } catch (IOException e) {
            log.error("Error generating exportacion proveedores JSON con contactos", e);
            throw new RuntimeException("Error generating exportacion proveedores JSON con contactos", e);
        }
    }

    private ExportacionProveedoresConContactosDTO.ProveedorExportDTO mapProveedor(Proveedor proveedor) {
        List<ExportacionProveedoresConContactosDTO.ContactoExportDTO> contactos =
                proveedor.getContactos() == null
                        ? List.of()
                        : proveedor.getContactos().stream().map(this::mapContacto).toList();

        return new ExportacionProveedoresConContactosDTO.ProveedorExportDTO(
                proveedor.getId(),
                proveedor.getTipoIdentificacion(),
                proveedor.getNombre(),
                proveedor.getDireccion(),
                proveedor.getRegimenTributario(),
                proveedor.getCiudad(),
                proveedor.getDepartamento(),
                proveedor.getUrl(),
                proveedor.getObservacion(),
                proveedor.getCondicionPago(),
                proveedor.getCategorias(),
                contactos
        );
    }

    private ExportacionProveedoresConContactosDTO.ContactoExportDTO mapContacto(ContactoProveedor contacto) {
        return new ExportacionProveedoresConContactosDTO.ContactoExportDTO(
                contacto.getFullName(),
                contacto.getCargo(),
                contacto.getCel(),
                contacto.getEmail()
        );
    }
}
