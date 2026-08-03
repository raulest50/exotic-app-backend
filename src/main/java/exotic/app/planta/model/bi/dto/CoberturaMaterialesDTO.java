package exotic.app.planta.model.bi.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Builder(toBuilder = true)
public record CoberturaMaterialesDTO(
        int ventanaDias,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        LocalDateTime fechaHoraCorteStock,
        FuenteDemandaCobertura fuenteDemanda,
        boolean escenarioExploratorio,
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
        int diasConDemanda,
        int materialesAnalizados,
        int materialesConDemanda,
        ResumenFuentesDemandaDTO resumenFuentesDemanda,
        List<EstimacionMaterialDTO> estimaciones,
        FacetasCoberturaDTO facetas,
        PaginaInformeInventarioDTO<EstimacionMaterialDTO> pagina
) {
    public enum EstadoCobertura {
        ESTIMADO,
        SIN_CONSUMO
    }

    @Builder
    public record ResumenFuentesDemandaDTO(
            int movimientosDispensacionIncluidos,
            int ajustesContingenciaDisponibles,
            int ajustesContingenciaIncluidos,
            int ajustesNegativosSinClasificarExcluidos
    ) {
    }

    @Builder
    public record FacetasCoberturaDTO(
            List<String> gruposDisponibles,
            List<String> unidadesDisponibles
    ) {
    }

    @Builder
    public record EstimacionMaterialDTO(
            String productoId,
            String nombre,
            String grupo,
            String unidadMedida,
            double stockActual,
            double demandaMediaDiaria,
            double demandaMediaDiariaOperativa,
            double demandaMediaDiariaContingencia,
            int diasConDispensacion,
            int diasConDemanda,
            int ajustesContingenciaIncluidos,
            Double diasHastaAgotamiento,
            LocalDate fechaAgotamiento,
            LocalDate intervaloFechaMin,
            LocalDate intervaloFechaMax,
            boolean confianzaBaja,
            List<String> motivosConfianzaBaja
    ) {
    }
}
