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
    private AnaliticaAreasDTO analiticaAreas;
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
    public static class AnaliticaAreasDTO {
        private boolean disponible;
        private String mensaje;
        private LocalDate fechaDesdePeriodoAnterior;
        private LocalDate fechaHastaPeriodoAnterior;
        private List<AreaOperativaAnaliticaDTO> areas;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AreaOperativaAnaliticaDTO {
        private Integer areaId;
        private String areaNombre;
        private String estado;
        private String confiabilidad;
        private List<String> motivos;
        private boolean comparacionDisponible;
        private Double coberturaUnidadPct;
        private List<ProduccionUnidadAreaDTO> produccion;
        private MetricasFlujoAreaDTO actual;
        private MetricasFlujoAreaDTO anterior;
        private List<SerieFlujoAreaDTO> serieActual;
        private List<SerieFlujoAreaDTO> serieAnterior;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProduccionUnidadAreaDTO {
        private String fuente;
        private String unidad;
        private double cantidadActual;
        private double cantidadAnterior;
        private Double variacionPct;
        private Double cantidadEquivalenteActual;
        private Double cantidadEquivalenteAnterior;
        private String unidadEquivalente;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricasFlujoAreaDTO {
        private int entradas;
        private int salidas;
        private int trabajoListo;
        private double ritmoSalidaDiario;
        private Double diasBacklog;
        private Double medianaMinutosEspera;
        private Double medianaMinutosProceso;
        private int muestrasEspera;
        private int muestrasProceso;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SerieFlujoAreaDTO {
        private LocalDate fecha;
        private int indiceDia;
        private int entradas;
        private int salidas;
        private int backlogCierre;
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
