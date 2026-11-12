package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.SeguimientoOrdenAreaEvento;
import exotic.app.planta.model.produccion.TipoEventoSeguimiento;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacionEvento;
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
        boolean reintentar = Boolean.TRUE.equals(request.nuevoIntento());
        if (reintentar && request.familiaId() == null)
            throw new IllegalArgumentException("Indique la familia cuyo formulario desea repetir.");
        if (!reintentar && request.familiaId() != null)
            throw new IllegalArgumentException("La familia solo se indica al solicitar un nuevo intento.");
        Origen origen = bloquearOrigen(request.seguimientoId(), request.operacionId());
        AreaOperativa area = plantillaService.bloquearArea(origen.area().getAreaId());
        exigirResponsable(area, actor);
        validarOrdenActiva(origen);
        validarDisponibilidad(area, origen);
        if (!area.isRequiereDespeje())
            return new Preparacion(false, false, true, "El área no exige despeje.", List.of(), List.of());
        if (mismaOcupacion(area, origen) && tieneConjuntoUtilizado(origen, area)) {
            if (reintentar) throw conflicto("Una ocupación iniciada conserva su conjunto utilizado; no admite nuevos intentos.");
            List<Requisito> utilizadas = conjuntoUtilizado(origen, area).stream()
                    .map(e -> requisito(e, true, "Formulario utilizado en esta ocupación.")).toList();
            return new Preparacion(true, area.isRequiereRevisionDespeje(), true,
                    "Reanudación de la misma ocupación; se conserva el conjunto de despejes utilizado.", utilizadas, List.of());
        }
        if (origen.estado() != SeguimientoOrdenArea.ESTADO_ESPERA)
            throw conflicto("El despeje se prepara antes de iniciar o reanudar una operación en espera.");
        boolean reanudacion = esReanudacion(origen);
        String accion = reanudacion ? "reanudar" : "iniciar";
        exigirContextoSinUsoPrevio(origen, area);
        List<PlantillaDespejeVersion> vigentes = vigentes(area);
        if (reintentar && vigentes.stream().noneMatch(p -> p.getFamilia().getId().equals(request.familiaId())))
            throw conflicto("La familia indicada no tiene una versión vigente en esta área. Actualice la preparación.");
        Map<Long, DespejeLineaEjecucion> ultimas = ultimasPorFamilia(intentos(origen));
        Set<Long> familiasVigentes = new HashSet<>();
        vigentes.forEach(p -> familiasVigentes.add(p.getFamilia().getId()));
        List<String> avisos = new ArrayList<>();
        for (DespejeLineaEjecucion anterior : ultimas.values()) {
            if (!familiasVigentes.contains(anterior.getFamilia().getId())
                    && anterior.getRevisionContexto() == area.getDespejeRevisionContexto()) {
                invalidarPendiente(anterior, "La familia ya no tiene una versión vigente antes del arranque.");
                avisos.add("La familia «" + anterior.getFamilia().getNombre()
                        + "» ya no tiene una versión vigente y no se exige en este inicio; su historial se conserva.");
            }
        }
        Lote lote = lote(origen);
        List<Requisito> requisitos = new ArrayList<>();
        for (PlantillaDespejeVersion vigente : vigentes) {
            Long familiaId = vigente.getFamilia().getId();
            DespejeLineaEjecucion ultima = ultimas.get(familiaId);
            if (ultima != null) entityManager.refresh(ultima, LockModeType.PESSIMISTIC_WRITE);
            boolean aplicable = ultima != null && aplicable(ultima, vigente, origen, area, lote);
            boolean repetirFamilia = reintentar && familiaId.equals(request.familiaId());
            if (aplicable && ultima.getEstado() == UTILIZADA)
                throw conflicto("Existe evidencia utilizada sin una ocupación coherente. Requiere conciliación operativa.");
            if (aplicable && ultima.getEstado() == BORRADOR) {
                requisitos.add(requisito(ultima, false, "Complete y envíe el borrador."));
                continue;
            }
            if (aplicable && !repetirFamilia && ultima.getEstado() != INVALIDADA) {
                requisitos.add(requisito(ultima, ultima.getEstado() == APROBADA, motivoEstado(ultima)));
                continue;
            }
            if (ultima != null) {
                String motivo = aplicable ? "Sustituida por un nuevo intento de esta familia."
                        : "Cambió la versión, el lote, la política o el contexto antes del arranque.";
                invalidarPendiente(ultima, motivo);
                avisos.add("Se preparó un nuevo intento de «" + vigente.getFamilia().getNombre()
                        + "»: " + motivo + " Las otras familias aplicables se conservan.");
            } else if (!ultimas.isEmpty()) {
                avisos.add("Se incorporó el formulario vigente de «" + vigente.getFamilia().getNombre()
                        + "» al conjunto requerido. Las aprobaciones aplicables de otras familias se conservan.");
            }
            // Los índices parciales deben ver la invalidación antes de insertar el nuevo borrador.
            ejecucionRepo.flush();
            DespejeLineaEjecucion nueva = crearBorrador(origen, area, vigente, lote, actor,
                    ultima == null ? 1 : ultima.getIntento() + 1);
            requisitos.add(requisito(nueva, false, "Complete y envíe este formulario antes de " + accion + "."));
        }
        boolean listo = !requisitos.isEmpty() && requisitos.stream().allMatch(Requisito::listo);
        String motivo = requisitos.isEmpty()
                ? "El área exige despeje, pero no tiene ninguna plantilla vigente. No puede " + accion + "."
                : listo ? "Todos los formularios actualmente vigentes están aprobados para " + accion + "."
                : "Complete y apruebe todos los formularios actualmente vigentes antes de " + accion + ".";
        return new Preparacion(true, area.isRequiereRevisionDespeje(), listo, motivo, requisitos, avisos);
    }

    private DespejeLineaEjecucion crearBorrador(Origen origen, AreaOperativa area,
                                               PlantillaDespejeVersion vigente, Lote lote, User actor, int intento) {
        DespejeLineaEjecucion nueva = new DespejeLineaEjecucion();
        nueva.setAreaOperativa(area);
        nueva.setAreaNombre(area.getNombre());
        nueva.setSeguimiento(origen.seguimiento());
        nueva.setOperacion(origen.operacion());
        nueva.setPlantilla(vigente);
        nueva.setFamilia(vigente.getFamilia());
        nueva.setIntento(intento);
        nueva.setRevisionContexto(area.getDespejeRevisionContexto());
        nueva.setRequiereRevision(area.isRequiereRevisionDespeje());
        nueva.setLoteActual(lote);
        nueva.setLoteActualNumero(lote.getBatchNumber());
        // Después de un arranque sin despeje, el último lote del área ya es el actual.
        // No puede atribuirse como antecedente de un formulario preparado para reanudar.
        boolean antecedenteConocido = !mismaOcupacion(area, origen) && !esReanudacion(origen)
                && area.isDespejeAntecedenteConocido();
        Lote antecedente = antecedenteConocido ? area.getDespejeUltimoLote() : null;
        nueva.setLoteAnterior(antecedente);
        nueva.setLoteAnteriorNumero(antecedente == null ? null : antecedente.getBatchNumber());
        nueva.setAntecedenteConocido(antecedenteConocido);
        nueva.setCreadaEn(ahora());
        nueva.setCreadaPor(actor);
        nueva.setCreadaPorUsername(actor.getUsername());
        nueva.setCreadaPorNombre(nombre(actor));
        Map<String, Object> contexto = new TreeMap<>();
        contexto.put("plantilla", plantillaService.toDTO(vigente));
        contexto.put("familiaId", vigente.getFamilia().getId());
        contexto.put("familiaNombre", vigente.getFamilia().getNombre());
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
        return ejecucionRepo.saveAndFlush(nueva);
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
        if (area.isRequiereDespeje() || area.isDespejeLineaExclusivaConfirmada() || ocupada(area)) {
            if (actor == null || actor.getId() == null)
                throw new AccessDeniedException("La pausa requiere un actor autenticado.");
            // Las operaciones heredadas pueden pausarse sin inventar ocupación ni evidencia.
            if (mismaOcupacion(area, origen) && tieneConjuntoUtilizado(origen, area))
                conjuntoUtilizado(origen, area);
        }
        // No se modifica el ocupante, el antecedente, la revisión ni la evidencia.
    }
    private void registrarInicio(Origen origen, User actor) {
        AreaOperativa area = plantillaService.bloquearArea(origen.area().getAreaId());
        validarOrdenActiva(origen);
        if (!area.isRequiereDespeje() && !area.isDespejeLineaExclusivaConfirmada() && !ocupada(area)) return;
        if (actor == null || actor.getId() == null) throw new AccessDeniedException("El inicio requiere un actor autenticado.");
        if (!area.isDespejeLineaExclusivaConfirmada()) throw conflicto("No se ha confirmado una línea física exclusiva.");
        validarDisponibilidad(area, origen);
        boolean mismaOcupacion = mismaOcupacion(area, origen);
        if (mismaOcupacion && area.getDespejeUltimoLote() != null
                && !area.getDespejeUltimoLote().getId().equals(lote(origen).getId()))
            throw conflicto("El lote actual no corresponde a la ocupación de la línea. Requiere conciliación operativa.");
        if (mismaOcupacion && tieneConjuntoUtilizado(origen, area)) {
            conjuntoUtilizado(origen, area);
            return;
        }
        if (!area.isRequiereDespeje()) {
            if (!mismaOcupacion) registrarOcupacion(area, origen, lote(origen), ahora());
            return;
        }
        exigirContextoSinUsoPrevio(origen, area);
        List<PlantillaDespejeVersion> vigentes = vigentes(area);
        if (vigentes.isEmpty()) throw conflicto("El área exige despeje, pero no tiene ninguna plantilla vigente. No puede iniciar.");
        Lote lote = lote(origen);
        Map<Long, DespejeLineaEjecucion> ultimas = ultimasPorFamilia(intentos(origen));
        List<DespejeLineaEjecucion> aprobadas = new ArrayList<>();
        for (PlantillaDespejeVersion vigente : vigentes) {
            DespejeLineaEjecucion ultima = ultimas.get(vigente.getFamilia().getId());
            if (ultima != null) entityManager.refresh(ultima, LockModeType.PESSIMISTIC_WRITE);
            if (ultima == null || ultima.getEstado() != APROBADA
                    || !aplicable(ultima, vigente, origen, area, lote))
                throw conflicto("Debe aprobar la versión vigente de «" + vigente.getFamilia().getNombre()
                        + "» para esta operación y contexto. Actualice la preparación.");
            if (ultima.isRequiereRevision() && (ultima.getRevisadaPor() == null
                    || ultima.getRevisadaPor().getId().equals(ultima.getEnviadaPor().getId())))
                throw conflicto("El formulario de «" + vigente.getFamilia().getNombre() + "» requiere una revisión independiente.");
            aprobadas.add(ultima);
        }
        // El catálogo y cada aprobación se comprobaron antes de consumir cualquiera.
        // El bloqueo de área también es compartido por publicación, retiro y altas.
        LocalDateTime utilizadaEn = ahora();
        for (DespejeLineaEjecucion aprobada : aprobadas) {
            aprobada.setEstado(UTILIZADA);
            aprobada.setUtilizadaEn(utilizadaEn);
        }
        if (!mismaOcupacion) registrarOcupacion(area, origen, lote, utilizadaEn);
        ejecucionRepo.flush();
    }

    private void registrarOcupacion(AreaOperativa area, Origen origen, Lote lote, LocalDateTime inicio) {
        area.setDespejeOcupanteSeguimiento(origen.seguimiento());
        area.setDespejeOcupanteOperacion(origen.operacion());
        area.setDespejeUltimoLote(lote);
        area.setDespejeAntecedenteConocido(true);
        area.setDespejeUltimoInicioEn(inicio);
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
            area.setDespejeRevisionContexto(area.getDespejeRevisionContexto() + 1);
        } else if (!ocupada(area) && area.isDespejeLineaExclusivaConfirmada()
                && (origen.estado() == SeguimientoOrdenArea.ESTADO_EN_PROCESO || esReanudacion(origen))) {
            // Una salida heredada no permite atribuir como inmediato el lote histórico del área.
            area.setDespejeAntecedenteConocido(false);
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
        Origen origen = new Origen(area, e.getSeguimiento(), e.getOperacion());
        validarDisponibilidad(area, origen);
        if (ocupada(area) && (origen.estado() != SeguimientoOrdenArea.ESTADO_ESPERA
                || tieneConjuntoUtilizado(origen, area)))
            throw conflicto("La ocupación ya utilizó sus despejes o debe pausarse antes de preparar la reanudación.");
        entityManager.refresh(e.getPlantilla());
        if (e.getPlantilla().getEstado() != PlantillaDespejeVersion.Estado.VIGENTE)
            throw conflicto("La plantilla fue sustituida o retirada antes del arranque. Prepare otro intento.");
        if (e.isRequiereRevision() != area.isRequiereRevisionDespeje())
            throw conflicto("Cambió la política de revisión. Actualice la preparación.");
        if (!lote(origen).getId().equals(e.getLoteActual().getId()))
            throw conflicto("Cambió el lote resultado de la operación. Prepare otro intento.");
        DespejeLineaEjecucion ultima = ultimasPorFamilia(intentos(origen)).get(e.getFamilia().getId());
        if (ultima == null || !ultima.getId().equals(e.getId()))
            throw conflicto("Este formulario fue sustituido por un intento posterior de la misma familia.");
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
    private List<PlantillaDespejeVersion> vigentes(AreaOperativa area) {
        List<PlantillaDespejeVersion> versiones = plantillaRepo
                .findByAreaOperativa_AreaIdAndEstadoOrderByFamilia_IdAsc(
                        area.getAreaId(), PlantillaDespejeVersion.Estado.VIGENTE);
        versiones.forEach(entityManager::refresh);
        return versiones;
    }
    private Map<Long, DespejeLineaEjecucion> ultimasPorFamilia(List<DespejeLineaEjecucion> ejecuciones) {
        Map<Long, DespejeLineaEjecucion> ultimas = new TreeMap<>();
        for (DespejeLineaEjecucion e : ejecuciones) {
            ultimas.merge(e.getFamilia().getId(), e,
                    (actual, candidata) -> candidata.getIntento() > actual.getIntento() ? candidata : actual);
        }
        return ultimas;
    }
    private boolean aplicable(DespejeLineaEjecucion e, PlantillaDespejeVersion vigente,
                              Origen origen, AreaOperativa area, Lote lote) {
        return e.getFamilia().getId().equals(vigente.getFamilia().getId())
                && e.getPlantilla().getId().equals(vigente.getId())
                && e.getAreaOperativa().getAreaId() == area.getAreaId()
                && e.getRevisionContexto() == area.getDespejeRevisionContexto()
                && e.isRequiereRevision() == area.isRequiereRevisionDespeje()
                && e.getLoteActual().getId().equals(lote.getId())
                && mismoOrigen(e, origen);
    }
    private boolean mismoOrigen(DespejeLineaEjecucion e, Origen origen) {
        return origen.seguimiento() != null && e.getSeguimiento() != null
                && origen.seguimiento().getId().equals(e.getSeguimiento().getId())
                || origen.operacion() != null && e.getOperacion() != null
                && origen.operacion().getId().equals(e.getOperacion().getId());
    }
    private boolean tieneConjuntoUtilizado(Origen origen, AreaOperativa area) {
        return intentos(origen).stream().anyMatch(e -> e.getEstado() == UTILIZADA
                && e.getRevisionContexto() == area.getDespejeRevisionContexto()
                && e.getAreaOperativa().getAreaId() == area.getAreaId());
    }

    private boolean esReanudacion(Origen origen) {
        if (origen.estado() != SeguimientoOrdenArea.ESTADO_ESPERA) return false;
        if (origen.seguimiento() != null) {
            SeguimientoOrdenAreaEvento ultimo = entityManager.createQuery("""
                    select e from SeguimientoOrdenAreaEvento e
                    where e.seguimientoOrdenArea.id = :id
                    order by e.fechaEvento desc, e.id desc
                    """, SeguimientoOrdenAreaEvento.class).setParameter("id", origen.seguimiento().getId())
                    .setMaxResults(1).getResultList().stream().findFirst().orElse(null);
            return ultimo != null && ultimo.getTipoEvento() == TipoEventoSeguimiento.OPERATIVO
                    && Objects.equals(ultimo.getEstadoOrigen(), SeguimientoOrdenArea.ESTADO_EN_PROCESO)
                    && ultimo.getEstadoDestino() == SeguimientoOrdenArea.ESTADO_ESPERA;
        }
        OrdenFabricacionOperacionEvento ultimo = entityManager.createQuery("""
                select e from OrdenFabricacionOperacionEvento e
                where e.operacion.id = :id
                order by e.fechaEvento desc, e.id desc
                """, OrdenFabricacionOperacionEvento.class).setParameter("id", origen.operacion().getId())
                .setMaxResults(1).getResultList().stream().findFirst().orElse(null);
        return ultimo != null && ultimo.getTipoEvento() == TipoEventoSeguimiento.OPERATIVO
                && Objects.equals(ultimo.getEstadoOrigen(), SeguimientoOrdenArea.ESTADO_EN_PROCESO)
                && ultimo.getEstadoDestino() == SeguimientoOrdenArea.ESTADO_ESPERA;
    }

    private void validarDisponibilidad(AreaOperativa area, Origen origen) {
        if (ocupada(area) && !mismaOcupacion(area, origen))
            throw conflicto("Otra operación ocupa el área; una pausa conserva su ocupación.");
        if (!area.isDespejeLineaExclusivaConfirmada()) return;
        // Las pausas heredadas sin ocupante se drenan una a una; no admiten nuevos primeros arranques.
        // Solo se leen las otras órdenes: sus bloqueos no se toman después del bloqueo del área.
        boolean incluirPausadas = !esReanudacion(origen);
        Long op = entityManager.createQuery("""
                select count(s) from SeguimientoOrdenArea s
                where s.areaOperativa.areaId = :area and s.id <> :seguimiento
                and s.ordenProduccion.estadoOrden not in (-1,2,3)
                and (s.estado = 4 or (s.estado = 1 and :incluirPausadas = true and exists (
                    select e.id from SeguimientoOrdenAreaEvento e
                    where e.seguimientoOrdenArea = s and e.estadoOrigen = 4 and e.estadoDestino = 1
                    and e.tipoEvento = :operativo and not exists (
                        select posterior.id from SeguimientoOrdenAreaEvento posterior
                        where posterior.seguimientoOrdenArea = s
                        and (posterior.fechaEvento > e.fechaEvento
                            or (posterior.fechaEvento = e.fechaEvento and posterior.id > e.id))
                    )
                )))
                """, Long.class).setParameter("area", area.getAreaId())
                .setParameter("seguimiento", origen.seguimiento() == null ? -1L : origen.seguimiento().getId())
                .setParameter("incluirPausadas", incluirPausadas)
                .setParameter("operativo", TipoEventoSeguimiento.OPERATIVO).getSingleResult();
        Long of = entityManager.createQuery("""
                select count(o) from OrdenFabricacionOperacion o
                where o.areaOperativa.areaId = :area and o.id <> :operacion
                and o.ordenFabricacion.estado in :estadosActivos
                and (o.estado = 4 or (o.estado = 1 and :incluirPausadas = true and exists (
                    select e.id from OrdenFabricacionOperacionEvento e
                    where e.operacion = o and e.estadoOrigen = 4 and e.estadoDestino = 1
                    and e.tipoEvento = :operativo and not exists (
                        select posterior.id from OrdenFabricacionOperacionEvento posterior
                        where posterior.operacion = o
                        and (posterior.fechaEvento > e.fechaEvento
                            or (posterior.fechaEvento = e.fechaEvento and posterior.id > e.id))
                    )
                )))
                """, Long.class).setParameter("area", area.getAreaId())
                .setParameter("operacion", origen.operacion() == null ? -1L : origen.operacion().getId())
                .setParameter("estadosActivos", List.of(
                        exotic.app.planta.model.produccion.fabricacion.EstadoOrdenFabricacion.LIBERADA,
                        exotic.app.planta.model.produccion.fabricacion.EstadoOrdenFabricacion.EN_EJECUCION))
                .setParameter("incluirPausadas", incluirPausadas)
                .setParameter("operativo", TipoEventoSeguimiento.OPERATIVO).getSingleResult();
        if (op > 0 || of > 0)
            throw conflicto("Hay otra operación iniciada o pausada del área. Finalice el trabajo anterior antes de este arranque.");
    }

    /** La ocupación conserva el conjunto consumido, aunque el catálogo cambie después. */
    private List<DespejeLineaEjecucion> conjuntoUtilizado(Origen origen, AreaOperativa area) {
        List<DespejeLineaEjecucion> utilizadas = intentos(origen).stream()
                .filter(e -> e.getEstado() == UTILIZADA
                        && e.getRevisionContexto() == area.getDespejeRevisionContexto()
                        && e.getAreaOperativa().getAreaId() == area.getAreaId())
                .sorted(Comparator.comparing(e -> e.getFamilia().getId())).toList();
        if (utilizadas.isEmpty())
            throw conflicto("La ocupación no tiene un conjunto de despejes utilizado válido. Requiere conciliación operativa.");
        Lote lote = lote(origen);
        Set<Long> familias = new HashSet<>();
        LocalDateTime instante = utilizadas.getFirst().getUtilizadaEn();
        for (DespejeLineaEjecucion e : utilizadas) {
            if (!familias.add(e.getFamilia().getId()) || !e.getLoteActual().getId().equals(lote.getId())
                    || e.getUtilizadaEn() == null || !Objects.equals(instante, e.getUtilizadaEn())
                    || e.isRequiereRevision() != area.isRequiereRevisionDespeje())
                throw conflicto("El conjunto utilizado no corresponde a una única ocupación y contexto coherentes.");
        }
        return utilizadas;
    }
    private void exigirContextoSinUsoPrevio(Origen origen, AreaOperativa area) {
        if (intentos(origen).stream().anyMatch(e -> e.getEstado() == UTILIZADA
                && e.getRevisionContexto() == area.getDespejeRevisionContexto()))
            throw conflicto("Existe un conjunto utilizado sin una ocupación coherente. Requiere conciliación operativa.");
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
    private Requisito requisito(DespejeLineaEjecucion e, boolean listo, String motivo) {
        return new Requisito(e.getFamilia().getId(), e.getFamilia().getNombre(), listo, motivo, toDTO(e));
    }
    private String motivoEstado(DespejeLineaEjecucion e) {
        return switch (e.getEstado()) {
            case APROBADA -> "Formulario aprobado para el contexto actual.";
            case PENDIENTE_REVISION -> "Formulario pendiente de revisión por otra persona autorizada.";
            case RECHAZADA -> "El último intento fue rechazado; prepare otro intento de esta familia.";
            default -> "El formulario todavía no habilita el inicio.";
        };
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
                .map(r -> new Respuesta(r.getItem().getId(), r.getValor(), r.getNumero(), r.getObservacion())).toList(),
                e.getFamilia().getId(), familiaNombre(contexto, e));
    }
    private String familiaNombre(JsonNode contexto, DespejeLineaEjecucion e) {
        String congelado = campo(contexto, "familiaNombre");
        return congelado == null ? e.getFamilia().getNombre() : congelado;
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
