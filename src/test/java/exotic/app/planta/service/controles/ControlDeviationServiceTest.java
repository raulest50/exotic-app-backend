package exotic.app.planta.service.controles;

import exotic.app.planta.model.controles.*;
import exotic.app.planta.model.controles.dto.ControlDTOs.DesviacionCloseRequest;
import exotic.app.planta.model.controles.dto.ControlDTOs.DesviacionResolveRequest;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.controles.ControlRequeridoRepo;
import exotic.app.planta.repo.controles.DesviacionControlRepo;
import exotic.app.planta.repo.controles.EjecucionControlRepo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ControlDeviationServiceTest {

    @Test
    void resolverNoPuedeCruzarLaFachadaDeAmbito() {
        DesviacionControlRepo desviacionRepo = mock(DesviacionControlRepo.class);
        ControlRequeridoRepo requeridoRepo = mock(ControlRequeridoRepo.class);
        ControlDeviationService service = new ControlDeviationService(
                desviacionRepo, requeridoRepo, mock(EjecucionControlRepo.class));

        assertThrows(NoSuchElementException.class, () -> service.resolver(
                AmbitoControl.CALIDAD, 50L, usuario(7L, "calidad"),
                new DesviacionResolveRequest("Investigacion", "Resolucion",
                        DisposicionDesviacionControl.REPETIR)));

        verify(desviacionRepo, never()).saveAndFlush(any());
        verifyNoInteractions(requeridoRepo);
    }

    @Test
    void aceptarJustificadamenteNoReescribeElResultadoOriginal() {
        DesviacionControlRepo desviacionRepo = mock(DesviacionControlRepo.class);
        ControlRequeridoRepo requeridoRepo = mock(ControlRequeridoRepo.class);
        ControlDeviationService service = new ControlDeviationService(
                desviacionRepo, requeridoRepo, mock(EjecucionControlRepo.class));
        User actor = usuario(7L, "director_planta");
        Lote lote = new Lote();
        lote.setId(20L);
        lote.setBatchNumber("L-20");
        ControlRequerido requisito = new ControlRequerido();
        requisito.setId(30L);
        requisito.setLote(lote);
        requisito.setEstado(EstadoControlRequerido.NO_CONFORME);
        requisito.setAmbitoSnapshot(AmbitoControl.PROCESO);
        requisito.setPlanCodigoSnapshot("PC-PESO");
        requisito.setPlanNombreSnapshot("Control de peso");
        requisito.setProductoIdSnapshot("PT-1");
        requisito.setTipoOrdenSnapshot(TipoOrdenControl.OP);
        EjecucionControl ejecucionNoConforme = new EjecucionControl();
        ejecucionNoConforme.setId(40L);
        ejecucionNoConforme.setControlRequerido(requisito);
        ejecucionNoConforme.setResultado(ResultadoEjecucionControl.NO_CONFORME);
        DesviacionControl desviacion = new DesviacionControl();
        desviacion.setId(50L);
        desviacion.setControlRequerido(requisito);
        desviacion.setEjecucionOrigen(ejecucionNoConforme);
        desviacion.setAmbito(AmbitoControl.PROCESO);
        desviacion.setEstado(EstadoDesviacionControl.ABIERTA);
        desviacion.setAbiertaEn(LocalDateTime.of(2026, 2, 1, 9, 0));
        desviacion.setAbiertaPor(usuario(8L, "operario"));

        when(desviacionRepo.findByIdAndAmbitoForUpdate(50L, AmbitoControl.PROCESO))
                .thenReturn(Optional.of(desviacion));
        when(desviacionRepo.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(requeridoRepo.findByIdForUpdate(30L)).thenReturn(Optional.of(requisito));
        when(desviacionRepo.existsByControlRequerido_IdAndEstadoNot(
                30L, EstadoDesviacionControl.CERRADA)).thenReturn(false);

        var response = service.resolver(AmbitoControl.PROCESO, 50L, actor,
                new DesviacionResolveRequest("Se verifico el proceso",
                        "Aceptacion excepcional documentada",
                        DisposicionDesviacionControl.ACEPTAR_JUSTIFICADAMENTE));

        assertEquals(EstadoDesviacionControl.CERRADA, response.estado());
        assertEquals(EstadoControlRequerido.ACEPTADO_POR_DESVIACION, requisito.getEstado());
        assertEquals(ResultadoEjecucionControl.NO_CONFORME, ejecucionNoConforme.getResultado());
        assertSame(actor, desviacion.getCerradaPor());
        verify(requeridoRepo).flush();
    }

    @Test
    void corregirReprocesarMantienePendienteUnaNuevaEjecucion() {
        DesviacionControlRepo desviacionRepo = mock(DesviacionControlRepo.class);
        ControlRequeridoRepo requeridoRepo = mock(ControlRequeridoRepo.class);
        ControlDeviationService service = new ControlDeviationService(
                desviacionRepo, requeridoRepo, mock(EjecucionControlRepo.class));
        ControlRequerido requisito = requisitoMinimo();
        DesviacionControl desviacion = desviacionMinima(requisito);
        when(desviacionRepo.findByIdAndAmbitoForUpdate(50L, AmbitoControl.PROCESO))
                .thenReturn(Optional.of(desviacion));
        when(desviacionRepo.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(requeridoRepo.findByIdForUpdate(30L)).thenReturn(Optional.of(requisito));

        service.resolver(AmbitoControl.PROCESO, 50L, usuario(7L, "director_planta"),
                new DesviacionResolveRequest("Investigacion", "Reprocesar producto",
                        DisposicionDesviacionControl.CORREGIR_REPROCESAR));

        assertEquals(EstadoControlRequerido.PENDIENTE, requisito.getEstado());
        assertEquals(true, requisito.isRequiereRepeticion());
    }

    @Test
    void cierreDeCalidadConservaResolucionYRegistraJustificacionDeDisposicionSeparada() {
        DesviacionControlRepo desviacionRepo = mock(DesviacionControlRepo.class);
        ControlRequeridoRepo requeridoRepo = mock(ControlRequeridoRepo.class);
        ControlDeviationService service = new ControlDeviationService(
                desviacionRepo, requeridoRepo, mock(EjecucionControlRepo.class));
        ControlRequerido requisito = requisitoMinimo();
        requisito.setAmbitoSnapshot(AmbitoControl.CALIDAD);
        DesviacionControl desviacion = desviacionMinima(requisito);
        desviacion.setAmbito(AmbitoControl.CALIDAD);
        desviacion.setEstado(EstadoDesviacionControl.RESUELTA);
        desviacion.setInvestigacion("Investigacion del analista");
        desviacion.setResolucion("Resolucion tecnica del analista");
        desviacion.setResueltaPor(usuario(8L, "analista_calidad"));
        when(desviacionRepo.findByIdAndAmbitoForUpdate(50L, AmbitoControl.CALIDAD))
                .thenReturn(Optional.of(desviacion));
        when(desviacionRepo.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(requeridoRepo.findByIdForUpdate(30L)).thenReturn(Optional.of(requisito));
        when(desviacionRepo.existsByControlRequerido_IdAndEstadoNot(
                30L, EstadoDesviacionControl.CERRADA)).thenReturn(false);

        service.cerrar(AmbitoControl.CALIDAD, 50L, usuario(9L, "jefe_calidad"),
                new DesviacionCloseRequest(
                        DisposicionDesviacionControl.ACEPTAR_JUSTIFICADAMENTE,
                        "Riesgo evaluado y aceptado por Calidad"));

        assertEquals("Resolucion tecnica del analista", desviacion.getResolucion());
        assertEquals("Riesgo evaluado y aceptado por Calidad",
                desviacion.getJustificacionDisposicion());
        assertEquals(EstadoControlRequerido.ACEPTADO_POR_DESVIACION, requisito.getEstado());
    }

    @Test
    void rechazoCerradoPrevioNoPuedeSerSobrescritoPorAceptacionPosterior() {
        DesviacionControlRepo desviacionRepo = mock(DesviacionControlRepo.class);
        ControlRequeridoRepo requeridoRepo = mock(ControlRequeridoRepo.class);
        ControlDeviationService service = new ControlDeviationService(
                desviacionRepo, requeridoRepo, mock(EjecucionControlRepo.class));
        ControlRequerido requisito = requisitoMinimo();
        requisito.setAmbitoSnapshot(AmbitoControl.CALIDAD);
        requisito.setRequiereRepeticion(true);
        DesviacionControl desviacion = desviacionMinima(requisito);
        desviacion.setAmbito(AmbitoControl.CALIDAD);
        desviacion.setEstado(EstadoDesviacionControl.RESUELTA);
        desviacion.setResolucion("Resolucion de una segunda desviacion");
        desviacion.setResueltaPor(usuario(9L, "analista"));
        User cerrador = usuario(10L, "jefe-calidad");

        when(desviacionRepo.findByIdAndAmbitoForUpdate(50L, AmbitoControl.CALIDAD))
                .thenReturn(java.util.Optional.of(desviacion));
        when(desviacionRepo.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(requeridoRepo.findByIdForUpdate(30L)).thenReturn(java.util.Optional.of(requisito));
        when(desviacionRepo.existsByControlRequerido_IdAndEstadoAndDisposicion(
                30L, EstadoDesviacionControl.CERRADA, DisposicionDesviacionControl.RECHAZAR))
                .thenReturn(true);

        service.cerrar(AmbitoControl.CALIDAD, 50L, cerrador,
                new DesviacionCloseRequest(
                        DisposicionDesviacionControl.ACEPTAR_JUSTIFICADAMENTE,
                        "Aceptacion de la segunda desviacion"));

        assertEquals(EstadoControlRequerido.NO_CONFORME, requisito.getEstado());
        assertEquals(false, requisito.isRequiereRepeticion());
    }

    private ControlRequerido requisitoMinimo() {
        Lote lote = new Lote();
        lote.setId(20L);
        lote.setBatchNumber("L-20");
        ControlRequerido requisito = new ControlRequerido();
        requisito.setId(30L);
        requisito.setLote(lote);
        requisito.setEstado(EstadoControlRequerido.NO_CONFORME);
        requisito.setAmbitoSnapshot(AmbitoControl.PROCESO);
        requisito.setPlanCodigoSnapshot("PC-PESO");
        requisito.setPlanNombreSnapshot("Control de peso");
        requisito.setProductoIdSnapshot("PT-1");
        requisito.setTipoOrdenSnapshot(TipoOrdenControl.OP);
        return requisito;
    }

    private DesviacionControl desviacionMinima(ControlRequerido requisito) {
        EjecucionControl ejecucion = new EjecucionControl();
        ejecucion.setId(40L);
        ejecucion.setControlRequerido(requisito);
        ejecucion.setResultado(ResultadoEjecucionControl.NO_CONFORME);
        DesviacionControl desviacion = new DesviacionControl();
        desviacion.setId(50L);
        desviacion.setControlRequerido(requisito);
        desviacion.setEjecucionOrigen(ejecucion);
        desviacion.setAmbito(AmbitoControl.PROCESO);
        desviacion.setEstado(EstadoDesviacionControl.ABIERTA);
        desviacion.setAbiertaEn(LocalDateTime.of(2026, 2, 1, 9, 0));
        desviacion.setAbiertaPor(usuario(8L, "operario"));
        return desviacion;
    }

    private User usuario(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
