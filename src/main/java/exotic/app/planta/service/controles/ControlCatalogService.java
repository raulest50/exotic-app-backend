package exotic.app.planta.service.controles;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.controles.MagnitudControl;
import exotic.app.planta.model.controles.UnidadControl;
import exotic.app.planta.model.controles.dto.ControlDTOs.CatalogoResponse;
import exotic.app.planta.model.controles.dto.ControlDTOs.CatalogoWriteRequest;
import exotic.app.planta.repo.controles.CaracteristicaPlanControlRepo;
import exotic.app.planta.repo.controles.MagnitudControlRepo;
import exotic.app.planta.repo.controles.UnidadControlRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ControlCatalogService {
    private final MagnitudControlRepo magnitudRepo;
    private final UnidadControlRepo unidadRepo;
    private final CaracteristicaPlanControlRepo caracteristicaRepo;

    @Transactional(readOnly = true)
    public List<CatalogoResponse> listarMagnitudes(boolean incluirInactivas) {
        return (incluirInactivas ? magnitudRepo.findAllByOrderByNombreAsc()
                : magnitudRepo.findByActivoTrueOrderByNombreAsc()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogoResponse> listarUnidades(boolean incluirInactivas) {
        return (incluirInactivas ? unidadRepo.findAllByOrderByNombreAsc()
                : unidadRepo.findByActivoTrueOrderByNombreAsc()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public CatalogoResponse crearMagnitud(CatalogoWriteRequest request) {
        String codigo = normalizarCodigo(request.codigo());
        if (magnitudRepo.findByCodigoIgnoreCase(codigo).isPresent()) {
            throw new IllegalArgumentException("Ya existe una magnitud con ese codigo.");
        }
        MagnitudControl item = new MagnitudControl();
        item.setCodigo(codigo);
        item.setNombre(requerido(request.nombre(), "nombre"));
        item.setSimbolo(requerido(request.simbolo(), "simbolo"));
        item.setDimension(normalizarCodigo(request.dimension()));
        item.setActivo(true);
        item.setCreadoEn(AppTime.now());
        return toResponse(magnitudRepo.saveAndFlush(item));
    }

    @Transactional
    public CatalogoResponse crearUnidad(CatalogoWriteRequest request) {
        String codigo = normalizarCodigo(request.codigo());
        if (unidadRepo.findByCodigoIgnoreCase(codigo).isPresent()) {
            throw new IllegalArgumentException("Ya existe una unidad con ese codigo.");
        }
        UnidadControl item = new UnidadControl();
        item.setCodigo(codigo);
        item.setNombre(requerido(request.nombre(), "nombre"));
        item.setSimbolo(requerido(request.simbolo(), "simbolo"));
        item.setDimension(normalizarCodigo(request.dimension()));
        item.setActivo(true);
        item.setCreadoEn(AppTime.now());
        return toResponse(unidadRepo.saveAndFlush(item));
    }

    @Transactional
    public CatalogoResponse cambiarEstadoMagnitud(Long id, boolean activo) {
        MagnitudControl item = magnitudRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Magnitud no encontrada."));
        item.setActivo(activo);
        return toResponse(magnitudRepo.saveAndFlush(item));
    }

    @Transactional
    public CatalogoResponse cambiarEstadoUnidad(Long id, boolean activo) {
        UnidadControl item = unidadRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Unidad no encontrada."));
        item.setActivo(activo);
        return toResponse(unidadRepo.saveAndFlush(item));
    }

    private CatalogoResponse toResponse(MagnitudControl item) {
        return new CatalogoResponse(item.getId(), item.getCodigo(), item.getNombre(),
                item.getDimension(), item.getSimbolo(), item.isActivo(),
                caracteristicaRepo.existsByMagnitud_Id(item.getId()));
    }

    private CatalogoResponse toResponse(UnidadControl item) {
        return new CatalogoResponse(item.getId(), item.getCodigo(), item.getNombre(),
                item.getDimension(), item.getSimbolo(), item.isActivo(),
                caracteristicaRepo.existsByUnidad_Id(item.getId()));
    }

    private String normalizarCodigo(String value) {
        String codigo = requerido(value, "codigo").toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_]+", "_")
                .replaceAll("^_+|_+$", "");
        if (codigo.isBlank() || codigo.length() > 40) {
            throw new IllegalArgumentException("El codigo normalizado no es valido.");
        }
        return codigo;
    }

    private String requerido(String value, String campo) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("El campo " + campo + " es obligatorio.");
        }
        return value.trim();
    }
}
