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
public class InformeGlobalProduccionDTO {

    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    private String modoFecha;
    private int diasRango;
    private List<Integer> mpsIds;
    private ResumenDTO resumen;
    private List<ConsolidadoCategoriaDTO> consolidadoCategorias;
    private List<DetalleReferenciaDTO> detalleReferencias;
    private List<NotaDTO> notas;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumenDTO {
        private double unidadesPlaneadas;
        private double unidadesProducidas;
        private double unidadesProducidasPeriodoAnterior;
        private double capacidadProductivaPeriodo;
        private Double rendimientoPlaneacionPct;
        private Double cumplimientoReferenciasPct;
        private Double capacidadUtilizadaPct;
        private Double tendenciaProduccionPct;
        private int referenciasPlaneadas;
        private int referenciasProducidas;
        private int referenciasPlaneadasProducidas;
        private int referenciasNoPlaneadas;
        private int categoriasConCapacidad;
        private int categoriasSinCapacidad;
        private int movimientosProduccion;
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
        private double capacidadProductivaPeriodo;
        private Double rendimientoPlaneacionPct;
        private Double cumplimientoReferenciasPct;
        private Double capacidadUtilizadaPct;
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
    public static class NotaDTO {
        private String tipo;
        private String mensaje;
    }
}
