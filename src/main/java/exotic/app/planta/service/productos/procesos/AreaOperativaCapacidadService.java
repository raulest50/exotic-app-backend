package exotic.app.planta.service.productos.procesos;

import exotic.app.planta.dto.CapacidadAreaOperativaDTO;
import exotic.app.planta.dto.CapacidadAreaOperativaRequestDTO;
import exotic.app.planta.dto.ConversionUnidadAreaOperativaRequestDTO;
import exotic.app.planta.dto.ConversionUnidadAreaOperativaResponseDTO;
import exotic.app.planta.dto.UnidadMedidaAreaOperativaDTO;
import exotic.app.planta.dto.UnidadMedidaAreaOperativaRequestDTO;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.organizacion.CapacidadAreaOperativa;
import exotic.app.planta.model.organizacion.DimensionUnidadAreaOperativa;
import exotic.app.planta.model.organizacion.UnidadMedidaAreaOperativa;
import exotic.app.planta.repo.producto.procesos.AreaOperativaCategoriaUnidadMedidaRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.producto.procesos.CapacidadAreaOperativaRepo;
import exotic.app.planta.repo.producto.procesos.UnidadMedidaAreaOperativaRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class AreaOperativaCapacidadService {

    private static final int CONVERSION_SCALE = 6;
    private static final Map<DimensionUnidadAreaOperativa, Set<String>> UNIDADES_ESTANDAR_VALIDAS = Map.of(
            DimensionUnidadAreaOperativa.VOLUMEN, Set.of("L"),
            DimensionUnidadAreaOperativa.MASA, Set.of("KG"),
            DimensionUnidadAreaOperativa.CONTEO, Set.of("U"),
            DimensionUnidadAreaOperativa.TIEMPO, Set.of("MIN")
    );

    private final AreaProduccionRepo areaProduccionRepo;
    private final UnidadMedidaAreaOperativaRepo unidadRepo;
    private final CapacidadAreaOperativaRepo capacidadRepo;
    private final AreaOperativaCategoriaUnidadMedidaRepo areaCategoriaUnidadRepo;

    @Transactional(readOnly = true)
    public List<UnidadMedidaAreaOperativaDTO> listarUnidades(Integer areaId) {
        requireArea(areaId);
        return unidadRepo.findAllByAreaOperativa_AreaIdOrderByPrincipalDescNombreAsc(areaId).stream()
                .map(UnidadMedidaAreaOperativaDTO::fromEntity)
                .toList();
    }

    public UnidadMedidaAreaOperativaDTO crearUnidad(Integer areaId, UnidadMedidaAreaOperativaRequestDTO request) {
        AreaOperativa area = requireArea(areaId);
        UnidadMedidaAreaOperativa unidad = new UnidadMedidaAreaOperativa();
        unidad.setAreaOperativa(area);
        applyUnidadRequest(areaId, unidad, request, true);
        return UnidadMedidaAreaOperativaDTO.fromEntity(unidadRepo.save(unidad));
    }

    public UnidadMedidaAreaOperativaDTO actualizarUnidad(
            Integer areaId,
            Long unidadId,
            UnidadMedidaAreaOperativaRequestDTO request
    ) {
        requireArea(areaId);
        UnidadMedidaAreaOperativa unidad = requireUnidad(areaId, unidadId);
        applyUnidadRequest(areaId, unidad, request, false);
        return UnidadMedidaAreaOperativaDTO.fromEntity(unidadRepo.save(unidad));
    }

    public void desactivarUnidad(Integer areaId, Long unidadId) {
        requireArea(areaId);
        UnidadMedidaAreaOperativa unidad = requireUnidad(areaId, unidadId);
        unidad.setActivo(false);
        unidad.setPrincipal(false);
        areaCategoriaUnidadRepo.deleteAllByUnidadMedida_Id(unidadId);
        unidadRepo.save(unidad);
    }

    @Transactional(readOnly = true)
    public List<CapacidadAreaOperativaDTO> listarCapacidades(Integer areaId) {
        requireArea(areaId);
        return capacidadRepo.findAllByAreaOperativa_AreaIdOrderByActivoDescTipoCapacidadAscPeriodoAsc(areaId).stream()
                .map(CapacidadAreaOperativaDTO::fromEntity)
                .toList();
    }

    public CapacidadAreaOperativaDTO crearCapacidad(Integer areaId, CapacidadAreaOperativaRequestDTO request) {
        AreaOperativa area = requireArea(areaId);
        CapacidadAreaOperativa capacidad = new CapacidadAreaOperativa();
        capacidad.setAreaOperativa(area);
        applyCapacidadRequest(areaId, capacidad, request);
        return CapacidadAreaOperativaDTO.fromEntity(capacidadRepo.save(capacidad));
    }

    public CapacidadAreaOperativaDTO actualizarCapacidad(
            Integer areaId,
            Long capacidadId,
            CapacidadAreaOperativaRequestDTO request
    ) {
        requireArea(areaId);
        CapacidadAreaOperativa capacidad = requireCapacidad(areaId, capacidadId);
        applyCapacidadRequest(areaId, capacidad, request);
        return CapacidadAreaOperativaDTO.fromEntity(capacidadRepo.save(capacidad));
    }

    public void desactivarCapacidad(Integer areaId, Long capacidadId) {
        requireArea(areaId);
        CapacidadAreaOperativa capacidad = requireCapacidad(areaId, capacidadId);
        capacidad.setActivo(false);
        capacidadRepo.save(capacidad);
    }

    @Transactional(readOnly = true)
    public ConversionUnidadAreaOperativaResponseDTO convertir(ConversionUnidadAreaOperativaRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de conversion no puede ser nula");
        }
        BigDecimal cantidadOrigen = requirePositive(request.getCantidadOrigen(), "La cantidad origen debe ser mayor que 0");
        UnidadMedidaAreaOperativa origen = unidadRepo.findById(request.getUnidadOrigenId())
                .orElseThrow(() -> new IllegalArgumentException("Unidad origen no encontrada: " + request.getUnidadOrigenId()));
        UnidadMedidaAreaOperativa destino = unidadRepo.findById(request.getUnidadDestinoId())
                .orElseThrow(() -> new IllegalArgumentException("Unidad destino no encontrada: " + request.getUnidadDestinoId()));

        if (!origen.isActivo() || !destino.isActivo()) {
            throw new IllegalArgumentException("Solo se pueden convertir unidades activas");
        }
        if (origen.getDimension() != destino.getDimension()
                || !origen.getUnidadEstandar().equalsIgnoreCase(destino.getUnidadEstandar())) {
            throw new IllegalArgumentException("Las unidades no son compatibles para conversion");
        }

        BigDecimal cantidadEstandar = cantidadOrigen.multiply(origen.getCantidadUnidadEstandar());
        BigDecimal cantidadDestino = cantidadEstandar.divide(
                destino.getCantidadUnidadEstandar(),
                CONVERSION_SCALE,
                RoundingMode.HALF_UP
        );

        return ConversionUnidadAreaOperativaResponseDTO.builder()
                .unidadOrigen(UnidadMedidaAreaOperativaDTO.fromEntity(origen))
                .unidadDestino(UnidadMedidaAreaOperativaDTO.fromEntity(destino))
                .cantidadOrigen(cantidadOrigen)
                .cantidadEstandar(cantidadEstandar)
                .unidadEstandar(origen.getUnidadEstandar())
                .cantidadDestino(cantidadDestino)
                .build();
    }

    private void applyUnidadRequest(
            Integer areaId,
            UnidadMedidaAreaOperativa unidad,
            UnidadMedidaAreaOperativaRequestDTO request,
            boolean isCreate
    ) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de unidad no puede ser nula");
        }

        String codigo = normalizeCodigo(request.getCodigo());
        String nombre = normalizeRequired(request.getNombre(), "El nombre de la unidad es obligatorio");
        DimensionUnidadAreaOperativa dimension = request.getDimension();
        if (dimension == null) {
            throw new IllegalArgumentException("La dimension de la unidad es obligatoria");
        }
        String unidadEstandar = normalizeCodigo(request.getUnidadEstandar());
        validateUnidadEstandar(dimension, unidadEstandar);
        BigDecimal cantidadUnidadEstandar = requirePositive(
                request.getCantidadUnidadEstandar(),
                "La cantidad en unidad estandar debe ser mayor que 0"
        );

        boolean duplicateCode = isCreate
                ? unidadRepo.existsByAreaOperativa_AreaIdAndCodigoIgnoreCase(areaId, codigo)
                : unidadRepo.existsByAreaOperativa_AreaIdAndCodigoIgnoreCaseAndIdNot(areaId, codigo, unidad.getId());
        if (duplicateCode) {
            throw new IllegalArgumentException("Ya existe una unidad con codigo " + codigo + " para esta area operativa");
        }

        boolean principal = request.getPrincipal() != null ? request.getPrincipal() : unidad.isPrincipal();
        boolean activo = request.getActivo() != null ? request.getActivo() : unidad.isActivo();
        if (principal && !activo) {
            throw new IllegalArgumentException("Una unidad principal debe estar activa");
        }
        if (principal) {
            clearPrincipal(areaId, unidad.getId());
        }

        unidad.setCodigo(codigo);
        unidad.setNombre(nombre);
        unidad.setDescripcion(trimToNull(request.getDescripcion()));
        unidad.setDimension(dimension);
        unidad.setUnidadEstandar(unidadEstandar);
        unidad.setCantidadUnidadEstandar(cantidadUnidadEstandar);
        unidad.setPrincipal(principal);
        unidad.setDiscreta(request.getDiscreta() != null ? request.getDiscreta() : unidad.isDiscreta());
        unidad.setActivo(activo);
    }

    private void applyCapacidadRequest(
            Integer areaId,
            CapacidadAreaOperativa capacidad,
            CapacidadAreaOperativaRequestDTO request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de capacidad no puede ser nula");
        }

        UnidadMedidaAreaOperativa unidad = requireUnidad(areaId, request.getUnidadMedidaId());
        if (!unidad.isActivo()) {
            throw new IllegalArgumentException("La unidad de medida seleccionada no esta activa");
        }
        if (request.getTipoCapacidad() == null) {
            throw new IllegalArgumentException("El tipo de capacidad es obligatorio");
        }
        if (request.getPeriodo() == null) {
            throw new IllegalArgumentException("El periodo de capacidad es obligatorio");
        }
        BigDecimal cantidad = requirePositive(request.getCantidad(), "La cantidad debe ser mayor que 0");
        BigDecimal eficiencia = request.getEficiencia() != null ? request.getEficiencia() : BigDecimal.ONE;
        if (eficiencia.compareTo(BigDecimal.ZERO) < 0 || eficiencia.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("La eficiencia debe estar entre 0 y 1");
        }
        if (request.getVigenteDesde() != null
                && request.getVigenteHasta() != null
                && request.getVigenteHasta().isBefore(request.getVigenteDesde())) {
            throw new IllegalArgumentException("La fecha de vigencia final no puede ser anterior a la inicial");
        }

        capacidad.setUnidadMedida(unidad);
        capacidad.setTipoCapacidad(request.getTipoCapacidad());
        capacidad.setCantidad(cantidad);
        capacidad.setPeriodo(request.getPeriodo());
        capacidad.setEficiencia(eficiencia);
        capacidad.setVigenteDesde(request.getVigenteDesde());
        capacidad.setVigenteHasta(request.getVigenteHasta());
        capacidad.setDescripcion(trimToNull(request.getDescripcion()));
        capacidad.setActivo(request.getActivo() != null ? request.getActivo() : capacidad.isActivo());
    }

    private AreaOperativa requireArea(Integer areaId) {
        if (areaId == null) {
            throw new IllegalArgumentException("El ID del area operativa es obligatorio");
        }
        return areaProduccionRepo.findById(areaId)
                .orElseThrow(() -> new IllegalArgumentException("Area operativa no encontrada con ID: " + areaId));
    }

    private UnidadMedidaAreaOperativa requireUnidad(Integer areaId, Long unidadId) {
        if (unidadId == null) {
            throw new IllegalArgumentException("El ID de la unidad de medida es obligatorio");
        }
        return unidadRepo.findByIdAndAreaOperativa_AreaId(unidadId, areaId)
                .orElseThrow(() -> new IllegalArgumentException("Unidad de medida no encontrada para esta area: " + unidadId));
    }

    private CapacidadAreaOperativa requireCapacidad(Integer areaId, Long capacidadId) {
        if (capacidadId == null) {
            throw new IllegalArgumentException("El ID de la capacidad es obligatorio");
        }
        return capacidadRepo.findByIdAndAreaOperativa_AreaId(capacidadId, areaId)
                .orElseThrow(() -> new IllegalArgumentException("Capacidad no encontrada para esta area: " + capacidadId));
    }

    private void clearPrincipal(Integer areaId, Long currentUnidadId) {
        unidadRepo.findAllByAreaOperativa_AreaIdOrderByPrincipalDescNombreAsc(areaId).forEach(unidad -> {
            if (!unidad.getId().equals(currentUnidadId) && unidad.isPrincipal()) {
                unidad.setPrincipal(false);
                unidadRepo.save(unidad);
            }
        });
    }

    private void validateUnidadEstandar(DimensionUnidadAreaOperativa dimension, String unidadEstandar) {
        Set<String> unidadesEstandar = UNIDADES_ESTANDAR_VALIDAS.get(dimension);
        if (unidadesEstandar == null || !unidadesEstandar.contains(unidadEstandar)) {
            throw new IllegalArgumentException(
                    "La unidad estandar " + unidadEstandar + " no es valida para la dimension " + dimension
            );
        }
    }

    private BigDecimal requirePositive(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String normalizeCodigo(String value) {
        String normalized = normalizeRequired(value, "El codigo de la unidad es obligatorio").toUpperCase();
        if (normalized.length() > 32) {
            throw new IllegalArgumentException("El codigo de la unidad no puede superar 32 caracteres");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = value != null ? value.trim() : "";
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        String trimmed = value != null ? value.trim() : "";
        return trimmed.isEmpty() ? null : trimmed;
    }
}
