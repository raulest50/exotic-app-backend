package exotic.app.planta.model.calidad.dto;

import exotic.app.planta.model.calidad.ResultadoControlProceso;
import exotic.app.planta.model.inventarios.EstadoCalidadLote;
import exotic.app.planta.model.produccion.batchrecord.DecisionCalidadBatchRecord;
import exotic.app.planta.model.produccion.batchrecord.EstadoBatchRecord;
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
import java.util.List;

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
        private Long loteId;
        private String lote;
        private EstadoCalidadLote estadoCalidadLote;
        private String productoId;
        private String productoNombre;
        private BigDecimal cantidadObtenida;
        private String unidadMedida;
        private LocalDateTime enviadoRevisionEn;
        private int controlesRequeridos;
        private int controlesConformes;
        private int controlesPendientes;
        private long desviacionesAbiertas;
        private boolean puedeLiberar;
        @Builder.Default
        private List<String> bloqueos = new ArrayList<>();
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
    }
}
