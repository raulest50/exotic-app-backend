package exotic.app.planta.model.bi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InformeDiarioIngresoTerminadosReporteDTO {

    private LocalDate fecha;
    private Integer mpsId;
    private String mpsEstado;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private ResumenDTO resumen;
    private List<ConsolidadoCategoriaDTO> consolidadoCategorias;
    private List<DetalleReferenciaDTO> detalleReferencias;
    private List<MovimientoDTO> movimientos;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumenDTO {
        private double unidadesPlaneadas;
        private double unidadesProducidas;
        private double unidadesProducidasDiaAnterior;
        private double capacidadProductivaDia;
        private Double rendimientoPlaneacionPct;
        private Double cumplimientoReferenciasPct;
        private Double rendimientoOperativoPct;
        private Double tendenciaVsDiaAnteriorPct;
        private int referenciasPlaneadas;
        private int referenciasProducidas;
        private int referenciasPlaneadasProducidas;
        private int referenciasNoPlaneadas;
        private int categoriasConCapacidad;
        private int categoriasSinCapacidad;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsolidadoCategoriaDTO {
        private Integer categoriaId;
        private String categoriaNombre;
        private double unidadesPlaneadas;
        private double unidadesProducidas;
        private double capacidadProductivaDia;
        private Double rendimientoPlaneacionPct;
        private Double rendimientoOperativoPct;
        private int referenciasPlaneadas;
        private int referenciasProducidas;
        private int referenciasPlaneadasProducidas;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetalleReferenciaDTO {
        private String productoId;
        private String productoNombre;
        private Integer categoriaId;
        private String categoriaNombre;
        private double cantidadPlaneada;
        private double cantidadProducida;
        private double diferencia;
        private Double rendimientoPlaneacionPct;
        private boolean planeado;
        private boolean producido;
        private boolean noPlaneado;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MovimientoDTO {
        private Integer movimientoId;
        private LocalDateTime fechaMovimiento;
        private Integer transaccionId;
        private Integer ordenProduccionId;
        private String productoId;
        private String productoNombre;
        private Integer categoriaId;
        private String categoriaNombre;
        private Double cantidad;
        private String unidad;
        private String almacen;
        private String loteBatchNumber;
        private LocalDate fechaVencimiento;
        private String observaciones;
    }
}
