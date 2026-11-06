package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion;
import exotic.app.planta.model.produccion.despeje.*;
import exotic.app.planta.model.produccion.dto.DespejeLineaDTOs.*;
import exotic.app.planta.model.users.*;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.produccion.*;
import exotic.app.planta.repo.produccion.fabricacion.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import static exotic.app.planta.model.produccion.despeje.DespejeLineaEjecucion.Estado.*;
import static exotic.app.planta.service.produccion.PlantillaDespejeService.*;

/** Evidencia operativa autónoma: no depende del expediente ni de servicios de OP/OF. */
@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
public class DespejeLineaService {
    private final DespejeLineaEjecucionRepo ejecucionRepo;
    private final PlantillaDespejeVersionRepo plantillaRepo;
    private final PlantillaDespejeService plantillaService;
    private final SeguimientoOrdenAreaRepo seguimientoRepo;
    private final OrdenProduccionRepo ordenRepo;
    private final OrdenFabricacionOperacionRepo operacionRepo;
    private final OrdenFabricacionRepo fabricacionRepo;
    private final LoteRepo loteRepo;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final Clock applicationClock;

    @Transactional
    public Preparacion preparar(PrepararRequest request, User actor) {
        if (request == null) throw new IllegalArgumentException("Indique el seguimiento OP o la operación OF.");
        Origen origen = bloquearOrigen(request.seguimientoId(), request.operacionId());
        AreaOperativa area = plantillaService.bloquearArea(origen.area().getAreaId());
        exigirResponsable(area, actor);
        validarOrdenActiva(origen);
        if (!area.isRequiereDespeje()) return new Preparacion(false, false, true, "El área no exige despeje.", null);
        if (ocupada(area) && !mismaOcupacion(area, origen))
            throw conflicto("El área está ocupada por otra operación, incluso si está pausada.");
        if (mismaOcupacion(area, origen)) {
            DespejeLineaEjecucion actual = area.getDespejeEjecucionActual();
            if (actual == null || actual.getEstado() != UTILIZADA)
                throw conflicto("La ocupación actual no tiene evidencia válida. Requiere conciliación operativa.");
            return preparacion(actual, true, "Reanudación de la misma ocupación; se conserva el despeje utilizado.");
        }
        if (origen.estado() != SeguimientoOrdenArea.ESTADO_ESPERA)
            throw conflicto("El despeje se prepara antes del arranque de una operación en espera.");
        PlantillaDespejeVersion vigente = plantillaRepo.findByAreaOperativa_AreaIdAndEstado(
                area.getAreaId(), PlantillaDespejeVersion.Estado.VIGENTE)
                .orElseThrow(() -> conflicto("El área exige despeje, pero no tiene una plantilla vigente."));
        entityManager.refresh(vigente);
        List<DespejeLineaEjecucion> intentos = intentos(origen);
        DespejeLineaEjecucion ultima = intentos.isEmpty() ? null : intentos.getFirst();
        if (ultima != null) {
            entityManager.refresh(ultima, LockModeType.PESSIMISTIC_WRITE);
            boolean vigenteContexto = ultima.getRevisionContexto() == area.getDespejeRevisionContexto()
                    && ultima.getPlantilla().getId().equals(vigente.getId());
            if (vigenteContexto && ultima.getEstado() == BORRADOR)
                return preparacion(ultima, false, "Complete y envíe el borrador.");
            if (vigenteContexto && !Boolean.TRUE.equals(request.nuevoIntento())
                    && ultima.getEstado() != INVALIDADA)
                return preparacion(ultima, ultima.getEstado() == APROBADA,
                        ultima.getEstado() == APROBADA ? "Despeje aprobado para iniciar." : "Consulte el resultado o prepare otro intento.");
            invalidarPendiente(ultima, vigenteContexto ? "Sustituida por un nuevo intento."
                    : "Cambió la plantilla o el contexto físico antes del arranque.");
        }
        Lote lote = lote(origen);
        DespejeLineaEjecucion nueva = new DespejeLineaEjecucion();
        nueva.setAreaOperativa(area);
        nueva.setAreaNombre(area.getNombre());
        nueva.setSeguimiento(origen.seguimiento());
        nueva.setOperacion(origen.operacion());
        nueva.setPlantilla(vigente);
        nueva.setIntento(intentos.isEmpty() ? 1 : intentos.getFirst().getIntento() + 1);
        nueva.setRevisionContexto(area.getDespejeRevisionContexto());
        nueva.setRequiereRevision(area.isRequiereRevisionDespeje());
        nueva.setLoteActual(lote);
        nueva.setLoteActualNumero(lote.getBatchNumber());
        Lote antecedente = area.isDespejeAntecedenteConocido() ? area.getDespejeUltimoLote() : null;
        nueva.setLoteAnterior(antecedente);
        nueva.setLoteAnteriorNumero(antecedente == null ? null : antecedente.getBatchNumber());
        nueva.setAntecedenteConocido(area.isDespejeAntecedenteConocido());
        nueva.setCreadaEn(ahora());
        nueva.setCreadaPor(actor);
        nueva.setCreadaPorUsername(actor.getUsername());
        nueva.setCreadaPorNombre(nombre(actor));
        Map<String, Object> contexto = new TreeMap<>();
        contexto.put("plantilla", plantillaService.toDTO(vigente));
        Producto actual = producto(lote);
        Producto anterior = producto(antecedente);
        contexto.put("productoActualId", actual == null ? null : actual.getProductoId());
        contexto.put("productoActualNombre", actual == null ? null : actual.getNombre());
        contexto.put("productoAnteriorId", anterior == null ? null : anterior.getProductoId());
        contexto.put("productoAnteriorNombre", anterior == null ? null : anterior.getNombre());
        nueva.setContextoJson(json(contexto));
        for (ItemPlantillaDespeje item : vigente.getItems()) {
            RespuestaDespeje respuesta = new RespuestaDespeje();
            respuesta.setEjecucion(nueva);
            respuesta.setItem(item);
            respuesta.setPlantilla(vigente);
            nueva.getRespuestas().add(respuesta);
        }
        return preparacion(ejecucionRepo.saveAndFlush(nueva), false, "Complete y envíe el despeje antes de iniciar.");
    }

