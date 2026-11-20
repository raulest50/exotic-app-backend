package exotic.app.planta.model.compras.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Contratos de cálculo y confirmación de puntos de reorden; no exponen entidades JPA. */
public final class PuntosReordenDTOs {

    private PuntosReordenDTOs() {
    }

    public record Parametros(
            @NotNull(message = "La fecha inicial es obligatoria.") LocalDate fechaDesde,
            @NotNull(message = "La fecha final es obligatoria.") LocalDate fechaHasta,
            @NotNull(message = "El porcentaje de seguridad es obligatorio.")
            @DecimalMin(value = "0", message = "El porcentaje de seguridad debe ser mayor o igual a 0.")
            @DecimalMax(value = "100", message = "El porcentaje de seguridad debe ser menor o igual a 100.")
            @Digits(integer = 3, fraction = 6, message = "El porcentaje de seguridad admite hasta seis decimales.")
            BigDecimal porcentajeSeguridad,
            @NotNull(message = "El mínimo de días efectivos es obligatorio.")
            @Min(value = 1, message = "El mínimo de días efectivos debe ser al menos 1.")
            @Max(value = 3660, message = "El mínimo de días efectivos no puede superar 3660.")
            Integer minimoDiasEfectivos,
            List<@NotNull(message = "Las fechas excluidas no pueden contener valores nulos.") LocalDate> fechasExcluidas,
            Map<@NotBlank(message = "El código de material no puede estar vacío.") String,
                    @NotBlank(message = "El código de proveedor no puede estar vacío.") String> proveedoresPorMaterial
    ) {
        public Parametros {
            fechasExcluidas = fechasExcluidas == null ? List.of() : List.copyOf(fechasExcluidas);
            proveedoresPorMaterial = proveedoresPorMaterial == null ? Map.of()
                    : Collections.unmodifiableMap(new TreeMap<>(proveedoresPorMaterial));
        }
    }

    public record Fila(
            String productoId,
            String nombre,
            String unidad,
            String proveedorId,
            String proveedorNombre,
            Integer leadTimeNegociado,
            long comprasProveedor,
            boolean proveedorManual,
            BigDecimal consumoTotal,
            BigDecimal consumoDiario,
            BigDecimal stockSeguridad,
            double puntoReordenActual,
            BigDecimal puntoReordenNuevo,
            String estado,
            boolean seleccionadaPorDefecto
    ) {
    }

    public record CalculoResponse(
            String calculoHash,
            Parametros parametros,
            OffsetDateTime calculadoEn,
            int diasCalendario,
            int diasExcluidos,
            int diasEfectivos,
            int minimoDiasEfectivos,
            List<LocalDate> diasConDispensacion,
            List<Fila> filas
    ) {
        public CalculoResponse {
            diasConDispensacion = List.copyOf(diasConDispensacion);
            filas = List.copyOf(filas);
        }
    }

    public record PrepararRequest(
            @Valid @NotNull(message = "Los parámetros del cálculo son obligatorios.") Parametros parametros,
            @NotEmpty(message = "Debe seleccionar al menos un material.")
            List<@NotBlank(message = "El código de material seleccionado no puede estar vacío.") String> productoIds,
            @NotBlank(message = "El identificador del cálculo es obligatorio.") String calculoHash
    ) {
        public PrepararRequest {
            productoIds = productoIds == null ? null : List.copyOf(productoIds);
        }
    }

    public record TokenRequest(
            @NotBlank(message = "El token es obligatorio.")
            @Pattern(regexp = "\\d{4}", message = "El token debe contener exactamente cuatro dígitos.")
            String token
    ) {
    }

    public record PreparacionResponse(
            UUID preparacionId,
            OffsetDateTime expiraEn,
            String token,
            OffsetDateTime tokenExpiraEn,
            int intentosPermitidos,
            CalculoResponse calculo
    ) {
    }

    public record ResultadoResponse(
            UUID operacionId,
            String usuario,
            OffsetDateTime aplicadoEn,
            int actualizados,
            CalculoResponse calculo
    ) {
    }
}
