package exotic.app.planta.service.commons.notificaciones;

import exotic.app.planta.model.commons.notificaciones.MaterialEnPuntoReordenConOcmDTO;
import exotic.app.planta.model.commons.notificaciones.MaterialEnPuntoReordenDTO;
import exotic.app.planta.model.commons.notificaciones.OcmPendienteIngresoDTO;
import exotic.app.planta.model.commons.notificaciones.PuntoReordenEvaluacionResult;
import exotic.app.planta.model.notificaciones.MaestraNotificacion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.notificaciones.MaestraNotificacionRepo;
import exotic.app.planta.service.commons.EmailService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Alerta periodica: materiales con stock en o bajo el punto de reorden.
 * Correo a los usuarios del grupo de la maestra de notificacion id=1.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PuntoReordenAlertScheduler {

    private static final int NOTIFICACION_ID = 1;
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter FECHA_OCM_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final PuntoReordenEvaluacionService puntoReordenEvaluacionService;
    private final MaestraNotificacionRepo maestraNotificacionRepo;
    private final EmailService emailService;

    @Scheduled(cron = "0 0 7 * * MON-FRI", zone = "America/Bogota")
    @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "America/Bogota")
    @Transactional(readOnly = true)
    public void checkPuntosDeReorden() {
        MaestraNotificacion notificacion = maestraNotificacionRepo.findByIdWithUsersGroup(NOTIFICACION_ID).orElse(null);
        if (notificacion == null || notificacion.getUsersGroup() == null || notificacion.getUsersGroup().isEmpty()) {
            log.debug("PuntoReordenAlert: sin maestra notificacion {} o sin usuarios en grupo; omitido.", NOTIFICACION_ID);
            return;
        }

        List<User> destinatarios = notificacion.getUsersGroup().stream()
                .filter(Objects::nonNull)
                .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                .toList();
        if (destinatarios.isEmpty()) {
            log.debug("PuntoReordenAlert: ningun usuario con correo en el grupo; omitido.");
            return;
        }

        PuntoReordenEvaluacionResult eval = puntoReordenEvaluacionService.evaluar();
        if (eval.totalEnAlerta() == 0) {
            log.info("PuntoReordenAlert: no hay materiales en alerta para correo.");
            return;
        }

        String fecha = LocalDate.now(BOGOTA).toString();
        String subject = "[Exotic] Alerta de Punto de Reorden - " + fecha;
        String html = buildHtmlBody(eval);

        for (User user : destinatarios) {
            try {
                emailService.sendHtmlEmail(user.getEmail().trim(), subject, html);
            } catch (MessagingException e) {
                log.warn("PuntoReordenAlert: fallo envio a {}: {}", user.getEmail(), e.getMessage());
            }
        }
        log.info("PuntoReordenAlert: correo enviado a {} destinatario(s), {} material(es) en alerta.",
                destinatarios.size(), eval.totalEnAlerta());
    }

    private static String buildHtmlBody(PuntoReordenEvaluacionResult eval) {
        StringBuilder sb = new StringBuilder(3072);
        sb.append("<html><body>");
        sb.append("<h2>Resumen de alerta de punto de reorden</h2>");
        sb.append("<p>Total en alerta: <strong>")
                .append(eval.totalEnAlerta())
                .append("</strong></p>");

        if (!eval.pendientesOrdenar().isEmpty()) {
            sb.append("<h3>Materiales que requieren generar OCM</h3>");
            sb.append("<table border=\"1\" cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse:collapse;\">");
            sb.append("<thead><tr><th>Codigo</th><th>Nombre</th><th>Tipo</th><th>Stock actual</th><th>Punto reorden</th></tr></thead><tbody>");
            for (MaterialEnPuntoReordenDTO material : eval.pendientesOrdenar()) {
                appendBaseMaterialRow(sb, material);
            }
            sb.append("</tbody></table>");
        }

        if (!eval.pendientesIngresoAlmacen().isEmpty()) {
            sb.append("<h3 style=\"margin-top:1.5em;\">Materiales ya solicitados, pendientes de ingreso</h3>");
            sb.append("<table border=\"1\" cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse:collapse;\">");
            sb.append("<thead><tr><th>Codigo</th><th>Nombre</th><th>Tipo</th><th>Stock actual</th><th>Punto reorden</th><th>OCM(s)</th></tr></thead><tbody>");
            for (MaterialEnPuntoReordenConOcmDTO material : eval.pendientesIngresoAlmacen()) {
                sb.append("<tr><td>").append(escapeHtml(material.getProductoId())).append("</td><td>")
                        .append(escapeHtml(material.getNombre())).append("</td><td>")
                        .append(escapeHtml(material.getTipoMaterialLabel())).append("</td><td>")
                        .append(formatQty(material.getStockActual())).append(" ").append(escapeHtml(material.getTipoUnidades())).append("</td><td>")
                        .append(formatQty(material.getPuntoReorden())).append(" ").append(escapeHtml(material.getTipoUnidades())).append("</td><td>");

                for (int i = 0; i < material.getOcmsPendientesIngreso().size(); i++) {
                    OcmPendienteIngresoDTO ocm = material.getOcmsPendientesIngreso().get(i);
                    if (i > 0) {
                        sb.append("<br/>");
                    }
                    sb.append("OCM #")
                            .append(ocm.getOrdenCompraId())
                            .append(" (")
                            .append(ocm.getFechaEmision() == null
                                    ? ""
                                    : escapeHtml(ocm.getFechaEmision().format(FECHA_OCM_FORMAT)))
                            .append(")");
                }
                sb.append("</td></tr>");
            }
            sb.append("</tbody></table>");
        }

        if (!eval.sinPuntoReorden().isEmpty()) {
            sb.append("<h3 style=\"margin-top:1.5em;\">Materiales sin punto de reorden configurado</h3>");
            sb.append("<p>Revise y defina un punto de reorden si aplica.</p><ul>");
            for (MaterialEnPuntoReordenDTO material : eval.sinPuntoReorden()) {
                sb.append("<li>").append(escapeHtml(material.getProductoId())).append(" / ")
                        .append(escapeHtml(material.getNombre())).append("</li>");
            }
            sb.append("</ul>");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private static void appendBaseMaterialRow(StringBuilder sb, MaterialEnPuntoReordenDTO material) {
        sb.append("<tr><td>").append(escapeHtml(material.getProductoId())).append("</td><td>")
                .append(escapeHtml(material.getNombre())).append("</td><td>")
                .append(escapeHtml(material.getTipoMaterialLabel())).append("</td><td>")
                .append(formatQty(material.getStockActual())).append(" ").append(escapeHtml(material.getTipoUnidades())).append("</td><td>")
                .append(formatQty(material.getPuntoReorden())).append(" ").append(escapeHtml(material.getTipoUnidades()))
                .append("</td></tr>");
    }

    private static String formatQty(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0";
        }
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.US, "%.4f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
