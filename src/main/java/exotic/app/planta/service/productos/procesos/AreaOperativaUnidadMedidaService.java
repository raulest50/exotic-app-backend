package exotic.app.planta.service.productos.procesos;

import exotic.app.planta.dto.ConversionUnidadAreaOperativaRequestDTO;
import exotic.app.planta.dto.ConversionUnidadAreaOperativaResponseDTO;
import exotic.app.planta.dto.UnidadMedidaAreaOperativaDTO;
import exotic.app.planta.dto.UnidadMedidaAreaOperativaRequestDTO;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.organizacion.UnidadMedidaAreaOperativa;
import exotic.app.planta.model.organizacion.UnidadRelacionAreaOperativa;
import exotic.app.planta.repo.producto.procesos.AreaOperativaCategoriaUnidadMedidaRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.producto.procesos.UnidadMedidaAreaOperativaRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class AreaOperativaUnidadMedidaService {

    private static final int CONVERSION_SCALE = 6;

    private final AreaProduccionRepo areaProduccionRepo;
    private final UnidadMedidaAreaOperativaRepo unidadRepo;
    private final AreaOperativaCategoriaUnidadMedidaRepo areaCategoriaUnidadRepo;

    @Transactional(readOnly = true)
    public List<UnidadMedidaAreaOperativaDTO> listarUnidades(Integer areaId) {
        requireArea(areaId);
        return unidadRepo.findAllByAreaOperativa_AreaIdOrderByNombreAsc(areaId).stream()
                .map(UnidadMedidaAreaOperativaDTO::fromEntity)
                .toList();
    }

    public UnidadMedidaAreaOperativaDTO crearUnidad(Integer areaId, UnidadMedidaAreaOperativaRequestDTO request) {
        AreaOperativa area = requireArea(areaId);
        UnidadMedidaAreaOperativa unidad = new UnidadMedidaAreaOperativa();
        unidad.setAreaOperativa(area);
        applyUnidadRequest(unidad, request, true);
        return UnidadMedidaAreaOperativaDTO.fromEntity(unidadRepo.save(unidad));
    }

    public UnidadMedidaAreaOperativaDTO actualizarUnidad(
            Integer areaId,
            Long unidadId,
            UnidadMedidaAreaOperativaRequestDTO request
    ) {
        requireArea(areaId);
        UnidadMedidaAreaOperativa unidad = requireUnidad(areaId, unidadId);
        applyUnidadRequest(unidad, request, false);
        return UnidadMedidaAreaOperativaDTO.fromEntity(unidadRepo.save(unidad));
    }

    public void eliminarUnidad(Integer areaId, Long unidadId) {
        requireArea(areaId);
        UnidadMedidaAreaOperativa unidad = requireUnidad(areaId, unidadId);
        areaCategoriaUnidadRepo.deleteAllByUnidadMedida_Id(unidadId);
        unidadRepo.delete(unidad);
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

        if (!origen.getUnidadRelacion().isCompatibleWith(destino.getUnidadRelacion())) {
            throw new IllegalArgumentException("Las unidades no son compatibles para conversion");
        }

        BigDecimal cantidadBase = origen.getUnidadRelacion()
                .toBase(cantidadOrigen.multiply(origen.getRelacionEstandar()));
        BigDecimal unidadDestinoBase = destino.getUnidadRelacion().toBase(destino.getRelacionEstandar());
        BigDecimal cantidadDestino = cantidadBase.divide(
                unidadDestinoBase,
                CONVERSION_SCALE,
                RoundingMode.HALF_UP
        );

        return ConversionUnidadAreaOperativaResponseDTO.builder()
                .unidadOrigen(UnidadMedidaAreaOperativaDTO.fromEntity(origen))
                .unidadDestino(UnidadMedidaAreaOperativaDTO.fromEntity(destino))
                .cantidadOrigen(cantidadOrigen)
                .cantidadBase(cantidadBase)
                .unidadBase(resolveUnidadBase(origen.getUnidadRelacion()))
                .cantidadDestino(cantidadDestino)
                .build();
    }

    private void applyUnidadRequest(
            UnidadMedidaAreaOperativa unidad,
            UnidadMedidaAreaOperativaRequestDTO request,
            boolean isCreate
    ) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de unidad no puede ser nula");
        }

        String nombre = normalizeRequired(request.getNombre(), "El nombre de la unidad es obligatorio");
        if (nombre.length() > 120) {
            throw new IllegalArgumentException("El nombre de la unidad no puede superar 120 caracteres");
        }
        UnidadRelacionAreaOperativa unidadRelacion = request.getUnidadRelacion();
        if (unidadRelacion == null) {
            throw new IllegalArgumentException("La unidad de relacion es obligatoria");
        }
        BigDecimal relacionEstandar = requirePositive(
                request.getRelacionEstandar(),
                "La relacion estandar debe ser mayor que 0"
        );

        boolean duplicateName = isCreate
                ? unidadRepo.existsByNombreIgnoreCase(nombre)
                : unidadRepo.existsByNombreIgnoreCaseAndIdNot(nombre, unidad.getId());
        if (duplicateName) {
            throw new IllegalArgumentException("Ya existe una unidad de medida con el nombre: " + nombre);
        }

        unidad.setNombre(nombre);
        unidad.setRelacionEstandar(relacionEstandar);
        unidad.setUnidadRelacion(unidadRelacion);
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

    private BigDecimal requirePositive(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = value != null ? value.trim() : "";
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String resolveUnidadBase(UnidadRelacionAreaOperativa unidadRelacion) {
        return switch (unidadRelacion) {
            case ML, L -> "ML";
            case G, KG -> "G";
            case U -> "U";
        };
    }
}
