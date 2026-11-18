package exotic.app.planta.model.organizacion.personal.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import exotic.app.planta.model.organizacion.personal.RegistroVisitante;
import exotic.app.planta.model.organizacion.personal.RegistroVisitante.Genero;

import java.time.Instant;

public final class VisitanteDTOs {
    private VisitanteDTOs() {
    }

    public record CrearRequest(String cedula, String nombreCompleto, Integer edad, Genero genero) {
        /** Evita convertir numeros, decimales o ordinales en datos personales validos. */
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public static CrearRequest fromJson(
                @JsonProperty("cedula") JsonNode cedula,
                @JsonProperty("nombreCompleto") JsonNode nombreCompleto,
                @JsonProperty("edad") JsonNode edad,
                @JsonProperty("genero") JsonNode genero) {
            return new CrearRequest(texto(cedula), texto(nombreCompleto), entero(edad), leerGenero(genero));
        }
    }

    public record Confirmacion(Instant fechaRegistro) {
    }

    public record Registro(Long id, String cedula, String nombreCompleto, Integer edad,
                           Genero genero, Instant fechaRegistro) {
        public static Registro fromEntity(RegistroVisitante registro) {
            return new Registro(registro.getId(), registro.getCedula(), registro.getNombreCompleto(),
                    registro.getEdad(), registro.getGenero(), registro.getFechaRegistro());
        }
    }

    private static String texto(JsonNode valor) {
        if (valor == null || valor.isNull()) return null;
        if (!valor.isTextual()) throw new IllegalArgumentException("Tipo de dato invalido.");
        return valor.textValue();
    }

    private static Integer entero(JsonNode valor) {
        if (valor == null || valor.isNull()) return null;
        if (!valor.isIntegralNumber() || !valor.canConvertToInt()) {
            throw new IllegalArgumentException("Tipo de dato invalido.");
        }
        return valor.intValue();
    }

    private static Genero leerGenero(JsonNode valor) {
        String codigo = texto(valor);
        if (codigo == null) return null;
        for (Genero genero : Genero.values()) {
            if (genero.name().equals(codigo)) return genero;
        }
        throw new IllegalArgumentException("Genero invalido.");
    }
}
