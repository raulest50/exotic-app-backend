package exotic.app.planta.model.produccion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class MpsFabricacionDTOs {
    private MpsFabricacionDTOs() {}

    @Data
    public static class GuardarRequest {
        @PositiveOrZero
        private Long version;
        @NotNull @Valid @Size(max = 1000)
        private List<@NotNull @Valid LineaRequest> propuestas;
    }

    @Data
    public static class LineaRequest {
        private Long id;
        @NotBlank
        private String semiTerminadoId;
        @NotNull @Positive @Digits(integer = 14, fraction = 4)
        private BigDecimal cantidad;
        @NotNull
        private LocalDateTime fechaInicio;
        @NotNull
        private LocalDateTime fechaFinal;
        @Size(max = 2000)
        private String observaciones;
    }

    @Data
    public static class LineaResponse {
        private Long id;
        private String semiTerminadoId;
        private String semiTerminadoNombre;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal cantidad;
        private String unidadMedida;
        private LocalDateTime fechaInicio;
        private LocalDateTime fechaFinal;
        private String observaciones;
        private boolean elegible;
    }

    @Data
    public static class ProgramaResponse {
        private Long id;
        private Long version;
        private LocalDate weekStartDate;
        private LocalDate weekEndDate;
        private LocalDateTime actualizadoEn;
        private String actualizadoPor;
        private List<LineaResponse> propuestas = new ArrayList<>();
    }

    @Data
    public static class OrdenResponse {
        private Long ordenFabricacionId;
        private String semiTerminadoId;
        private String semiTerminadoNombre;
        private BigDecimal cantidadPlanificada;
        private String unidadMedida;
        private String lote;
        private String estado;
        private LocalDateTime fechaInicioSemana;
        private boolean usaFechaCreacion;
        private LocalDateTime fechaFinalPlanificada;
    }
}
