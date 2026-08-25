package exotic.app.planta.service.commons.notificaciones;

import exotic.app.planta.model.commons.notificaciones.PuntoReordenEvaluacionResult;
import exotic.app.planta.model.empresa.EmpresaIdentidadLegalVersion;
import exotic.app.planta.model.notificaciones.MaestraNotificacion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.notificaciones.MaestraNotificacionRepo;
import exotic.app.planta.service.commons.EmailService;
import exotic.app.planta.service.empresa.EmpresaIdentidadLegalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PuntoReordenAlertSchedulerTest {

    private PuntoReordenEvaluacionService evaluacionService;
    private MaestraNotificacionRepo notificacionRepo;
    private EmailService emailService;
    private EmpresaIdentidadLegalService identidadLegalService;
    private PuntoReordenAlertScheduler scheduler;

    @BeforeEach
    void setUp() {
        evaluacionService = mock(PuntoReordenEvaluacionService.class);
        notificacionRepo = mock(MaestraNotificacionRepo.class);
        emailService = mock(EmailService.class);
        identidadLegalService = mock(EmpresaIdentidadLegalService.class);
        scheduler = new PuntoReordenAlertScheduler(
                evaluacionService,
                notificacionRepo,
                emailService,
                identidadLegalService
        );
    }

    @Test
    void checkPuntosDeReorden_usesCurrentIdentityInSubjectAndEscapedHtmlFooter() throws Exception {
        when(notificacionRepo.findByIdWithUsersGroup(1)).thenReturn(Optional.of(notificacion("destino@example.com")));
        when(evaluacionService.evaluar()).thenReturn(evaluacion(1));
        when(identidadLegalService.getVigente()).thenReturn(identidadVigente());

        scheduler.checkPuntosDeReorden();

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendHtmlEmail(
                org.mockito.ArgumentMatchers.eq("destino@example.com"),
                subjectCaptor.capture(),
                htmlCaptor.capture()
        );

        assertTrue(subjectCaptor.getValue().startsWith("[Nueva Marca & Co] Alerta de Punto de Reorden - "));
        String html = htmlCaptor.getValue();
        assertTrue(html.contains("<strong>Nueva Marca &amp; Co</strong>"));
        assertTrue(html.contains("Nueva Razón &lt;S.A.S.&gt;"));
        assertTrue(html.contains("Teléfono: 601 &amp; 123"));
        assertTrue(html.contains("Correo: compras&amp;alertas@example.com"));
        verify(identidadLegalService).getVigente();
    }

    @Test
    void checkPuntosDeReorden_doesNotResolveIdentityOrSendWhenThereAreNoAlerts() throws Exception {
        when(notificacionRepo.findByIdWithUsersGroup(1)).thenReturn(Optional.of(notificacion("destino@example.com")));
        when(evaluacionService.evaluar()).thenReturn(evaluacion(0));

        scheduler.checkPuntosDeReorden();

        verify(identidadLegalService, never()).getVigente();
        verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString());
    }

    @Test
    void checkPuntosDeReorden_doesNotSendWhenThereIsNoCurrentIdentity() throws Exception {
        when(notificacionRepo.findByIdWithUsersGroup(1)).thenReturn(Optional.of(notificacion("destino@example.com")));
        when(evaluacionService.evaluar()).thenReturn(evaluacion(1));
        when(identidadLegalService.getVigente()).thenThrow(new IllegalStateException("Sin identidad vigente"));

        scheduler.checkPuntosDeReorden();

        verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString());
    }

    private static MaestraNotificacion notificacion(String email) {
        User user = new User();
        user.setEmail(email);
        MaestraNotificacion notificacion = new MaestraNotificacion();
        notificacion.setId(1);
        notificacion.setUsersGroup(List.of(user));
        return notificacion;
    }

    private static PuntoReordenEvaluacionResult evaluacion(long totalEnAlerta) {
        return new PuntoReordenEvaluacionResult(
                List.of(),
                List.of(),
                List.of(),
                0,
                0,
                0,
                totalEnAlerta
        );
    }

    private static EmpresaIdentidadLegalVersion identidadVigente() {
        EmpresaIdentidadLegalVersion identidad = new EmpresaIdentidadLegalVersion();
        identidad.setId(10L);
        identidad.setNombreComercial("Nueva Marca & Co");
        identidad.setRazonSocial("Nueva Razón <S.A.S.>");
        identidad.setTelefonoPrincipal("601 & 123");
        identidad.setEmailPrincipal("compras&alertas@example.com");
        return identidad;
    }
}
