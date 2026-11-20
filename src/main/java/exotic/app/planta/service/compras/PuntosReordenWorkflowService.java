package exotic.app.planta.service.compras;

import exotic.app.planta.config.TimeZoneConfig;
import exotic.app.planta.model.compras.dto.PuntosReordenDTOs.*;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.producto.MaterialRepo;
import exotic.app.planta.service.productos.PuntoReordenPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Preparaciones acotadas y temporales: dos confirmaciones y una unica escritura transaccional. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PuntosReordenWorkflowService {
    private static final int MAX_PREPARACIONES = 100;
    private static final int MAX_ACTIVAS_POR_USUARIO = 3;
    private static final int MAX_INTENTOS = 5;
    private static final ZoneId BOGOTA = ZoneId.of(TimeZoneConfig.APP_TIME_ZONE_ID);

    private final PuntosReordenService calculos;
    private final MaterialRepo materialRepo;
    private final PlatformTransactionManager transactionManager;
    private final Clock applicationClock;
    private final Map<UUID, Preparacion> preparaciones = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public PreparacionResponse preparar(PrepararRequest request, User usuario) {
        CalculoResponse calculo = calculos.calcular(request.parametros());
        if (!Objects.equals(calculo.calculoHash(), request.calculoHash())) {
            throw conflicto("Los datos cambiaron. Actualice el calculo y revise los materiales antes de continuar.");
        }
        if (calculo.diasEfectivos() < calculo.minimoDiasEfectivos()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El periodo no alcanza el minimo de dias efectivos seleccionado.");
        }
        if (request.productoIds() == null || request.productoIds().isEmpty()
                || request.productoIds().size() > 10000
                || request.productoIds().stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccione al menos un material valido.");
        }
        Set<String> ids = new TreeSet<>(request.productoIds());
        if (ids.size() != request.productoIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La seleccion contiene materiales repetidos.");
        }
        List<Fila> filas = calculo.filas().stream().filter(fila -> ids.contains(fila.productoId())).toList();
        if (filas.size() != ids.size() || filas.stream().anyMatch(fila ->
                !"LISTO".equals(fila.estado()) || fila.puntoReordenNuevo() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Hay materiales seleccionados sin consumo, proveedor o lead time suficiente para calcular.");
        }
        if (filas.stream().noneMatch(fila -> BigDecimal.valueOf(fila.puntoReordenActual())
                .compareTo(fila.puntoReordenNuevo()) != 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La seleccion no contiene cambios de punto de reorden.");
        }
        OffsetDateTime ahora = ahora();
        Preparacion preparacion = new Preparacion(UUID.randomUUID(), usuario.getId(),
                calculo.calculoHash(), soloSeleccionadas(calculo, filas), ahora.plusMinutes(30));
        String token = emitirToken(preparacion);
        synchronized (preparaciones) {
            limpiarVencidas(ahora);
            long activas = preparaciones.values().stream().filter(p -> p.usuarioId.equals(usuario.getId())
                    && p.fase != Fase.APLICADO).count();
            if (preparaciones.size() >= MAX_PREPARACIONES || activas >= MAX_ACTIVAS_POR_USUARIO) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Hay varias propuestas pendientes. Cancele las anteriores o espere a que expiren.");
            }
            preparaciones.put(preparacion.id, preparacion);
        }
        return respuesta(preparacion, token);
    }

    public PreparacionResponse continuar(UUID id, String token, User usuario) {
        Preparacion preparacion = buscar(id, usuario);
        synchronized (preparacion) {
            validarVigencia(preparacion);
            if (preparacion.fase != Fase.CONFIGURACION) {
                throw conflicto("La propuesta ya paso a revision. Regrese a la configuracion para preparar otra.");
            }
            validarToken(preparacion, token);
            preparacion.fase = Fase.REVISION;
            return respuesta(preparacion, emitirToken(preparacion));
        }
    }

    public ResultadoResponse confirmar(UUID id, String token, User usuario) {
        Preparacion preparacion = buscar(id, usuario);
        synchronized (preparacion) {
            validarVigencia(preparacion);
            if (preparacion.fase == Fase.APLICADO) return preparacion.resultado;
            if (preparacion.fase != Fase.REVISION) {
                throw conflicto("Primero debe revisar la propuesta y confirmar el primer token.");
            }
            validarToken(preparacion, token);
            TransactionTemplate transaccion = new TransactionTemplate(transactionManager);
            transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            try {
                // execute termina el commit antes de publicar el resultado y liberar este monitor.
                ResultadoResponse resultado = Objects.requireNonNull(transaccion.execute(status ->
                        aplicar(preparacion, usuario)));
                preparacion.resultado = resultado;
                preparacion.tokenHash = null;
                preparacion.fase = Fase.APLICADO;
                log.info("Actualizacion PR: operacion={}, usuario={}, materiales={}",
                        preparacion.id, usuario.getUsername(), resultado.actualizados());
                return resultado;
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode().value() == 409) invalidar(preparacion);
                throw exception;
            } catch (TransientDataAccessException exception) {
                invalidar(preparacion);
                throw conflicto("Otro usuario modifico los datos durante la confirmacion. Actualice el calculo.");
            }
        }
    }

    public void cancelar(UUID id, User usuario) {
        Preparacion preparacion = preparaciones.get(id);
        if (preparacion == null) return;
        if (!preparacion.usuarioId.equals(usuario.getId())) throw noDisponible();
        synchronized (preparacion) {
            if (preparacion.fase != Fase.APLICADO) {
                preparacion.fase = Fase.CANCELADO;
                preparacion.tokenHash = null;
                preparaciones.remove(id, preparacion);
            }
        }
    }

    private ResultadoResponse aplicar(Preparacion preparacion, User usuario) {
        List<String> ids = preparacion.calculo.filas().stream().map(Fila::productoId).toList();
        Map<String, Material> bloqueados = materialRepo.findAllByProductoIdInForUpdate(ids).stream()
                .collect(Collectors.toMap(Material::getProductoId, Function.identity()));
        CalculoResponse vigente = calculos.calcular(preparacion.calculo.parametros());
        if (!preparacion.huellaCompleta.equals(vigente.calculoHash())) {
            throw conflicto("El consumo, los proveedores o los materiales cambiaron. Vuelva a calcular y revisar.");
        }
        List<Material> cambios = new ArrayList<>();
        for (Fila fila : preparacion.calculo.filas()) {
            Material material = bloqueados.get(fila.productoId());
            if (material == null || !material.isInventareable()
                    || Double.compare(material.getPuntoReorden(), fila.puntoReordenActual()) != 0) {
                throw conflicto("Uno de los materiales cambio desde la revision. No se aplico ningun punto de reorden.");
            }
            double nuevo = fila.puntoReordenNuevo().doubleValue();
            PuntoReordenPolicy.validate(nuevo);
            if (Double.compare(material.getPuntoReorden(), nuevo) != 0) {
                material.setPuntoReorden(nuevo);
                cambios.add(material);
            }
        }
        materialRepo.saveAllAndFlush(cambios);
        return new ResultadoResponse(preparacion.id, usuario.getUsername(), ahora(), cambios.size(), preparacion.calculo);
    }

    private Preparacion buscar(UUID id, User usuario) {
        Preparacion preparacion = preparaciones.get(id);
        if (preparacion == null || !preparacion.usuarioId.equals(usuario.getId())) throw noDisponible();
        return preparacion;
    }

    private void validarVigencia(Preparacion preparacion) {
        if (preparaciones.get(preparacion.id) != preparacion
                || !preparacion.expiraEn.isAfter(ahora()) || preparacion.fase == Fase.CANCELADO) {
            invalidar(preparacion);
            throw noDisponible();
        }
    }

    private void invalidar(Preparacion preparacion) {
        preparacion.fase = Fase.CANCELADO;
        preparacion.tokenHash = null;
        preparaciones.remove(preparacion.id, preparacion);
    }

    private void validarToken(Preparacion preparacion, String token) {
        if (preparacion.tokenHash == null || !preparacion.tokenExpiraEn.isAfter(ahora())) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "El token expiro. Regrese a la configuracion y prepare de nuevo la propuesta.");
        }
        if (token == null || !MessageDigest.isEqual(preparacion.tokenHash, hashToken(preparacion.id, token))) {
            preparacion.intentos++;
            int restantes = MAX_INTENTOS - preparacion.intentos;
            if (restantes == 0) {
                preparaciones.remove(preparacion.id, preparacion);
                preparacion.fase = Fase.CANCELADO;
            }
            throw new ResponseStatusException(restantes == 0 ? HttpStatus.GONE : HttpStatus.BAD_REQUEST,
                    restantes == 0 ? "Se agotaron los intentos. Prepare nuevamente la propuesta."
                            : "Token incorrecto. Intentos restantes: " + restantes + ".");
        }
    }

    private String emitirToken(Preparacion preparacion) {
        String token;
        byte[] hash;
        do {
            token = Integer.toString(1000 + random.nextInt(9000));
            hash = hashToken(preparacion.id, token);
        } while (preparacion.tokenHash != null && MessageDigest.isEqual(hash, preparacion.tokenHash));
        preparacion.tokenHash = hash;
        OffsetDateTime expiracion = ahora().plusMinutes(10);
        preparacion.tokenExpiraEn = expiracion.isBefore(preparacion.expiraEn) ? expiracion : preparacion.expiraEn;
        preparacion.intentos = 0;
        return token;
    }

    private byte[] hashToken(UUID id, String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest((id + ":" + token).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("No fue posible generar la confirmacion de la propuesta.", exception);
        }
    }

    private PreparacionResponse respuesta(Preparacion preparacion, String token) {
        return new PreparacionResponse(preparacion.id, preparacion.expiraEn, token,
                preparacion.tokenExpiraEn, MAX_INTENTOS, preparacion.calculo);
    }

    private CalculoResponse soloSeleccionadas(CalculoResponse calculo, List<Fila> filas) {
        return new CalculoResponse(calculo.calculoHash(), calculo.parametros(), calculo.calculadoEn(),
                calculo.diasCalendario(), calculo.diasExcluidos(), calculo.diasEfectivos(),
                calculo.minimoDiasEfectivos(), calculo.diasConDispensacion(), filas);
    }

    private void limpiarVencidas(OffsetDateTime ahora) {
        preparaciones.values().removeIf(preparacion -> !preparacion.expiraEn.isAfter(ahora));
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.now(applicationClock.withZone(BOGOTA));
    }

    private ResponseStatusException conflicto(String mensaje) {
        return new ResponseStatusException(HttpStatus.CONFLICT, mensaje);
    }

    private ResponseStatusException noDisponible() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "La propuesta no esta disponible o expiro. Prepare nuevamente el calculo.");
    }

    private enum Fase { CONFIGURACION, REVISION, APLICADO, CANCELADO }

    private static final class Preparacion {
        private final UUID id;
        private final Long usuarioId;
        private final String huellaCompleta;
        private final CalculoResponse calculo;
        private final OffsetDateTime expiraEn;
        private volatile Fase fase = Fase.CONFIGURACION;
        private byte[] tokenHash;
        private OffsetDateTime tokenExpiraEn;
        private int intentos;
        private ResultadoResponse resultado;

        private Preparacion(UUID id, Long usuarioId, String huella, CalculoResponse calculo, OffsetDateTime expiraEn) {
            this.id = id;
            this.usuarioId = usuarioId;
            this.huellaCompleta = huella;
            this.calculo = calculo;
            this.expiraEn = expiraEn;
        }
    }
}
