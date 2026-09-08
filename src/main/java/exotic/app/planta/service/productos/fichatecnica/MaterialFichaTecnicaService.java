package exotic.app.planta.service.productos.fichatecnica;

import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.producto.MaterialRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MaterialFichaTecnicaService {

    private final MaterialRepo materialRepo;
    private final MaterialFichaTecnicaStorage storage;

    public boolean isAvailable(String productoId) {
        Material material = requireMaterial(productoId);
        boolean available = storage.isAvailable(material.getFichaTecnicaUrl());
        if (!available && material.getFichaTecnicaUrl() != null && !material.getFichaTecnicaUrl().isBlank()) {
            log.warn("La ficha tecnica declarada para el material {} no esta disponible.", productoId);
        }
        return available;
    }

    public TechnicalSheetDownload load(String productoId) {
        Material material = requireMaterial(productoId);
        MaterialFichaTecnicaStorage.StoredTechnicalSheet storedSheet = storage
                .load(material.getFichaTecnicaUrl())
                .orElseThrow(() -> new NoSuchElementException(
                        "La ficha tecnica del material no esta disponible."
                ));
        return new TechnicalSheetDownload(storedSheet.resource(), storedSheet.contentLength());
    }

    private Material requireMaterial(String productoId) {
        return materialRepo.findById(productoId)
                .orElseThrow(() -> new NoSuchElementException("Material no encontrado: " + productoId));
    }

    public record TechnicalSheetDownload(Resource resource, long contentLength) {
    }
}
