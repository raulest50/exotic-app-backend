package exotic.app.planta.service.compras;

import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.compras.OrigenCierreOcm;
import exotic.app.planta.model.compras.dto.OcmCierreDTOs.*;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcmCierreService {
    private final OrdenCompraRepo ordenes;
    private final RecepcionCompletaOcmService recepcion;
    private final OcmCierreConfigService configuracion;
    private final Clock applicationClock;

    /** Caller holds configuration, then order locks. Receipt and edit use this same transition. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void actualizarRecepcion(OrdenCompraMateriales orden, boolean completaAntes, Config config) {
        if (orden.getEstado() != 2) return;
        boolean completaAhora = recepcion.estaCompleta(orden);
        if (!completaAhora) orden.setFechaRecepcionCompleta(null);
        else if (!completaAntes) orden.setFechaRecepcionCompleta(LocalDateTime.now(applicationClock).truncatedTo(ChronoUnit.MICROS));
        // A historical complete order with no timestamp keeps that unknown date.
        cerrarSiVencida(orden, completaAhora, config);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarCierre(OrdenCompraMateriales orden, OrigenCierreOcm origen, String username) {
        if (orden.getEstado() == 3) return;
        orden.setEstado(3);
        orden.setFechaCierre(LocalDateTime.now(applicationClock));
        orden.setOrigenCierre(origen);
        orden.setUsuarioCierreUsername(username);
        log.info("OCM {} cerrada: origen={}, usuario={}", orden.getOrdenCompraId(), origen, username);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cerrarAutomaticamente(int id) {
        Config config = configuracion.bloquearParaOperacion();
        if (config.modo() == Modo.DESACTIVADO) return;
        ordenes.findByOrdenCompraIdForUpdate(id).ifPresent(orden -> {
            if (orden.getEstado() != 2 || fechaPrevista(orden, config) == null) return;
            boolean completa = recepcion.estaCompleta(orden);
            if (!completa) orden.setFechaRecepcionCompleta(null);
            cerrarSiVencida(orden, completa, config);
        });
    }

    /** Null means closed; otherwise an explicit reason for skipping this selected order. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String cerrarCompletaManualmente(int id, String username) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Se requiere un usuario autenticado.");
        OrdenCompraMateriales orden = ordenes.findByOrdenCompraIdForUpdate(id).orElse(null);
        if (orden == null) return "La OCM ya no existe.";
        if (orden.getEstado() != 2) return "La OCM ya no está pendiente de recepción.";
        if (!recepcion.estaCompleta(orden)) return "La OCM ya no tiene recepción completa.";
        registrarCierre(orden, OrigenCierreOcm.MANUAL_DIRECTIVAS, username);
        return null;
    }

    @Transactional(readOnly = true)
    public EstadoRecepcion consultarEstado(int id) {
        OrdenCompraMateriales orden = ordenes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La OCM no existe."));
        boolean completa = recepcion.estaCompleta(orden);
        return new EstadoRecepcion(id, orden.getEstado(), completa, orden.getFechaRecepcionCompleta(),
                completa ? fechaPrevista(orden, configuracion.consultar()) : null,
                orden.getFechaCierre(), orden.getOrigenCierre(), orden.getUsuarioCierreUsername());
    }

    static LocalDateTime fechaPrevista(OrdenCompraMateriales orden, Config config) {
        LocalDateTime completa = orden.getFechaRecepcionCompleta();
        if (orden.getEstado() != 2 || config.modo() == Modo.DESACTIVADO || completa == null
                || config.activadoDesde() == null || completa.isBefore(config.activadoDesde())) return null;
        return config.modo() == Modo.PLAZO ? completa.plusDays(config.dias()) : completa;
    }

    private void cerrarSiVencida(OrdenCompraMateriales orden, boolean completa, Config config) {
        LocalDateTime prevista = fechaPrevista(orden, config);
        if (completa && prevista != null && !LocalDateTime.now(applicationClock).isBefore(prevista)) {
            registrarCierre(orden, config.modo() == Modo.PLAZO
                    ? OrigenCierreOcm.AUTOMATICO_PLAZO : OrigenCierreOcm.AUTOMATICO_RECEPCION, null);
        }
    }
}
