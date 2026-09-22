package exotic.app.planta.service.compras;

import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.compras.OrigenCierreOcm;
import exotic.app.planta.model.compras.dto.OcmCierreDTOs.Config;
import exotic.app.planta.model.compras.dto.OcmCierreDTOs.Modo;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OcmCierreServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-22T15:00:00Z"), ZoneId.of("America/Bogota"));
    private final LocalDateTime now = LocalDateTime.now(clock);
    private OrdenCompraRepo ordenes;
    private RecepcionCompletaOcmService recepcion;
    private OcmCierreConfigService config;
    private OcmCierreService service;
    private OrdenCompraMateriales orden;

    @BeforeEach
    void preparar() {
        ordenes = mock(OrdenCompraRepo.class);
        recepcion = mock(RecepcionCompletaOcmService.class);
        config = mock(OcmCierreConfigService.class);
        service = new OcmCierreService(ordenes, recepcion, config, clock);
        orden = new OrdenCompraMateriales();
        orden.setOrdenCompraId(7);
        orden.setEstado(2);
    }

    @Test
    void registraLaFechaApagadoYNoLaReescribeMientrasPermaneceCompleta() {
        when(recepcion.estaCompleta(orden)).thenReturn(true);
        service.actualizarRecepcion(orden, false, Config.desactivada());
        assertEquals(now, orden.getFechaRecepcionCompleta());
        assertEquals(2, orden.getEstado());
        orden.setFechaRecepcionCompleta(now.minusDays(2));
        service.actualizarRecepcion(orden, true, Config.desactivada());
        assertEquals(now.minusDays(2), orden.getFechaRecepcionCompleta());
    }

    @Test
    void limpiaLaFechaAlQuedarIncompletaYLaRenuevaAlCompletarDeNuevo() {
        orden.setFechaRecepcionCompleta(now.minusDays(2));
        when(recepcion.estaCompleta(orden)).thenReturn(false, true);
        service.actualizarRecepcion(orden, true, Config.desactivada());
        assertNull(orden.getFechaRecepcionCompleta());
        service.actualizarRecepcion(orden, false, Config.desactivada());
        assertEquals(now, orden.getFechaRecepcionCompleta());
    }

    @Test
    void completaHistoricaNoRecibeFechaInventadaNiCierreAutomatico() {
        when(recepcion.estaCompleta(orden)).thenReturn(true);
        service.actualizarRecepcion(orden, true, new Config(Modo.RECEPCION_COMPLETA, null, now.minusDays(1)));
        assertNull(orden.getFechaRecepcionCompleta());
        assertEquals(2, orden.getEstado());
    }

    @Test
    void cierraAlCompletarSinInventarUsuario() {
        when(recepcion.estaCompleta(orden)).thenReturn(true);
        service.actualizarRecepcion(orden, false, new Config(Modo.RECEPCION_COMPLETA, null, now.minusHours(1)));
        assertEquals(3, orden.getEstado());
        assertEquals(now, orden.getFechaCierre());
        assertEquals(OrigenCierreOcm.AUTOMATICO_RECEPCION, orden.getOrigenCierre());
        assertNull(orden.getUsuarioCierreUsername());
    }

    @Test
    void elPlazoSeCumpleALaMismaHoraYSeRevalidaConBloqueos() {
        when(config.bloquearParaOperacion()).thenReturn(new Config(Modo.PLAZO, 2, now.minusDays(5)));
        when(ordenes.findByOrdenCompraIdForUpdate(7)).thenReturn(Optional.of(orden));
        when(recepcion.estaCompleta(orden)).thenReturn(true);
        orden.setFechaRecepcionCompleta(now.minusDays(2).plusSeconds(1));
        service.cerrarAutomaticamente(7);
        assertEquals(2, orden.getEstado());
        orden.setFechaRecepcionCompleta(now.minusDays(2));
        service.cerrarAutomaticamente(7);
        assertEquals(3, orden.getEstado());
        assertEquals(OrigenCierreOcm.AUTOMATICO_PLAZO, orden.getOrigenCierre());
        var order = inOrder(config, ordenes, recepcion);
        order.verify(config).bloquearParaOperacion();
        order.verify(ordenes).findByOrdenCompraIdForUpdate(7);
        order.verify(recepcion).estaCompleta(orden);
    }

    @Test
    void laActivacionNoIncluyeCompletasAnteriores() {
        orden.setFechaRecepcionCompleta(now.minusDays(1));
        assertNull(OcmCierreService.fechaPrevista(orden, new Config(Modo.RECEPCION_COMPLETA, null, now)));
        orden.setFechaRecepcionCompleta(now);
        assertEquals(now, OcmCierreService.fechaPrevista(orden, new Config(Modo.RECEPCION_COMPLETA, null, now)));
    }

    @Test
    void desactivarDetieneUnCandidatoYaSeleccionadoPorElScheduler() {
        when(config.bloquearParaOperacion()).thenReturn(Config.desactivada());
        service.cerrarAutomaticamente(7);
        verifyNoInteractions(ordenes, recepcion);
    }

    @Test
    void noCierraSiLasCantidadesCambiaronDesdeLaSeleccion() {
        when(config.bloquearParaOperacion()).thenReturn(new Config(Modo.RECEPCION_COMPLETA, null, now.minusDays(2)));
        when(ordenes.findByOrdenCompraIdForUpdate(7)).thenReturn(Optional.of(orden));
        orden.setFechaRecepcionCompleta(now.minusDays(1));
        service.cerrarAutomaticamente(7);
        assertEquals(2, orden.getEstado());
        assertNull(orden.getFechaRecepcionCompleta());
    }

    @Test
    void cierreManualIncluyeHistoricasSinFabricarFechaYReintentarNoCambiaAuditoria() {
        when(ordenes.findByOrdenCompraIdForUpdate(7)).thenReturn(Optional.of(orden));
        when(recepcion.estaCompleta(orden)).thenReturn(true);
        assertNull(service.cerrarCompletaManualmente(7, "master"));
        assertNull(orden.getFechaRecepcionCompleta());
        assertEquals(OrigenCierreOcm.MANUAL_DIRECTIVAS, orden.getOrigenCierre());
        assertEquals("master", orden.getUsuarioCierreUsername());
        assertNotNull(service.cerrarCompletaManualmente(7, "super_master"));
        service.registrarCierre(orden, OrigenCierreOcm.MANUAL, "otro");
        assertEquals("master", orden.getUsuarioCierreUsername());
        assertEquals(now, orden.getFechaCierre());
        verifyNoInteractions(config);
    }

    @Test
    void cierreMasivoOmiteOrdenIncompletaOCancelada() {
        when(ordenes.findByOrdenCompraIdForUpdate(7)).thenReturn(Optional.of(orden));
        assertNotNull(service.cerrarCompletaManualmente(7, "master"));
        assertEquals(2, orden.getEstado());
        orden.setEstado(-1);
        assertNotNull(service.cerrarCompletaManualmente(7, "master"));
        assertEquals(-1, orden.getEstado());
        assertNull(orden.getFechaCierre());
    }
}
