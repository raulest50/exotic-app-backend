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
    private List<ResumenUnidadDTO> resumenPorUnidad;
    private List<RankingUnidadDTO> rankingDispensacion;
    private List<SerieFisicaDiariaDTO> serieFisicaDiaria;
    private List<NotaDTO> notas;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumenDTO {
        private int movimientosDispensacion;
        private int materialesDispensados;
        private int movimientosRecepcionCompra;
        private int materialesRecibidosCompra;
        private int movimientosOtrosIngresos;
        private int materialesOtrosIngresos;
        private double valorDispensacionesEstimado;
        private double valorRecepcionesCompraEstimado;
        private double valorOtrosIngresosEstimado;
        private int materialesConCosto;
        private int materialesSinCosto;
        private Double coberturaCostosPct;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumenUnidadDTO {
        private String unidadMedida;
        private double cantidadDispensada;
        private double cantidadRecibidaCompra;
        private double cantidadOtrosIngresos;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RankingUnidadDTO {
        private String unidadMedida;
        private double cantidadTotal;
        private int materialesTotales;
        private List<MaterialDispensadoDTO> materiales;
        private double cantidadOtros;
        private int materialesOtros;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaterialDispensadoDTO {
        private String productoId;
        private String productoNombre;
        private String tipoMaterial;
        private double cantidadDispensada;
        private double participacionPct;
        private int movimientos;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SerieFisicaDiariaDTO {
        private LocalDate fecha;
        private String unidadMedida;
        private double cantidadDispensada;
        private double cantidadRecibidaCompra;
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
