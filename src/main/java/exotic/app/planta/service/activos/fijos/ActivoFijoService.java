package exotic.app.planta.service.activos.fijos;

import exotic.app.planta.model.activos.fijos.dto.DTO_SearchActivoFijo;
import exotic.app.planta.model.activos.fijos.ActivoFijo;
import exotic.app.planta.model.activos.fijos.compras.FacturaCompraActivo;
import exotic.app.planta.model.activos.fijos.compras.ItemOrdenCompraActivo;
import exotic.app.planta.model.activos.fijos.compras.OrdenCompraActivo;
import exotic.app.planta.model.activos.fijos.gestion.IncorporacionActivoHeader;
import exotic.app.planta.model.activos.fijos.gestion.IncorporacionActivoLine;
import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import exotic.app.planta.repo.activos.fijos.ActivoFijoRepo;
import exotic.app.planta.repo.activos.fijos.compras.FacturaCompraActivoRepo;
import exotic.app.planta.repo.activos.fijos.gestion.IncorporacionActivoHeaderRepo;
import exotic.app.planta.repo.activos.fijos.gestion.IncorporacionActivoLineRepo;
import exotic.app.planta.repo.personal.IntegrantePersonalRepo;
import exotic.app.planta.model.activos.fijos.dto.IncorporacionActivoDto;
import exotic.app.planta.model.activos.fijos.dto.GrupoActivosDto;
import exotic.app.planta.service.commons.FileStorageService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Servicio para la gestión de activos fijos.
 * Proporciona métodos para administrar incorporaciones, depreciaciones
 * y otros aspectos relacionados con los activos fijos.
 * 
 * Nota: La gestión de órdenes de compra de activos fijos ha sido movida a {@link OCAFService}
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivoFijoService {

    private final ActivoFijoRepo activoFijoRepo;
    private final IntegrantePersonalRepo integrantePersonalRepo;
    private final IncorporacionActivoHeaderRepo incorporacionActivoHeaderRepo;
    private final IncorporacionActivoLineRepo incorporacionActivoLineRepo;
    private final FacturaCompraActivoRepo facturaCompraActivoRepo;
    private final FileStorageService fileStorageService;

    /**
     * Obtiene todos los activos fijos paginados.
     * 
     * @param pageable Configuración de paginación
     * @return Página de activos fijos
     */
    public Page<ActivoFijo> findAll(Pageable pageable) {
        return activoFijoRepo.findAll(pageable);
    }

    /**
     * Obtiene un activo fijo por su ID.
     * 
     * @param id ID del activo fijo
     * @return Activo fijo si existe
     */
    public Optional<ActivoFijo> findById(String id) {
        return activoFijoRepo.findById(id);
    }

    /**
     * Guarda un activo fijo.
     * 
     * @param activoFijo Activo fijo a guardar
     * @return Activo fijo guardado
     */
    @Transactional
    public ActivoFijo save(ActivoFijo activoFijo) {
        if (activoFijo.getFechaCodificacion() == null) {
            activoFijo.setFechaCodificacion(LocalDateTime.now());
        }
        return activoFijoRepo.save(activoFijo);
    }

    /**
     * Elimina un activo fijo.
     * 
     * @param id ID del activo fijo a eliminar
     */
    @Transactional
    public void deleteById(String id) {
        activoFijoRepo.deleteById(id);
    }

    /**
     * Busca activos fijos por ubicación.
     * 
     * @param ubicacion Ubicación a buscar
     * @return Lista de activos fijos en la ubicación especificada
     */
    public List<ActivoFijo> findByUbicacion(String ubicacion) {
        return activoFijoRepo.findByUbicacion(ubicacion);
    }

    /**
     * Busca activos fijos por responsable.
     * 
     * @param responsableId ID del responsable
     * @return Lista de activos fijos asignados al responsable
     */
    public List<ActivoFijo> findByResponsable(long responsableId) {
        return activoFijoRepo.findByResponsableId(responsableId);
    }

    /**
     * Busca activos fijos por tipo.
     * 
     * @param tipoActivo Tipo de activo
     * @return Lista de activos fijos del tipo especificado
     */
    public List<ActivoFijo> findByTipoActivo(ActivoFijo.TipoActivo tipoActivo) {
        return activoFijoRepo.findByTipoActivo(tipoActivo);
    }

    /**
     * Asigna un responsable a un activo fijo.
     * 
     * @param activoId ID del activo fijo
     * @param responsableId ID del responsable
     * @return Activo fijo actualizado
     */
    @Transactional
    public Optional<ActivoFijo> asignarResponsable(String activoId, long responsableId) {
        Optional<ActivoFijo> activoOpt = activoFijoRepo.findById(activoId);
        Optional<IntegrantePersonal> responsableOpt = integrantePersonalRepo.findById(responsableId);

        if (activoOpt.isPresent() && responsableOpt.isPresent()) {
            ActivoFijo activo = activoOpt.get();
            activo.setResponsable(responsableOpt.get());
            return Optional.of(activoFijoRepo.save(activo));
        }

        return Optional.empty();
    }

    /**
     * Actualiza la ubicación de un activo fijo.
     * 
     * @param activoId ID del activo fijo
     * @param ubicacion Nueva ubicación
     * @return Activo fijo actualizado
     */
    @Transactional
    public Optional<ActivoFijo> actualizarUbicacion(String activoId, String ubicacion) {
        Optional<ActivoFijo> activoOpt = activoFijoRepo.findById(activoId);

        if (activoOpt.isPresent()) {
            ActivoFijo activo = activoOpt.get();
            activo.setUbicacion(ubicacion);
            return Optional.of(activoFijoRepo.save(activo));
        }

        return Optional.empty();
    }

    /**
     * Busca activos fijos según los criterios especificados en el DTO de búsqueda.
     * 
     * @param searchDTO DTO con los criterios de búsqueda
     * @param pageable Configuración de paginación
     * @return Página de activos fijos que cumplen con los criterios
     */
    public Page<ActivoFijo> search(DTO_SearchActivoFijo searchDTO, Pageable pageable) {
        Specification<ActivoFijo> spec = createSpecification(searchDTO);
        return activoFijoRepo.findAll(spec, pageable);
    }

    /**
     * Busca activos fijos disponibles para asignar a recursos de producción.
     * Filtra por tipo PRODUCCION, estado activo y no asignados a ningún recurso.
     * 
     * @param nombreBusqueda Término de búsqueda para filtrar por nombre (opcional)
     * @param pageable Configuración de paginación
     * @return Página de activos fijos disponibles para asignar
     */
    @Transactional(readOnly = true)
    public Page<ActivoFijo> findActivosFijosDisponiblesParaProduccion(String nombreBusqueda, Pageable pageable) {
        Specification<ActivoFijo> spec = (root, query, cb) -> {
            // Predicado inicial: tipo PRODUCCION y estado activo
            var predicate = cb.and(
                cb.equal(root.get("tipoActivo"), ActivoFijo.TipoActivo.PRODUCCION),
                cb.equal(root.get("estado"), 0)
            );

            // Añadir predicado: tipoRecurso es nulo (no asignado)
            predicate = cb.and(predicate, cb.isNull(root.get("tipoRecurso")));

            // Si hay nombre de búsqueda, añadir predicado de coincidencia parcial
            if (nombreBusqueda != null && !nombreBusqueda.trim().isEmpty()) {
                predicate = cb.and(predicate, 
                    cb.like(cb.lower(root.get("nombre")), "%" + nombreBusqueda.toLowerCase() + "%"));
            }

            return predicate;
        };

        return activoFijoRepo.findAll(spec, pageable);
    }

    /**
     * Crea una especificación JPA basada en los criterios de búsqueda.
     * 
     * @param searchDTO DTO con los criterios de búsqueda
     * @return Especificación JPA para la búsqueda
     */
    private Specification<ActivoFijo> createSpecification(DTO_SearchActivoFijo searchDTO) {
        return (root, query, criteriaBuilder) -> {
            // Inicializar con predicado verdadero (no filtra nada)
            var predicate = criteriaBuilder.conjunction();

            // Filtrar por tipo de activo según el nuevo enum TipoActivoBusqueda
            if (searchDTO.getTipoActivoBusqueda() != null) {
                if (searchDTO.getTipoActivoBusqueda() != DTO_SearchActivoFijo.TipoActivoBusqueda.TODOS) {
                    // Convertir el valor del enum TipoActivoBusqueda al enum TipoActivo correspondiente
                    ActivoFijo.TipoActivo tipoActivo = ActivoFijo.TipoActivo.valueOf(
                        searchDTO.getTipoActivoBusqueda().name());

                    predicate = criteriaBuilder.and(predicate, 
                        criteriaBuilder.equal(root.get("tipoActivo"), tipoActivo));
                }
                // Si es TODOS, no aplicamos ningún filtro por tipo
            } 
            // Mantener compatibilidad con el campo tipoActivo (deprecated)
            else if (searchDTO.getTipoActivo() != null) {
                predicate = criteriaBuilder.and(predicate, 
                    criteriaBuilder.equal(root.get("tipoActivo"), searchDTO.getTipoActivo()));
            }

            // Filtrar por estado activo si se solicita
            if (searchDTO.getSoloActivos() != null && searchDTO.getSoloActivos()) {
                predicate = criteriaBuilder.and(predicate, 
                    criteriaBuilder.equal(root.get("estado"), 0)); // 0: activo según el modelo
            }

            // Aplicar criterio de búsqueda específico
            if (searchDTO.getTipoBusqueda() != null && searchDTO.getValorBusqueda() != null 
                    && !searchDTO.getValorBusqueda().trim().isEmpty()) {

                switch (searchDTO.getTipoBusqueda()) {
                    case POR_ID:
                        predicate = criteriaBuilder.and(predicate, 
                            criteriaBuilder.equal(root.get("id"), searchDTO.getValorBusqueda()));
                        break;

                    case POR_NOMBRE:
                        predicate = criteriaBuilder.and(predicate, 
                            criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("nombre")), 
                                "%" + searchDTO.getValorBusqueda().toLowerCase() + "%"));
                        break;

                    case POR_UBICACION:
                        predicate = criteriaBuilder.and(predicate, 
                            criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("ubicacion")), 
                                "%" + searchDTO.getValorBusqueda().toLowerCase() + "%"));
                        break;

                    case POR_RESPONSABLE:
                        try {
                            long responsableId = Long.parseLong(searchDTO.getValorBusqueda());
                            predicate = criteriaBuilder.and(predicate, 
                                criteriaBuilder.equal(root.get("responsable").get("id"), responsableId));
                        } catch (NumberFormatException e) {
                            log.warn("Valor de búsqueda para responsable no es un número válido: {}", 
                                searchDTO.getValorBusqueda());
                        }
                        break;

                    case POR_MARCA:
                        predicate = criteriaBuilder.and(predicate, 
                            criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("brand")), 
                                "%" + searchDTO.getValorBusqueda().toLowerCase() + "%"));
                        break;

                    case POR_CAPACIDAD:
                        try {
                            double capacidad = Double.parseDouble(searchDTO.getValorBusqueda());
                            predicate = criteriaBuilder.and(predicate, 
                                criteriaBuilder.equal(root.get("capacidad"), capacidad));
                        } catch (NumberFormatException e) {
                            log.warn("Valor de búsqueda para capacidad no es un número válido: {}", 
                                searchDTO.getValorBusqueda());
                        }
                        break;
                }
            }

            return predicate;
        };
    }

    /**
     * Procesa la incorporación de activos fijos a partir del DTO recibido.
     */
    @Transactional
    public IncorporacionActivoHeader procesarIncorporacion(IncorporacionActivoDto dto,
                                                           OrdenCompraActivo ordenCompraActivo,
                                                           MultipartFile documentoSoporte) throws java.io.IOException {
        IncorporacionActivoHeader header = new IncorporacionActivoHeader();
        header.setEstado(1);
        header.setObservaciones(dto.getObservaciones());

        FacturaCompraActivo factura = new FacturaCompraActivo();
        if (ordenCompraActivo != null) {
            factura.setOrdenCompraActivo(ordenCompraActivo);
            factura.setProveedor(ordenCompraActivo.getProveedor());
            factura.setSubTotal(ordenCompraActivo.getSubTotal());
            factura.setIva(ordenCompraActivo.getIva());
            factura.setTotalPagar(ordenCompraActivo.getTotalPagar());
            factura.setCondicionPago(ordenCompraActivo.getCondicionPago());
            factura.setPlazoPago(ordenCompraActivo.getPlazoPago());
        }

        factura = facturaCompraActivoRepo.save(factura);

        if (documentoSoporte != null && !documentoSoporte.isEmpty()) {
            String path = fileStorageService.storeFacturaActivoFile(factura.getFacturaCompraActivoId(), documentoSoporte);
            factura.setUrlFactura(path);
            factura = facturaCompraActivoRepo.save(factura);
        }

        header.setFacturaCompraActivo(factura);

        // Procesar grupos y activos
        if (dto.getGruposActivos() != null) {
            for (GrupoActivosDto grupo : dto.getGruposActivos()) {
                ItemOrdenCompraActivo item = grupo.getItemOrdenCompra();
                if (grupo.getActivos() != null) {
                    for (ActivoFijo af : grupo.getActivos()) {
                        if (af.getFechaCodificacion() == null) {
                            af.setFechaCodificacion(LocalDateTime.now());
                        }
                        ActivoFijo saved = activoFijoRepo.save(af);
                        IncorporacionActivoLine line = new IncorporacionActivoLine();
                        line.setIncorporacionHeader(header);
                        line.setActivoFijo(saved);
                        line.setDescripcion(saved.getNombre());
                        line.setCantidad(1);
                        if (item != null) {
                            java.math.BigDecimal valor = java.math.BigDecimal.valueOf(item.getPrecioUnitario());
                            line.setValorUnitario(valor);
                            line.setValorTotal(valor);
                        }
                        line.setUbicacionInicial(saved.getUbicacion());
                        line.setVidaUtilMeses(saved.getVidaUtilMeses());
                        line.setMetodoDepreciacion(saved.getMetodoDespreciacion());
                        header.getLineasIncorporacion().add(line);
                    }
                }
            }
        }

        return incorporacionActivoHeaderRepo.save(header);
    }
}
