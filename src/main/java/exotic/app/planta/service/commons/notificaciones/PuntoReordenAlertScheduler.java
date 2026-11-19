package exotic.app.planta.service.commons.notificaciones;

import exotic.app.planta.model.commons.notificaciones.PuntoReordenCorreoData;
import exotic.app.planta.model.notificaciones.MaestraNotificacion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.notificaciones.MaestraNotificacionRepo;
import exotic.app.planta.service.commons.EmailService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Map;

/** Programa el correo de reorden y entrega un mismo snapshot a sus destinatarios. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PuntoReordenAlertScheduler {

    private static final int NOTIFICACION_ID = 1;

    private final PuntoReordenCorreoService puntoReordenCorreoService;
    private final PuntoReordenCorreoRenderer puntoReordenCorreoRenderer;
    private final MaestraNotificacionRepo maestraNotificacionRepo;
    private final EmailService emailService;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled = true;

    @Scheduled(cron = "0 0 7 * * MON-FRI", zone = "America/Bogota")
    @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "America/Bogota")
    public void checkPuntosDeReorden() {
        if (!mailEnabled) {
            log.debug("PuntoReordenAlert: correo deshabilitado en este entorno; omitido.");
            return;
        }
        MaestraNotificacion notificacion = maestraNotificacionRepo.findByIdWithUsersGroup(NOTIFICACION_ID).orElse(null);
        if (notificacion == null || notificacion.getUsersGroup() == null) {
            log.debug("PuntoReordenAlert: sin maestra de notificacion; omitido.");
            return;
        }
        List<String> destinatarios = notificacion.getUsersGroup().stream()
                .filter(Objects::nonNull)
                .map(User::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (destinatarios.isEmpty()) {
            log.debug("PuntoReordenAlert: sin destinatarios con correo; omitido.");
            return;
        }

        // La transaccion de lectura de preparar() termina antes de renderizar o usar SMTP.
        PuntoReordenCorreoData data;
        String html;
        Map<String, Resource> inlineImages;
        try {
            data = puntoReordenCorreoService.preparar();
            if (data.totalEnAlerta() == 0) {
                log.info("PuntoReordenAlert: no hay materiales en alerta para correo.");
                return;
            }
            html = puntoReordenCorreoRenderer.render(data);
            inlineImages = puntoReordenCorreoRenderer.inlineImages();
        } catch (RuntimeException exception) {
            log.error("PuntoReordenAlert: no fue posible preparar el correo; envio omitido.", exception);
            return;
        }

        String subject = "[" + data.identidad().nombreComercial().trim()
                + "] Alerta de Punto de Reorden - " + data.fechaCorte();
        int enviados = 0;
        int fallidos = 0;
        for (String email : destinatarios) {
            try {
                emailService.sendHtmlEmail(email, subject, html, inlineImages);
                enviados++;
            } catch (MessagingException | MailException exception) {
                fallidos++;
                log.warn("PuntoReordenAlert: fallo de envio a un destinatario: {}", exception.getMessage());
            }
        }
        log.info("PuntoReordenAlert: {} enviado(s), {} fallido(s), {} material(es) en alerta.",
                enviados, fallidos, data.totalEnAlerta());
    }
}
