package exotic.app.planta.model.organizacion.personal.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import exotic.app.planta.model.organizacion.personal.RegistroVisitante;
import exotic.app.planta.model.organizacion.personal.RegistroVisitante.Genero;
import exotic.app.planta.model.organizacion.personal.RegistroVisitante.Motivo;

import java.time.Instant;

public final class VisitanteDTOs {
    private VisitanteDTOs() {
    }

    public record CrearRequest(String cedula, String nombreCompleto, Integer edad, Genero genero,
                               Motivo motivo, String motivoOtro) {
        /** Evita convertir numeros, decimales o ordinales en datos personales validos. */
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public static CrearRequest fromJson(
                @JsonProperty("cedula") JsonNode cedula,
                @JsonProperty("nombreCompleto") JsonNode nombreCompleto,
                @JsonProperty("edad") JsonNode edad,
                @JsonProperty("genero") JsonNode genero,
                @JsonProperty("motivo") JsonNode motivo,
                @JsonProperty("motivoOtro") JsonNode motivoOtro) {
            return new CrearRequest(texto(cedula), texto(nombreCompleto), entero(edad), leerGenero(genero),
                    leerMotivo(motivo), texto(motivoOtro));
        }
    }

    public record Confirmacion(Instant fechaRegistro) {
    }

    public record Registro(Long id, String cedula, String nombreCompleto, Integer edad,
                           Genero genero, Motivo motivo, String motivoOtro, Instant fechaRegistro) {
        public static Registro fromEntity(RegistroVisitante registro) {
            return new Registro(registro.getId(), registro.getCedula(), registro.getNombreCompleto(),
                    registro.getEdad(), registro.getGenero(), registro.getMotivo(), registro.getMotivoOtro(), registro.getFechaRegistro());
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
        if (Genero.MASCULINO.name().equals(codigo)) return Genero.MASCULINO;
        if (Genero.FEMENINO.name().equals(codigo)) return Genero.FEMENINO;
        throw new IllegalArgumentException("Genero invalido.");
    }

    private static Motivo leerMotivo(JsonNode valor) {
        String codigo = texto(valor);
        if (codigo == null) return null;
        for (Motivo motivo : Motivo.values()) {
            if (motivo.name().equals(codigo)) return motivo;
        }
        throw new IllegalArgumentException("Motivo invalido.");
    }
}
