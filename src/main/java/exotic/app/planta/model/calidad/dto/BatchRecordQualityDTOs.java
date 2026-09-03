package exotic.app.planta.model.calidad.dto;

import exotic.app.planta.model.calidad.ResultadoControlProceso;
import exotic.app.planta.model.controles.dto.BloqueoControlDTO;
import exotic.app.planta.model.controles.dto.ControlRequeridoRevisionDTO;
import exotic.app.planta.model.controles.dto.ControlDTOs.EjecucionDetalleResponse;
import exotic.app.planta.model.controles.dto.ResumenControlesBatchRecordDTO;
import exotic.app.planta.model.inventarios.EstadoCalidadLote;
import exotic.app.planta.model.produccion.batchrecord.DecisionCalidadBatchRecord;
import exotic.app.planta.model.produccion.batchrecord.EstadoBatchRecord;
import exotic.app.planta.model.produccion.batchrecord.EstadoCicloRevisionBatchRecord;
import exotic.app.planta.model.produccion.batchrecord.OrigenCicloRevisionBatchRecord;
import exotic.app.planta.model.produccion.dto.BatchRecordDTOs;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BatchRecordQualityDTOs {

    private BatchRecordQualityDTOs() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InboxItem {
        private Long batchRecordId;
        private String codigo;
        private EstadoBatchRecord estado;
        private Integer ordenProduccionId;
        private Long ordenFabricacionId;
        private Long loteId;
        private String lote;
        private EstadoCalidadLote estadoCalidadLote;
        private String productoId;
        private String productoNombre;
        private BigDecimal cantidadObtenida;
        private String unidadMedida;
        private LocalDateTime enviadoRevisionEn;
        private long cicloRevisionActual;
        private EstadoCicloRevisionBatchRecord estadoCicloRevision;
        private OrigenCicloRevisionBatchRecord origenCicloRevision;
        private int controlesRequeridos;
        private int controlesConformes;
        private int controlesPendientes;
        private long desviacionesAbiertas;
        private ResumenControlesBatchRecordDTO resumenControles;
        private boolean puedeLiberar;
        @Builder.Default
        private List<String> bloqueos = new ArrayList<>();
        @Builder.Default
        private List<BloqueoControlDTO> bloqueosControl = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EtapaControl {
        private Long etapaId;
        private Integer secuencia;
        private Integer areaOperativaId;
        private String areaOperativaNombre;
        private String etapaNombre;
        private Long plantillaId;
        private Integer plantillaVersion;
        private Long ultimaEjecucionId;
        private ResultadoControlProceso ultimoResultado;
        private LocalDateTime ultimaEjecucionEn;
        private boolean pendiente;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewDetail {
        private BatchRecordDTOs.Detail expediente;
        private InboxItem evaluacion;
        /** Proyección neutral de controles de Proceso, siempre de solo lectura en Calidad. */
        @Builder.Default
        private List<ControlRequeridoRevisionDTO> controlesProceso = new ArrayList<>();
        /** Requisitos de Calidad visibles para el revisor sin conceder permiso de ejecución. */
        @Builder.Default
        private List<ControlRequeridoRevisionDTO> controlesCalidad = new ArrayList<>();
        /** Todas las mediciones de Calidad, incluidas repeticiones y lecturas originales. */
        @Builder.Default
        private List<EjecucionDetalleResponse> ejecucionesCalidad = new ArrayList<>();
        /** Evidencia del subsistema legado, conservada durante la transición. */
        @Builder.Default
        private List<EtapaControl> controles = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DecisionRequest {
        @NotNull
        private DecisionCalidadBatchRecord decision;

        @NotBlank
        @Size(max = 500)
        private String motivo;

        @Size(max = 100)
        private Set<Long> etapaIds = new LinkedHashSet<>();

        @Size(max = 200)
        private Set<Long> requisitoIds = new LinkedHashSet<>();

        @Size(max = 50)
        private Set<@NotBlank @Size(max = 120) String> seccionesDocumentales =
                new LinkedHashSet<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReaperturaRequest {
        @NotBlank
        @Size(max = 500)
        private String motivo;

        @NotBlank
        @Size(max = 4000)
        private String evidencia;

        @NotBlank
        @Size(max = 4000)
        private String alcance;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AprobarReaperturaRequest {
        @NotBlank
        @Size(max = 500)
        private String motivo;
    }
}
