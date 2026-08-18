package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionDocumentoVersion;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionOperacionRepo;
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
    private final OrdenFabricacionOperacionRepo ordenFabricacionOperacionRepo;
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

    public ProcesoProduccionDocumentoService.DescargaDocumento getDescargaFabricacion(
            Long ordenFabricacionId,
            Long operacionId,
            Long userId
    ) {
        if (userId == null) {
            throw new AccessDeniedException("Se requiere un usuario autenticado.");
        }
        OrdenFabricacionOperacion operacion = ordenFabricacionOperacionRepo
                .findPoeDetalle(ordenFabricacionId, operacionId)
                .orElseThrow(() -> new NoSuchElementException(
                        "La operacion solicitada no pertenece a la OF indicada."));
        Long responsableId = operacion.getAreaOperativa().getResponsableArea() == null
                ? null : operacion.getAreaOperativa().getResponsableArea().getId();
        if (!Objects.equals(responsableId, userId)) {
            throw new AccessDeniedException(
                    "Solo el responsable del area de esta operacion puede consultar su POE.");
        }
        ProcesoProduccionDocumentoVersion documento = operacion.getPoeDocumentoVersion();
        if (documento == null) {
            throw new NoSuchElementException(
                    "La operacion no tiene una version de POE congelada.");
        }
        return procesoDocumentoService.getDescarga(
                documento.getProceso().getProcesoId(), documento.getId());
    }
}
