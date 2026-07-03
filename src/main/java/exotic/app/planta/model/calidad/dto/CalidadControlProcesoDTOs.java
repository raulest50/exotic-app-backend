package exotic.app.planta.model.calidad.dto;

import exotic.app.planta.model.calidad.EstadoControlProcesoPlantilla;
import exotic.app.planta.model.calidad.TipoCaracteristicaControlProceso;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class CalidadControlProcesoDTOs {

    private CalidadControlProcesoDTOs() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AreaOperativaResumen {
        private Integer areaId;
        private String nombre;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductoResumen {
        private String productoId;
        private String nombre;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoteProduccionResumen {
        private Long id;
        private String batchNumber;
        private LocalDate productionDate;
        private LocalDate expirationDate;
        private Integer ordenProduccionId;
        private ProductoResumen producto;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaracteristicaRequest {
        private String nombre;
        private TipoCaracteristicaControlProceso tipo;
        private String unidad;
        private Integer orden;
        private Integer cantidadMuestras;
        private Integer unidadesPorMuestra;
        private Double limiteInferior;
        private Double limiteSuperior;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaracteristicaResponse {
        private Long id;
        private String nombre;
        private TipoCaracteristicaControlProceso tipo;
        private String unidad;
        private Integer orden;
        private Integer cantidadMuestras;
        private Integer unidadesPorMuestra;
        private Double limiteInferior;
        private Double limiteSuperior;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlantillaRequest {
        private Integer areaOperativaId;
        private List<CaracteristicaRequest> caracteristicas;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlantillaResponse {
        private Long id;
        private AreaOperativaResumen areaOperativa;
        private Integer version;
        private EstadoControlProcesoPlantilla estado;
        private List<CaracteristicaResponse> caracteristicas;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrepararEjecucionResponse {
        private PlantillaResponse plantilla;
        private LoteProduccionResumen lote;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LecturaRequest {
        private Integer indiceUnidad;
        private Double valorNumerico;
        private Boolean valorBooleano;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MuestraRequest {
        private Long caracteristicaId;
        private Integer numeroMuestra;
        private List<LecturaRequest> lecturas;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EjecucionRequest {
        private Long plantillaId;
        private Long loteId;
        private String observaciones;
        private List<MuestraRequest> muestras;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LecturaResponse {
        private Long id;
        private Integer indiceUnidad;
        private Double valorNumerico;
        private Boolean valorBooleano;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MuestraResponse {
        private Long id;
        private Long caracteristicaId;
        private String caracteristicaNombre;
        private TipoCaracteristicaControlProceso tipo;
        private String unidad;
        private Integer numeroMuestra;
        private List<LecturaResponse> lecturas;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EjecucionListItemResponse {
        private Long id;
        private Long plantillaId;
        private Integer plantillaVersion;
        private AreaOperativaResumen areaOperativa;
        private LoteProduccionResumen lote;
        private String usuarioUsername;
        private String usuarioNombreCompleto;
        private LocalDateTime fechaRegistro;
        private String observaciones;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EjecucionDetalleResponse {
        private Long id;
        private Long plantillaId;
        private Integer plantillaVersion;
        private AreaOperativaResumen areaOperativa;
        private LoteProduccionResumen lote;
        private String usuarioUsername;
        private String usuarioNombreCompleto;
        private LocalDateTime fechaRegistro;
        private String observaciones;
        private List<MuestraResponse> muestras;
    }
}
