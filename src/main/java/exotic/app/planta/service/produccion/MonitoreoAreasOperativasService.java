package exotic.app.planta.service.produccion;

import exotic.app.planta.config.initializers.AreaOperativaInitializer;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.produccion.dto.AreaOperativaMonitoreoDTO;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MonitoreoAreasOperativasService {

    private final AreaProduccionRepo areaProduccionRepo;
    private final SeguimientoOrdenAreaService seguimientoOrdenAreaService;

    @Transactional(readOnly = true)
    public List<AreaOperativaMonitoreoDTO> listarAreasMonitoreables() {
        return areaProduccionRepo
                .findAllByResponsableAreaIsNotNullAndAreaIdNotOrderByNombreAsc(
                        AreaOperativaInitializer.ALMACEN_GENERAL_ID)
                .stream()
                .map(this::toMonitoreoDto)
                .toList();
    }

    private AreaOperativaMonitoreoDTO toMonitoreoDto(AreaOperativa area) {
        return AreaOperativaMonitoreoDTO.builder()
                .areaId(area.getAreaId())
                .nombre(area.getNombre())
                .descripcion(area.getDescripcion())
                .responsableArea(AreaOperativaMonitoreoDTO.ResponsableAreaResumenDTO.builder()
                        .id(area.getResponsableArea().getId())
                        .username(area.getResponsableArea().getUsername())
                        .nombreCompleto(area.getResponsableArea().getNombreCompleto())
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public SeguimientoOrdenAreaService.AreaOperativaTableroDTO getTableroAreaPorFecha(int areaId, LocalDate fecha) {
        return seguimientoOrdenAreaService.getTableroAreaPorFecha(areaId, fecha);
    }
}
