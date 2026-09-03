package exotic.app.planta.service.controles;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.controles.RegistroIdempotenciaControl;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.controles.RegistroIdempotenciaControlRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class ControlIdempotencyService {
    public static final String HEADER = "Idempotency-Key";

    private final RegistroIdempotenciaControlRepo repo;
    private final ObjectMapper objectMapper;

    /**
     * Ejecuta y registra una mutacion en la misma transaccion. El INSERT con
     * ON CONFLICT seguido de SELECT FOR UPDATE serializa solicitudes concurrentes
     * con el mismo alcance sin convertir una colision esperada en rollback-only.
     */
    @Transactional
    public <T> T ejecutar(
            User actor,
            String accion,
            String recurso,
            String clave,
            Object payload,
            Class<T> tipoRespuesta,
            Supplier<T> mutacion) {
        validarAlcance(actor, accion, recurso, clave);
        String claveNormalizada = clave.trim();
        String huella = huella(payload);

        repo.insertarSiAusente(
                actor.getId(), accion, recurso, claveNormalizada, huella, AppTime.now());
        RegistroIdempotenciaControl registro = repo.buscarParaActualizar(
                        actor.getId(), accion, recurso, claveNormalizada)
                .orElseThrow(() -> new IllegalStateException(
                        "No fue posible adquirir el registro de idempotencia."));

        if (!MessageDigest.isEqual(
                registro.getHuellaPayload().getBytes(StandardCharsets.US_ASCII),
                huella.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalStateException(
                    "La Idempotency-Key ya fue utilizada para este recurso con un payload diferente.");
        }
        if (registro.completada()) {
            return leerRespuesta(registro.getRespuestaJson(), tipoRespuesta);
        }

        T respuesta = mutacion.get();
        if (respuesta == null) {
            throw new IllegalStateException("La mutacion idempotente no produjo una respuesta almacenable.");
        }
        registro.setRespuestaJson(escribir(respuesta));
        registro.setCompletadaEn(AppTime.now());
        repo.save(registro);
        return respuesta;
    }

    private void validarAlcance(
            User actor, String accion, String recurso, String clave) {
        if (actor == null || actor.getId() == null) {
            throw new IllegalArgumentException("El actor autenticado es obligatorio.");
        }
        if (accion == null || accion.isBlank() || accion.length() > 80) {
            throw new IllegalArgumentException("La accion idempotente no es valida.");
        }
        if (recurso == null || recurso.isBlank() || recurso.length() > 180) {
            throw new IllegalArgumentException("El recurso idempotente no es valido.");
        }
        if (clave == null || clave.isBlank() || clave.trim().length() > 200
                || clave.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "Idempotency-Key es obligatoria, no puede exceder 200 caracteres ni contener controles.");
        }
    }

    private String huella(Object payload) {
        try {
            ObjectMapper canonical = objectMapper.copy()
                    .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            byte[] bytes = canonical.writeValueAsBytes(payload);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("No fue posible calcular la huella idempotente.", exception);
        }
    }

    private String escribir(Object respuesta) {
        try {
            return objectMapper.writeValueAsString(respuesta);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No fue posible almacenar la respuesta idempotente.", exception);
        }
    }

    private <T> T leerRespuesta(String json, Class<T> tipoRespuesta) {
        try {
            return objectMapper.readValue(json, tipoRespuesta);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No fue posible recuperar la respuesta idempotente.", exception);
        }
    }
}
