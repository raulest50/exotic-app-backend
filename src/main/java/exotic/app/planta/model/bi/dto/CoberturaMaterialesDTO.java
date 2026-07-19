package exotic.app.planta.model.bi.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record CoberturaMaterialesDTO(
        int ventanaDias,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        LocalDateTime fechaHoraCorteStock,
        EstadoCobertura estado,
        LocalDate fechaPrimerAgotamiento,
        String materialCriticoId,
        String materialCriticoNombre,
        LocalDate intervaloFechaMin,
        LocalDate intervaloFechaMax,
        boolean confianzaBaja,
        List<String> motivosConfianzaBaja,
        int diasObservados,
        int diasConDispensacion,
        int materialesAnalizados,
        int materialesConDemanda,
        List<EstimacionMaterialDTO> estimaciones
) {
    public enum EstadoCobertura {
        ESTIMADO,
        SIN_CONSUMO
    }

    @Builder
    public record EstimacionMaterialDTO(
            String productoId,
            String nombre,
            String unidadMedida,
            double stockActual,
            double demandaMediaDiaria,
            int diasConDispensacion,
            Double diasHastaAgotamiento,
            LocalDate fechaAgotamiento,
            LocalDate intervaloFechaMin,
            LocalDate intervaloFechaMax
    ) {
    }
}
