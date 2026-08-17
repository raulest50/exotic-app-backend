package exotic.app.planta.model.produccion.dto;

import exotic.app.planta.model.calidad.ResultadoControlProceso;
import exotic.app.planta.model.inventarios.EstadoCalidadLote;
import exotic.app.planta.model.produccion.batchrecord.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class BatchRecordDTOs {

    private BatchRecordDTOs() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListItem {
        private Long id;
        private String codigo;
        private EstadoBatchRecord estado;
        private int revisionDocumental;
        private Integer ordenProduccionId;
        private Long ordenFabricacionId;
        private String lote;
        private EstadoCalidadLote estadoCalidadLote;
        private String productoId;
        private String productoNombre;
        private String tipoProducto;
        private BigDecimal cantidadPlanificada;
        private BigDecimal cantidadObtenida;
        private String unidadMedida;
        private LocalDateTime creadoEn;
        private LocalDateTime enviadoRevisionEn;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Detail {
        private ListItem resumen;
        private Long manufacturingVersionId;
        private Integer manufacturingVersionNumber;
        private String creadoPor;
        private LocalDateTime iniciadoEn;
        private LocalDateTime cerradoEn;
        private String observaciones;
        @Builder.Default
        private List<Etapa> etapas = new ArrayList<>();
        @Builder.Default
        private List<Consumo> consumos = new ArrayList<>();
        @Builder.Default
        private List<Control> controles = new ArrayList<>();
        @Builder.Default
        private List<Desviacion> desviaciones = new ArrayList<>();
        @Builder.Default
        private List<Correccion> correcciones = new ArrayList<>();
        @Builder.Default
        private List<Firma> firmas = new ArrayList<>();
        @Builder.Default
        private List<Revision> revisiones = new ArrayList<>();
        @Builder.Default
        private List<DecisionCalidad> decisionesCalidad = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Etapa {
        private Long id;
        private int secuencia;
        private String nombre;
        private Integer areaOperativaId;
        private String areaOperativaNombre;
        private EstadoBatchRecordEtapa estado;
        private LocalDateTime iniciadaEn;
        private LocalDateTime completadaEn;
        private String reportadaPor;
        private String observaciones;
        private Long plantillaControlId;
        private Integer plantillaControlVersion;
        private Long seguimientoEventoOrigenId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Consumo {
        private Long id;
        private String productoId;
        private String productoNombre;
        private Long loteOrigenId;
        private String loteOrigen;
        private Integer movimientoId;
        private TipoRegistroConsumoBatchRecord tipo;
        private BigDecimal cantidad;
        private String unidadMedida;
        private LocalDateTime registradoEn;
        private String registradoPor;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Control {
        private Long id;
        private Long etapaId;
        private Long plantillaId;
        private Integer plantillaVersion;
        private Integer areaOperativaId;
        private String areaOperativaNombre;
        private ResultadoControlProceso resultado;
        private LocalDateTime fechaRegistro;
        private String registradoPor;
        private String observaciones;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Desviacion {
        private Long id;
        private Long etapaId;
        private String codigo;
        private String descripcion;
        private EstadoBatchRecordDesviacion estado;
        private LocalDateTime ocurridaEn;
        private LocalDateTime detectadaEn;
        private String detectadaPor;
        private OrigenDesviacionBatchRecord origen;
        private String accionInmediata;
        private String evaluacionImpacto;
        private String causaRaiz;
        private String accionesCorrectivasPreventivas;
        private String resolucion;
        private LocalDateTime resueltaEn;
        private String resueltaPor;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Correccion {
        private Long id;
        private Long etapaId;
        private Long eventoCorreccionId;
        private Long eventoRevertidoId;
        private String valorAnterior;
        private String valorNuevo;
        private String motivo;
        private LocalDateTime corregidaEn;
        private String corregidaPor;
        private Integer revision;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Firma {
        private Long id;
        private Long etapaId;
        private Long seguimientoEventoId;
        private Integer revision;
        private AlcanceFirmaBatchRecord alcance;
        private DecisionFirmaBatchRecord decision;
        private LocalDateTime firmadoEn;
        private String usernameFirmante;
        private String nombreFirmante;
        private String cedulaFirmante;
        private String rolFirmante;
        private String manifestacion;
        private String hashContenidoFirmado;
        private Long firmaVisualVersionId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Revision {
        private Long id;
        private int numero;
        private TipoRevisionBatchRecord tipo;
        private String contenidoSha256;
        private String esquemaVersion;
        private String plantillaPdfVersion;
        private LocalDateTime creadaEn;
        private String creadaPor;
        private String motivo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DecisionCalidad {
        private Long id;
        private DecisionCalidadBatchRecord decision;
        private String motivo;
        private LocalDateTime decididaEn;
        private String decididaPor;
        private Integer revision;
        private Long firmaId;
    }
}