    @Transactional
    public Ejecucion guardar(Long id, GuardarRequest request, User actor) {
        Bloqueo bloqueo = bloquearEjecucion(id);
        DespejeLineaEjecucion e = bloqueo.ejecucion();
        exigirResponsable(bloqueo.area(), actor);
        validarOrdenActiva(bloqueo.origen());
        if (e.getEstado() != BORRADOR) throw conflicto("El contenido enviado es inmutable; prepare otro intento.");
        validarContexto(e, bloqueo.area());
        if (request == null || request.respuestas() == null || request.respuestas().size() > 100)
            throw new IllegalArgumentException("Indique las respuestas del formulario.");
        Map<Long, Respuesta> nuevas = new HashMap<>();
        Set<Long> items = new HashSet<>();
        e.getRespuestas().forEach(r -> items.add(r.getItem().getId()));
        for (Respuesta r : request.respuestas()) {
            if (r == null || r.itemId() == null || !items.contains(r.itemId()) || nuevas.put(r.itemId(), r) != null)
                throw new IllegalArgumentException("Hay respuestas repetidas o ajenas a la plantilla fijada.");
        }
        boolean iguales = Objects.equals(e.getObservaciones(), texto(request.observaciones()))
                && Objects.equals(e.getObservacionAntecedente(), texto(request.observacionAntecedente()))
                && e.isConfirmaAntecedenteDesconocido() == Boolean.TRUE.equals(request.confirmaAntecedenteDesconocido());
        for (RespuestaDespeje respuesta : e.getRespuestas()) {
            Respuesta r = nuevas.get(respuesta.getItem().getId());
            validarRespuesta(respuesta.getItem(), r, false);
            iguales &= respuesta.getValor() == (r == null ? null : r.valor())
                    && mismoNumero(respuesta.getNumero(), r == null ? null : r.numero())
                    && Objects.equals(respuesta.getObservacion(), texto(r == null ? null : r.observacion()));
        }
        // PUT repetido con el mismo contenido no crea intentos ni exige reescribir una versión nueva.
        if (!iguales) exigirVersion(request.version(), e.getVersion());
        else return toDTO(e);
        e.setConfirmaAntecedenteDesconocido(Boolean.TRUE.equals(request.confirmaAntecedenteDesconocido()));
        e.setObservacionAntecedente(texto(request.observacionAntecedente()));
        e.setObservaciones(texto(request.observaciones()));
        for (RespuestaDespeje respuesta : e.getRespuestas()) {
            Respuesta r = nuevas.get(respuesta.getItem().getId());
            respuesta.setValor(r == null ? null : r.valor());
            respuesta.setNumero(r == null ? null : r.numero());
            respuesta.setObservacion(texto(r == null ? null : r.observacion()));
        }
        // Las respuestas son hijas inversas; se incrementa explícitamente la versión del agregado.
        entityManager.lock(e, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        ejecucionRepo.saveAndFlush(e);
        return toDTO(e);
    }

    @Transactional
    public Ejecucion enviar(Long id, EnviarRequest request, User actor) {
        Bloqueo bloqueo = bloquearEjecucion(id);
        DespejeLineaEjecucion e = bloqueo.ejecucion();
        exigirResponsable(bloqueo.area(), actor);
        validarOrdenActiva(bloqueo.origen());
        if (e.getEstado() != BORRADOR) {
            if (e.getEnviadaEn() != null && e.getEnviadaPor().getId().equals(actor.getId())) return toDTO(e);
            throw conflicto("El intento no admite envío.");
        }
        exigirVersion(request == null ? null : request.version(), e.getVersion());
        validarContexto(e, bloqueo.area());
        if (!e.isAntecedenteConocido() && (!e.isConfirmaAntecedenteDesconocido()
                || texto(e.getObservacionAntecedente()) == null))
            throw new IllegalArgumentException("Confirme el antecedente desconocido y describa la comprobación inicial realizada.");
        for (RespuestaDespeje r : e.getRespuestas())
            validarRespuesta(r.getItem(), new Respuesta(r.getItem().getId(), r.getValor(), r.getNumero(), r.getObservacion()), true);
        e.setEnviadaEn(ahora());
        e.setEnviadaPor(actor);
        e.setEnviadaPorUsername(actor.getUsername());
        e.setEnviadaPorNombre(nombre(actor));
        e.setContenidoSha256(sha256(contenidoEnviado(e)));
        e.setEstado(!conforme(e) ? RECHAZADA : e.isRequiereRevision() ? PENDIENTE_REVISION : APROBADA);
        return toDTO(ejecucionRepo.saveAndFlush(e));
    }

    @Transactional
    public Ejecucion revisar(Long id, RevisarRequest request, User actor) {
        if (request == null || request.aprobar() == null || texto(request.motivo()) == null)
            throw new IllegalArgumentException("Indique el dictamen y su motivo.");
        Bloqueo bloqueo = bloquearEjecucion(id);
        DespejeLineaEjecucion e = bloqueo.ejecucion();
        validarOrdenActiva(bloqueo.origen());
        if (actor == null || actor.getId() == null) throw new AccessDeniedException("Se requiere identidad autenticada.");
        if (e.getEnviadaPor() != null && e.getEnviadaPor().getId().equals(actor.getId()))
            throw new AccessDeniedException("La revisión requiere una persona distinta de quien envió el despeje.");
        if (e.getEstado() != PENDIENTE_REVISION) {
            if (e.getRevisadaPor() != null && e.getRevisadaPor().getId().equals(actor.getId())
                    && e.getEstado() == (request.aprobar() ? APROBADA : RECHAZADA)
                    && Objects.equals(e.getMotivoRevision(), texto(request.motivo()))) return toDTO(e);
            throw conflicto("El intento no está pendiente de revisión.");
        }
        exigirVersion(request.version(), e.getVersion());
        validarContexto(e, bloqueo.area());
        if (request.aprobar() && !conforme(e)) throw conflicto("No se puede aprobar un formulario no conforme.");
        e.setRevisadaEn(ahora());
        e.setRevisadaPor(actor);
        e.setRevisadaPorUsername(actor.getUsername());
        e.setRevisadaPorNombre(nombre(actor));
        e.setMotivoRevision(texto(request.motivo()));
        e.setEstado(request.aprobar() ? APROBADA : RECHAZADA);
        return toDTO(ejecucionRepo.saveAndFlush(e));
    }

    public Ejecucion detalle(Long id, User actor) {
        DespejeLineaEjecucion e = require(id);
        exigirConsulta(e.getAreaOperativa(), actor);
        return toDTO(e);
    }

    /** API de integración: el caller autoriza la transición y conserva su transacción. */
    @Transactional
    public void validarYRegistrarInicio(SeguimientoOrdenArea seguimiento, User actor) {
        registrarInicio(bloquearOrigen(seguimiento.getId(), null), actor);
    }
    @Transactional
    public void validarYRegistrarInicio(OrdenFabricacionOperacion operacion, User actor) {
        registrarInicio(bloquearOrigen(null, operacion.getId()), actor);
    }
    @Transactional
    public void validarPausa(SeguimientoOrdenArea seguimiento, User actor) {
        validarPausa(bloquearOrigen(seguimiento.getId(), null), actor);
    }
    @Transactional
    public void validarPausa(OrdenFabricacionOperacion operacion, User actor) {
        validarPausa(bloquearOrigen(null, operacion.getId()), actor);
    }
    private void validarPausa(Origen origen, User actor) {
        AreaOperativa area = plantillaService.bloquearArea(origen.area().getAreaId());
        validarOrdenActiva(origen);
        if (origen.estado() != SeguimientoOrdenArea.ESTADO_EN_PROCESO)
            throw conflicto("Solo una operación actualmente en proceso puede pausarse.");
        if (area.isRequiereDespeje()) {
            if (actor == null || actor.getId() == null)
                throw new AccessDeniedException("La pausa requiere un actor autenticado.");
            if (ocupada(area) && !mismaOcupacion(area, origen))
                throw conflicto("Otra operación ocupa el área; no puede pausarse esta ocupación.");
        }
        // No se modifica el ocupante, el antecedente, la revisión ni la evidencia.
    }
    private void registrarInicio(Origen origen, User actor) {
        AreaOperativa area = plantillaService.bloquearArea(origen.area().getAreaId());
        validarOrdenActiva(origen);
        if (!area.isRequiereDespeje()) return;
        if (actor == null || actor.getId() == null) throw new AccessDeniedException("El inicio requiere un actor autenticado.");
        if (!area.isDespejeLineaExclusivaConfirmada()) throw conflicto("No se ha confirmado una línea física exclusiva.");
        if (ocupada(area)) {
            if (!mismaOcupacion(area, origen)) throw conflicto("Otra operación ocupa el área; una pausa conserva su ocupación.");
            DespejeLineaEjecucion actual = area.getDespejeEjecucionActual();
            if (actual == null || actual.getEstado() != UTILIZADA
                    || actual.getRevisionContexto() != area.getDespejeRevisionContexto())
                throw conflicto("La ocupación no tiene un despeje utilizado válido.");
            return;
        }
        DespejeLineaEjecucion aprobada = intentos(origen).stream()
                .filter(e -> e.getEstado() == APROBADA && e.getRevisionContexto() == area.getDespejeRevisionContexto())
                .findFirst().orElseThrow(() -> conflicto("Debe completar y aprobar el despeje de esta operación antes de iniciar."));
        entityManager.refresh(aprobada, LockModeType.PESSIMISTIC_WRITE);
        if (aprobada.getEstado() != APROBADA) throw conflicto("El estado del despeje cambió antes del arranque.");
        validarContexto(aprobada, area);
        aprobada.setEstado(UTILIZADA);
        aprobada.setUtilizadaEn(ahora());
        area.setDespejeOcupanteSeguimiento(origen.seguimiento());
        area.setDespejeOcupanteOperacion(origen.operacion());
        area.setDespejeEjecucionActual(aprobada);
        area.setDespejeUltimoLote(aprobada.getLoteActual());
        area.setDespejeAntecedenteConocido(true);
        area.setDespejeUltimoInicioEn(aprobada.getUtilizadaEn());
        ejecucionRepo.saveAndFlush(aprobada);
    }

    @Transactional
    public void registrarSalida(SeguimientoOrdenArea seguimiento, User actor) {
        salida(bloquearOrigen(seguimiento.getId(), null), false);
    }
    @Transactional
    public void registrarSalida(OrdenFabricacionOperacion operacion, User actor) {
        salida(bloquearOrigen(null, operacion.getId()), false);
    }
    @Transactional
    public void reconciliarCorreccion(SeguimientoOrdenArea seguimiento, User actor) {
        salida(bloquearOrigen(seguimiento.getId(), null), true);
    }
    @Transactional
    public void reconciliarCorreccion(OrdenFabricacionOperacion operacion, User actor) {
        salida(bloquearOrigen(null, operacion.getId()), true);
    }
    private void salida(Origen origen, boolean correccion) {
        AreaOperativa area = plantillaService.bloquearArea(origen.area().getAreaId());
        if (correccion) intentos(origen).forEach(e -> invalidarPendiente(e,
                "La operación fue corregida o anulada; se requiere comprobar el nuevo contexto."));
        if (mismaOcupacion(area, origen)) {
            area.setDespejeOcupanteSeguimiento(null);
            area.setDespejeOcupanteOperacion(null);
            area.setDespejeEjecucionActual(null);
            area.setDespejeRevisionContexto(area.getDespejeRevisionContexto() + 1);
        }
        // Nunca se borra el último lote iniciado realmente, tampoco al cancelar una orden.
    }

    public List<Ejecucion> evidenciaOrdenProduccion(int ordenId) {
        return evidencia(ejecucionRepo.findBySeguimiento_OrdenProduccion_OrdenIdOrderByCreadaEnAscIdAsc(ordenId));
    }
    public List<Ejecucion> evidenciaOrdenFabricacion(Long ordenId) {
        return evidencia(ejecucionRepo.findByOperacion_OrdenFabricacion_OrdenFabricacionIdOrderByCreadaEnAscIdAsc(ordenId));
    }
    public List<Ejecucion> consultarOrdenProduccion(int ordenId, User actor) {
        List<DespejeLineaEjecucion> lista = ejecucionRepo.findBySeguimiento_OrdenProduccion_OrdenIdOrderByCreadaEnAscIdAsc(ordenId);
        exigirConsultaOrden(lista, actor);
        return evidencia(lista);
    }
    public List<Ejecucion> consultarOrdenFabricacion(Long ordenId, User actor) {
        List<DespejeLineaEjecucion> lista = ejecucionRepo.findByOperacion_OrdenFabricacion_OrdenFabricacionIdOrderByCreadaEnAscIdAsc(ordenId);
        exigirConsultaOrden(lista, actor);
        return evidencia(lista);
    }
    private List<Ejecucion> evidencia(List<DespejeLineaEjecucion> lista) {
        return lista.stream().filter(e -> e.getEnviadaEn() != null).map(this::toDTO).toList();
    }
    private void exigirConsultaOrden(List<DespejeLineaEjecucion> lista, User actor) {
        if (puedeConsultarExpedientes(actor)) return;
        if (lista.isEmpty() || lista.stream().anyMatch(e -> !responsable(e.getAreaOperativa(), actor)))
            throw new AccessDeniedException("No tiene acceso al expediente completo de esta orden.");
    }
    private void validarContexto(DespejeLineaEjecucion e, AreaOperativa area) {
        if (!area.isRequiereDespeje() || e.getRevisionContexto() != area.getDespejeRevisionContexto())
            throw conflicto("Cambió la política o el contexto del área. Prepare otro intento.");
        if (ocupada(area)) throw conflicto("El área ya está ocupada; el formulario no puede utilizarse para otro arranque.");
        entityManager.refresh(e.getPlantilla());
        if (e.getPlantilla().getEstado() != PlantillaDespejeVersion.Estado.VIGENTE)
            throw conflicto("La plantilla fue sustituida o retirada antes del arranque. Prepare otro intento.");
        Origen origen = new Origen(area, e.getSeguimiento(), e.getOperacion());
        if (!lote(origen).getId().equals(e.getLoteActual().getId()))
            throw conflicto("Cambió el lote resultado de la operación. Prepare otro intento.");
    }
    private void validarOrdenActiva(Origen origen) {
        if (origen.seguimiento() != null) {
            int estado = origen.seguimiento().getOrdenProduccion().getEstadoOrden();
            if (estado == -1 || estado == 2 || estado == 3)
                throw conflicto("La orden de producción ya no admite nuevos despejes.");
        } else {
            var estado = origen.operacion().getOrdenFabricacion().getEstado();
            if (estado != exotic.app.planta.model.produccion.fabricacion.EstadoOrdenFabricacion.LIBERADA
                    && estado != exotic.app.planta.model.produccion.fabricacion.EstadoOrdenFabricacion.EN_EJECUCION)
                throw conflicto("La orden de fabricación debe estar liberada o en ejecución.");
        }
    }
    private void invalidarPendiente(DespejeLineaEjecucion e, String motivo) {
        if (e.getEstado() == BORRADOR || e.getEstado() == PENDIENTE_REVISION || e.getEstado() == APROBADA) {
            e.setEstado(INVALIDADA);
            e.setMotivoInvalidacion(motivo);
        }
    }
    private boolean conforme(DespejeLineaEjecucion e) {
        for (RespuestaDespeje r : e.getRespuestas()) {
            ItemPlantillaDespeje i = r.getItem();
            if (i.getTipo() == ItemPlantillaDespeje.Tipo.SI_NO_NA) {
                if (r.getValor() != null && r.getValor() != ItemPlantillaDespeje.Valor.NA
                        && r.getValor() != i.getRespuestaConforme()) return false;
            } else if (r.getNumero() != null && ((i.getMinimo() != null && r.getNumero().compareTo(i.getMinimo()) < 0)
                    || (i.getMaximo() != null && r.getNumero().compareTo(i.getMaximo()) > 0))) return false;
        }
        return true;
    }
    private void validarRespuesta(ItemPlantillaDespeje i, Respuesta r, boolean completa) {
        ItemPlantillaDespeje.Valor valor = r == null ? null : r.valor();
        BigDecimal numero = r == null ? null : r.numero();
        texto(r == null ? null : r.observacion());
        if (i.getTipo() == ItemPlantillaDespeje.Tipo.SI_NO_NA) {
            if (numero != null || (valor == ItemPlantillaDespeje.Valor.NA && !i.isPermiteNa()))
                throw new IllegalArgumentException("La respuesta no corresponde a la verificación o NA no está permitido.");
            if (completa && i.isObligatorio() && valor == null)
                throw new IllegalArgumentException("Responda todos los ítems obligatorios.");
            if (completa && valor == ItemPlantillaDespeje.Valor.NA && texto(r.observacion()) == null)
                throw new IllegalArgumentException("Justifique cada respuesta NA.");
            if (completa && valor != null && valor != ItemPlantillaDespeje.Valor.NA
                    && valor != i.getRespuestaConforme() && texto(r.observacion()) == null)
                throw new IllegalArgumentException("Justifique cada respuesta no conforme.");
        } else {
            validarNumero(numero);
            if (valor != null) throw new IllegalArgumentException("La pregunta requiere un número.");
            if (completa && i.isObligatorio() && numero == null)
                throw new IllegalArgumentException("Responda todos los ítems numéricos obligatorios.");
            if (completa && numero != null && ((i.getMinimo() != null && numero.compareTo(i.getMinimo()) < 0)
                    || (i.getMaximo() != null && numero.compareTo(i.getMaximo()) > 0))
                    && texto(r.observacion()) == null)
                throw new IllegalArgumentException("Justifique el resultado numérico fuera de los límites.");
        }
    }

    private Origen bloquearOrigen(Long seguimientoId, Long operacionId) {
        if ((seguimientoId == null) == (operacionId == null))
            throw new IllegalArgumentException("Indique exactamente un seguimiento OP o una operación OF.");
        if (seguimientoId != null) {
            SeguimientoOrdenArea s = seguimientoRepo.findById(seguimientoId)
                    .orElseThrow(() -> new NoSuchElementException("Seguimiento OP no encontrado."));
            var orden = ordenRepo.findByIdForUpdate(s.getOrdenProduccion().getOrdenId())
                    .orElseThrow(() -> new NoSuchElementException("Orden de producción no encontrada."));
            entityManager.refresh(orden, LockModeType.PESSIMISTIC_WRITE);
            entityManager.refresh(s, LockModeType.PESSIMISTIC_WRITE);
            return new Origen(s.getAreaOperativa(), s, null);
        }
        OrdenFabricacionOperacion o = operacionRepo.findById(operacionId)
                .orElseThrow(() -> new NoSuchElementException("Operación OF no encontrada."));
        var orden = fabricacionRepo.findByIdForUpdate(o.getOrdenFabricacion().getOrdenFabricacionId())
                .orElseThrow(() -> new NoSuchElementException("Orden de fabricación no encontrada."));
        entityManager.refresh(orden, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(o, LockModeType.PESSIMISTIC_WRITE);
        return new Origen(o.getAreaOperativa(), null, o);
    }
    private Bloqueo bloquearEjecucion(Long id) {
        DespejeLineaEjecucion e = require(id);
        Origen origen = bloquearOrigen(e.getSeguimiento() == null ? null : e.getSeguimiento().getId(),
                e.getOperacion() == null ? null : e.getOperacion().getId());
        AreaOperativa area = plantillaService.bloquearArea(origen.area().getAreaId());
        entityManager.refresh(e, LockModeType.PESSIMISTIC_WRITE);
        return new Bloqueo(origen, area, e);
    }
    private Lote lote(Origen origen) {
        List<Lote> lotes = origen.seguimiento() != null
                ? loteRepo.findByOrdenProduccion_OrdenId(origen.seguimiento().getOrdenProduccion().getOrdenId())
                : loteRepo.findByOrdenFabricacion_OrdenFabricacionId(origen.operacion().getOrdenFabricacion().getOrdenFabricacionId());
        if (lotes.size() != 1) throw conflicto("La operación no tiene un único lote resultado identificable.");
        return lotes.getFirst();
    }
    private Producto producto(Lote lote) {
        if (lote == null) return null;
        if (lote.getProducto() != null) return lote.getProducto();
        if (lote.getOrdenProduccion() != null) return lote.getOrdenProduccion().getProducto();
        return lote.getOrdenFabricacion() == null ? null : lote.getOrdenFabricacion().getSemiTerminado();
    }
    private List<DespejeLineaEjecucion> intentos(Origen origen) {
        return ejecucionRepo.findIntentos(origen.seguimiento() == null ? null : origen.seguimiento().getId(),
                origen.operacion() == null ? null : origen.operacion().getId());
    }
    private DespejeLineaEjecucion require(Long id) {
        return ejecucionRepo.findById(id).orElseThrow(() -> new NoSuchElementException("Intento de despeje no encontrado."));
    }
    private boolean ocupada(AreaOperativa area) {
        return area.getDespejeOcupanteSeguimiento() != null || area.getDespejeOcupanteOperacion() != null;
    }
    private boolean mismaOcupacion(AreaOperativa a, Origen o) {
        return o.seguimiento() != null && a.getDespejeOcupanteSeguimiento() != null
                && o.seguimiento().getId().equals(a.getDespejeOcupanteSeguimiento().getId())
                || o.operacion() != null && a.getDespejeOcupanteOperacion() != null
                && o.operacion().getId().equals(a.getDespejeOcupanteOperacion().getId());
    }
    private Preparacion preparacion(DespejeLineaEjecucion e, boolean puedeIniciar, String motivo) {
        return new Preparacion(true, e.isRequiereRevision(), puedeIniciar, motivo, toDTO(e));
    }
    private boolean responsable(AreaOperativa a, User actor) {
        return actor != null && a.getResponsableArea() != null && actor.getId().equals(a.getResponsableArea().getId());
    }
    private void exigirResponsable(AreaOperativa area, User actor) {
        if (!responsable(area, actor)) throw new AccessDeniedException("Solo el responsable autenticado del área puede diligenciar el despeje.");
    }
    private boolean puedeConsultarExpedientes(User actor) {
        if (actor == null) return false;
        if ("master".equalsIgnoreCase(actor.getUsername()) || "super_master".equalsIgnoreCase(actor.getUsername())) return true;
        return UserAccessEvaluator.tabNivel(actor, ModuloSistema.PRODUCCION, "CONSULTAR_BATCH_RECORD").orElse(0) >= 1
                || UserAccessEvaluator.tabNivel(actor, ModuloSistema.CALIDAD, "REVISION_LIBERACION_LOTES").orElse(0) >= 1;
    }
    private void exigirConsulta(AreaOperativa area, User actor) {
        if (!responsable(area, actor) && !puedeConsultarExpedientes(actor))
            throw new AccessDeniedException("No tiene acceso a esta evidencia de despeje.");
    }
    private Ejecucion toDTO(DespejeLineaEjecucion e) {
        JsonNode contexto = leerContexto(e);
        Plantilla plantilla = objectMapper.convertValue(contexto.get("plantilla"), Plantilla.class);
        return new Ejecucion(e.getId(), e.getAreaOperativa().getAreaId(), e.getAreaNombre(),
                e.getSeguimiento() == null ? null : e.getSeguimiento().getId(),
                e.getOperacion() == null ? null : e.getOperacion().getId(),
                e.getSeguimiento() == null ? null : e.getSeguimiento().getOrdenProduccion().getOrdenId(),
                e.getOperacion() == null ? null : e.getOperacion().getOrdenFabricacion().getOrdenFabricacionId(),
                e.getIntento(), e.getRevisionContexto(), e.getEstado().name(), plantilla, e.isRequiereRevision(),
                e.getLoteActual().getId(), e.getLoteActualNumero(),
                campo(contexto, "productoActualId"), campo(contexto, "productoActualNombre"),
                e.getLoteAnterior() == null ? null : e.getLoteAnterior().getId(), e.getLoteAnteriorNumero(),
                campo(contexto, "productoAnteriorId"), campo(contexto, "productoAnteriorNombre"), e.isAntecedenteConocido(),
                e.isConfirmaAntecedenteDesconocido(), e.getObservacionAntecedente(), e.getObservaciones(),
                e.getMotivoRevision(), e.getMotivoInvalidacion(), e.getCreadaEn(), e.getEnviadaEn(), e.getRevisadaEn(),
                e.getUtilizadaEn(), actor(e.getCreadaPor(), e.getCreadaPorUsername(), e.getCreadaPorNombre()),
                actor(e.getEnviadaPor(), e.getEnviadaPorUsername(), e.getEnviadaPorNombre()),
                actor(e.getRevisadaPor(), e.getRevisadaPorUsername(), e.getRevisadaPorNombre()), e.getContenidoSha256(),
                e.getVersion(), e.getRespuestas().stream().sorted(Comparator.comparingInt(r -> r.getItem().getOrden()))
                .map(r -> new Respuesta(r.getItem().getId(), r.getValor(), r.getNumero(), r.getObservacion())).toList());
    }
    private JsonNode leerContexto(DespejeLineaEjecucion e) {
        try { return objectMapper.readTree(e.getContextoJson()); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("El contexto congelado del despeje no es legible.", ex); }
    }
    private String campo(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
    private Actor actor(User u, String username, String nombre) { return u == null ? null : new Actor(u.getId(), username, nombre); }
    private String contenidoEnviado(DespejeLineaEjecucion e) {
        Map<String, Object> data = new TreeMap<>();
        data.put("contexto", leerContexto(e));
        data.put("revisionContexto", e.getRevisionContexto());
        data.put("intento", e.getIntento());
        data.put("loteActualId", e.getLoteActual().getId());
        data.put("loteAnteriorId", e.getLoteAnterior() == null ? null : e.getLoteAnterior().getId());
        data.put("confirmaAntecedenteDesconocido", e.isConfirmaAntecedenteDesconocido());
        data.put("observacionAntecedente", e.getObservacionAntecedente());
        data.put("observaciones", e.getObservaciones());
        data.put("enviadaPor", actor(e.getEnviadaPor(), e.getEnviadaPorUsername(), e.getEnviadaPorNombre()));
        data.put("enviadaEn", e.getEnviadaEn());
        data.put("respuestas", toDTO(e).respuestas());
        return json(data);
    }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("No fue posible congelar el contexto del despeje.", ex); }
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private String nombre(User actor) {
        return actor.getNombreCompleto() == null || actor.getNombreCompleto().isBlank() ? actor.getUsername() : actor.getNombreCompleto();
    }
    private String texto(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > 4000) throw new IllegalArgumentException("La observación admite hasta 4000 caracteres.");
        return value.trim();
    }
    private boolean mismoNumero(BigDecimal a, BigDecimal b) { return a == null ? b == null : b != null && a.compareTo(b) == 0; }
    private LocalDateTime ahora() { return LocalDateTime.now(applicationClock); }
    private record Bloqueo(Origen origen, AreaOperativa area, DespejeLineaEjecucion ejecucion) {}
    private record Origen(AreaOperativa area, SeguimientoOrdenArea seguimiento, OrdenFabricacionOperacion operacion) {
        int estado() { return seguimiento != null ? seguimiento.getEstado() : operacion.getEstado(); }
    }
}
