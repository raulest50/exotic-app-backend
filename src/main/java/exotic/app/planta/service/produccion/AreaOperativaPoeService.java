package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionDocumentoVersion;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.service.productos.procesos.ProcesoProduccionDocumentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AreaOperativaPoeService {

    private final SeguimientoOrdenAreaRepo seguimientoOrdenAreaRepo;
    private final ProcesoProduccionDocumentoService procesoDocumentoService;

    public ProcesoProduccionDocumentoService.DescargaDocumento getDescarga(
            int ordenId,
            Long seguimientoId,
            Long userId
    ) {
        if (userId == null) {
            throw new AccessDeniedException("Se requiere un usuario autenticado.");
        }

        SeguimientoOrdenArea seguimiento = seguimientoOrdenAreaRepo
                .findPoeDetalleByIdAndOrdenId(seguimientoId, ordenId)
                .orElseThrow(() -> new NoSuchElementException(
                        "La etapa solicitada no pertenece a la orden indicada."
                ));

        Long responsableId = seguimiento.getAreaOperativa() != null
                && seguimiento.getAreaOperativa().getResponsableArea() != null
                ? seguimiento.getAreaOperativa().getResponsableArea().getId()
                : null;
        if (!Objects.equals(responsableId, userId)) {
            throw new AccessDeniedException(
                    "Solo el responsable del area de esta etapa puede consultar su POE."
            );
        }

        ProcesoProduccionDocumentoVersion documento = seguimiento.getPoeDocumentoVersion();
        if (documento == null) {
            throw new NoSuchElementException(
                    "La etapa no tiene una version de POE asociada."
            );
        }

        return procesoDocumentoService.getDescarga(
                documento.getProceso().getProcesoId(),
                documento.getId()
        );
    }
}
