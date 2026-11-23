package exotic.app.planta.model.organizacion.personal.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import exotic.app.planta.model.organizacion.personal.RegistroVisitante.Genero;
import exotic.app.planta.model.organizacion.personal.RegistroVisitante.Motivo;
import exotic.app.planta.model.organizacion.personal.RegistroVisitante.TipoIngreso;

import java.time.Instant;

public final class VisitanteDTOs {
    private VisitanteDTOs() {
    }

    public record CrearRequest(String cedula, String nombreCompleto, Integer edad, Genero genero,
                               Motivo motivo, String motivoOtro, String cargoEmpresa, TipoIngreso tipoIngreso,
                               String areaPersonaVisita, Boolean aceptaDatosPersonales, String consentimientoVersion,
                               Boolean autorizaFirma, String firmaBase64) {
        /** Evita convertir numeros, decimales o ordinales en datos personales validos. */
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public static CrearRequest fromJson(
                @JsonProperty("cedula") JsonNode cedula,
                @JsonProperty("nombreCompleto") JsonNode nombreCompleto,
                @JsonProperty("edad") JsonNode edad,
                @JsonProperty("genero") JsonNode genero,
                @JsonProperty("motivo") JsonNode motivo,
                @JsonProperty("motivoOtro") JsonNode motivoOtro,
                @JsonProperty("cargoEmpresa") JsonNode cargoEmpresa,
                @JsonProperty("tipoIngreso") JsonNode tipoIngreso,
                @JsonProperty("areaPersonaVisita") JsonNode areaPersonaVisita,
                @JsonProperty("aceptaDatosPersonales") JsonNode aceptaDatosPersonales,
                @JsonProperty("consentimientoVersion") JsonNode consentimientoVersion,
                @JsonProperty("autorizaFirma") JsonNode autorizaFirma,
                @JsonProperty("firmaBase64") JsonNode firmaBase64) {
            return new CrearRequest(texto(cedula), texto(nombreCompleto), entero(edad), leerGenero(genero),
                    leerMotivo(motivo), texto(motivoOtro), texto(cargoEmpresa), leerTipoIngreso(tipoIngreso),
                    texto(areaPersonaVisita), booleano(aceptaDatosPersonales), texto(consentimientoVersion),
                    booleano(autorizaFirma), texto(firmaBase64));
        }
    }

    public record Confirmacion(Instant fechaRegistro) {
    }

    public record Registro(Long id, String cedula, String nombreCompleto, Integer edad,
                           Genero genero, Motivo motivo, String motivoOtro, Instant fechaRegistro,
                           String cargoEmpresa, TipoIngreso tipoIngreso, String areaPersonaVisita,
                           boolean firmaRegistrada) { }

    private static Boolean booleano(JsonNode valor) {
        if (valor == null || valor.isNull()) return null;
        if (!valor.isBoolean()) throw new IllegalArgumentException("Tipo de dato invalido.");
        return valor.booleanValue();
    }

    private static TipoIngreso leerTipoIngreso(JsonNode valor) {
        String codigo = texto(valor);
        if (codigo == null) return null;
        for (TipoIngreso tipo : TipoIngreso.values()) if (tipo.name().equals(codigo)) return tipo;
        throw new IllegalArgumentException("Tipo de ingreso invalido.");
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
