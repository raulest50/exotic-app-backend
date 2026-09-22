package exotic.app.planta.model.compras.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import exotic.app.planta.model.compras.OrigenCierreOcm;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.io.IOException;
import java.util.List;

public final class OcmCierreDTOs {
    private OcmCierreDTOs() {}

    public enum Modo { DESACTIVADO, RECEPCION_COMPLETA, PLAZO }

    public record Config(Modo modo, Integer dias, LocalDateTime activadoDesde) {
        public static Config desactivada() { return new Config(Modo.DESACTIVADO, null, null); }
    }

    public record ConfigWrite(@NotNull Modo modo,
            @Positive @JsonDeserialize(using = EnteroEstrictoDeserializer.class) Integer dias) {}

    public static class EnteroEstrictoDeserializer extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return (Integer) context.handleUnexpectedToken(Integer.class, parser);
            }
            return parser.getIntValue();
        }
    }

    public record EstadoRecepcion(
            int ordenCompraId, int estado, boolean recepcionCompleta,
            LocalDateTime fechaRecepcionCompleta, LocalDateTime fechaCierreAutomaticoPrevista,
            LocalDateTime fechaCierre, OrigenCierreOcm origenCierre, String usuarioCierreUsername) {}

    public record Candidata(int ordenCompraId, String proveedor, LocalDateTime fechaEmision,
                            LocalDateTime fechaRecepcionCompleta, LocalDateTime fechaCierreAutomaticoPrevista) {}

    public record CierreWrite(@NotEmpty @Size(max = 100)
            @JsonDeserialize(contentUsing = EnteroEstrictoDeserializer.class)
            List<@NotNull @Positive Integer> ordenCompraIds) {}

    public record Incidencia(int ordenCompraId, String motivo) {}

    public record ResultadoCierre(List<Integer> cerradas, List<Incidencia> omitidas, List<Incidencia> fallidas) {}
}
