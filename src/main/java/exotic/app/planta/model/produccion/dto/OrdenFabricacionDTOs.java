package exotic.app.planta.model.produccion.dto;

import exotic.app.planta.model.inventarios.EstadoCalidadLote;
import exotic.app.planta.model.produccion.fabricacion.EstadoOrdenFabricacion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class OrdenFabricacionDTOs {

    private OrdenFabricacionDTOs() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SemiterminadoOption {
        private String productoId;
        private String nombre;
        private String unidadMedida;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank
        private String semiTerminadoId;

        @NotNull
        @Positive
        private BigDecimal cantidadPlanificada;

        @NotBlank
        @Size(max = 80)
        private String lote;

        private LocalDateTime fechaLanzamiento;
        private LocalDateTime fechaFinalPlanificada;
        private Long responsableId;

        @Size(max = 2000)
        private String observaciones;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long ordenFabricacionId;
        private EstadoOrdenFabricacion estado;
        private String semiTerminadoId;
        private String semiTerminadoNombre;
        private Long manufacturingVersionId;
        private Integer manufacturingVersionNumber;
        private BigDecimal cantidadPlanificada;
        private String unidadMedida;
        private Long loteId;
        private String lote;
        private EstadoCalidadLote estadoCalidadLote;
        private Long batchRecordId;
        private String batchRecordCodigo;
        private LocalDateTime fechaCreacion;
        private LocalDateTime fechaLanzamiento;
        private LocalDateTime fechaFinalPlanificada;
        private String creadaPor;
        private String responsable;
        private String observaciones;
        private Integer ordenProduccionOrigenId;
        private LocalDateTime liberadaEn;
        private String politicaDispensacionInicio;
        private String estadoDispensacionMateriales;
        @Builder.Default
        private List<OperacionResponse> operaciones = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperacionResponse {
        private Long id;
        private Long ordenFabricacionId;
        private String frontendNodeId;
        private Integer procesoProduccionId;
        private String procesoNombre;
        private Integer areaOperativaId;
        private String areaOperativaNombre;
        private int posicionSecuencia;
        private int estado;
        private String estadoDescripcion;
        private LocalDateTime fechaEstadoActual;
        private LocalDateTime fechaVisible;
        private LocalDateTime fechaCompletado;
        private String usuarioReporta;
        private String observaciones;
        private Long batchRecordEtapaId;
        private Long poeDocumentoVersionId;
        private Integer poeVersion;
        private String poeNombreArchivo;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperacionTransitionRequest {
        @Size(max = 500)
        private String observaciones;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperacionCompletarRequest {
        @Size(max = 500)
        private String observaciones;
        private BigDecimal cantidadObtenida;
        private LocalDate fechaVencimiento;
        @Size(max = 500)
        private String motivoDiferenciaCantidad;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CorreccionOperacionRequest {
        @NotNull
        private Integer estadoEsperado;
        @NotNull
        private Integer estadoDestino;
        @NotBlank
        @Size(max = 500)
        private String motivo;
    }
}
