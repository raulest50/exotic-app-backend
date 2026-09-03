package exotic.app.planta.model.controles.dto;

public record ResumenControlesBatchRecordDTO(
        Long batchRecordId,
        long total,
        long pendientes,
        long conformes,
        long noConformes,
        long aceptadosPorDesviacion,
        long porRevalidar,
        long desviacionesAbiertas) {
}
