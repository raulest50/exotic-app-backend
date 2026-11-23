package exotic.app.planta.service.rehumanos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Aviso exclusivo del registro de ingresos; no reutiliza consentimientos de Usuarios. */
@Service
public class VisitantesPrivacidadService {
    private static final String RESPONSABLE = "Laboratorios Novum S.A.S.";
    private static final String AUTORIZACION_DATOS = "Soy mayor de edad y autorizo el tratamiento de mis datos para las finalidades informadas.";
    private static final String AUTORIZACION_FIRMA = "Autorizo guardar y consultar la imagen de mi firma, tratada como dato sensible, como constancia de este registro. Puedo continuar sin firmar. Al firmar confirmo los datos registrados.";
    private final Aviso aviso;

    public VisitantesPrivacidadService(@Value("${VISITANTES_POLITICA_DATOS_URL:}") String politicaUrl,
                                      @Value("${VISITANTES_CONTACTO_DATOS:}") String contacto,
                                      @Value("${VISITANTES_TELEFONO_DATOS:}") String telefono) {
        politicaUrl = politicaUrl.strip(); contacto = contacto.strip(); telefono = telefono.strip();
        boolean disponible = urlSegura(politicaUrl) && textoValido(contacto, 200) && textoValido(telefono, 60);
        String texto = disponible ? RESPONSABLE + " recolecta, conserva y consulta estos datos para registrar su visita "
                + "y apoyar el control de ingreso a planta. Puede consultar gratis sus datos, conocer su uso, actualizarlos o rectificarlos, "
                + "pedir prueba de autorización y solicitar revocatoria o supresión cuando proceda. Puede presentar consultas o reclamos "
                + "ante Novum y quejas ante la SIC. Responsable y consultas o reclamos: "
                + contacto + " · Tel. " + telefono + "." : "";
        String version = disponible ? "visitas-" + sha256(texto + "\n" + politicaUrl + "\n" + AUTORIZACION_DATOS + "\n" + AUTORIZACION_FIRMA).substring(0, 24) : "";
        aviso = new Aviso(disponible, version, texto, disponible ? politicaUrl : "", AUTORIZACION_DATOS, AUTORIZACION_FIRMA);
    }

    public Aviso obtenerAviso() { return aviso; }

    public Aviso exigirAceptacion(Boolean acepta, String version) {
        if (!aviso.disponible()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        if (!Boolean.TRUE.equals(acepta) || !aviso.version().equals(version)) {
            throw new IllegalArgumentException("Revise el aviso vigente y autorice el tratamiento de datos.");
        }
        return aviso;
    }

    private static boolean textoValido(String texto, int maximo) {
        return !texto.isBlank() && texto.length() <= maximo
                && texto.codePoints().noneMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT);
    }

    private static boolean urlSegura(String texto) {
        try {
            URI uri = URI.create(texto);
            return textoValido(texto, 1000) && "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) { return false; }
    }

    private static String sha256(String texto) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 no disponible", e); }
    }

    public record Aviso(boolean disponible, String version, String texto, String politicaUrl,
                        String autorizacionDatos, String autorizacionFirma) {
        public String textoAceptado() { return texto + "\nPolítica de tratamiento: " + politicaUrl + "\n" + autorizacionDatos; }
    }
}
