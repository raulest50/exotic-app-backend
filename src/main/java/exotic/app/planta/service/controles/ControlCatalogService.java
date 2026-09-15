package exotic.app.planta.service.controles;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.controles.MagnitudControl;
import exotic.app.planta.model.controles.UnidadControl;
import exotic.app.planta.model.controles.dto.ControlDTOs.CatalogoResponse;
import exotic.app.planta.model.controles.dto.ControlDTOs.CatalogoWriteRequest;
import exotic.app.planta.model.controles.dto.ControlDTOs.ProductoControlOption;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.repo.controles.CaracteristicaPlanControlRepo;
import exotic.app.planta.repo.controles.MagnitudControlRepo;
import exotic.app.planta.repo.controles.UnidadControlRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ControlCatalogService {
    private final MagnitudControlRepo magnitudRepo;
    private final UnidadControlRepo unidadRepo;
    private final CaracteristicaPlanControlRepo caracteristicaRepo;
    private final ProductoRepo productoRepo;

    @Transactional(readOnly = true)
    public Page<ProductoControlOption> buscarProductos(
            String search, String tipoBusqueda, Integer categoriaId, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new IllegalArgumentException("La paginacion de productos no es valida.");
        }
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String normalizedType = tipoBusqueda == null ? "NOMBRE" : tipoBusqueda.trim().toUpperCase(Locale.ROOT);
        if (!normalizedType.equals("NOMBRE") && !normalizedType.equals("ID")) {
            throw new IllegalArgumentException("El tipo de busqueda debe ser NOMBRE o ID.");
        }

        Specification<Producto> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            var terminado = criteriaBuilder.treat(root, Terminado.class);
            var semiterminado = criteriaBuilder.treat(root, SemiTerminado.class);

            if (categoriaId != null) {
                predicates.add(criteriaBuilder.equal(root.type(), Terminado.class));
                predicates.add(criteriaBuilder.equal(
                        terminado.get("categoria").get("categoriaId"), categoriaId));
            } else {
                Predicate terminadoElegible = criteriaBuilder.and(
                        criteriaBuilder.equal(root.type(), Terminado.class),
                        criteriaBuilder.isNotNull(terminado.get("categoria")));
                Predicate semiterminadoElegible = criteriaBuilder.and(
                        criteriaBuilder.equal(root.type(), SemiTerminado.class),
                        criteriaBuilder.isTrue(semiterminado.get("requiereOrdenFabricacion")));
                predicates.add(criteriaBuilder.or(terminadoElegible, semiterminadoElegible));
            }

            if (!normalizedSearch.isBlank()) {
                if (normalizedType.equals("ID")) {
                    predicates.add(criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("productoId")), "%" + normalizedSearch + "%"));
                } else {
                    for (String term : normalizedSearch.split("\\s+")) {
                        predicates.add(criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("nombre")), "%" + term + "%"));
                    }
                }
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };

        return productoRepo.findAll(specification,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "productoId")))
                .map(this::toProductoResponse);
    }

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

    private ProductoControlOption toProductoResponse(Producto producto) {
        Categoria categoria = producto instanceof Terminado terminado ? terminado.getCategoria() : null;
        return new ProductoControlOption(
                producto.getProductoId(), producto.getNombre(),
                producto instanceof Terminado ? "T" : "S",
                categoria == null ? null : categoria.getCategoriaId(),
                categoria == null ? null : categoria.getCategoriaNombre());
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
