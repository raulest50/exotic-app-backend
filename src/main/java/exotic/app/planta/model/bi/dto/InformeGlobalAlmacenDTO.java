package exotic.app.planta.model.bi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InformeGlobalAlmacenDTO {

    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    private String modoFecha;
    private int diasRango;
    private ResumenDTO resumen;
    private List<CantidadUnidadDTO> cantidadesPorUnidad;
    private List<SerieDiariaDTO> serieDiaria;
    private List<ConsolidadoTipoMaterialDTO> consolidadoTipoMaterial;
    private List<TopMaterialDTO> topMateriales;
    private List<NotaDTO> notas;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumenDTO {
        private double valorIngresosEstimado;
        private double valorDispensacionesEstimado;
        private double balanceValorEstimado;
        private int movimientosIngreso;
        private int movimientosDispensacion;
        private int materialesIngresados;
        private int materialesDispensados;
        private int materialesConCosto;
        private int materialesSinCosto;
        private Double coberturaCostosPct;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CantidadUnidadDTO {
        private String unidadMedida;
        private double cantidadIngresada;
        private double cantidadDispensada;
        private double balanceNeto;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SerieDiariaDTO {
        private LocalDate fecha;
        private double valorIngresosEstimado;
        private double valorDispensacionesEstimado;
        private int movimientosIngreso;
        private int movimientosDispensacion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsolidadoTipoMaterialDTO {
        private String tipoMaterial;
        private double valorIngresosEstimado;
        private double valorDispensacionesEstimado;
        private int movimientos;
        private List<CantidadUnidadDTO> cantidadesPorUnidad;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopMaterialDTO {
        private String productoId;
        private String productoNombre;
        private String tipoMaterial;
        private String unidadMedida;
        private double cantidadIngresada;
        private double cantidadDispensada;
        private double valorIngresosEstimado;
        private double valorDispensacionesEstimado;
        private double impactoValorEstimado;
        private boolean costoDisponible;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotaDTO {
        private String tipo;
        private String mensaje;
    }
}
