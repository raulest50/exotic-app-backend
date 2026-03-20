package exotic.app.planta.service.commons.notificaciones;

import exotic.app.planta.model.notificaciones.MaestraNotificacion;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Alerta periódica: materiales con stock en o bajo el punto de reorden.
 * Correo a los usuarios del grupo de la maestra de notificación id=1.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PuntoReordenAlertScheduler {

    private static final int NOTIFICACION_ID = 1;
    private static final double PUNTO_REORDEN_IGNORAR = -1.0;
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final TransaccionAlmacenRepo transaccionAlmacenRepo;
    private final MaestraNotificacionRepo maestraNotificacionRepo;
    private final EmailService emailService;

    @Scheduled(cron = "0 0 7 * * MON-FRI", zone = "America/Bogota")
    @Scheduled(cron = "0 30 12 * * MON-FRI", zone = "America/Bogota")
    @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "America/Bogota")
    @Transactional(readOnly = true)
    public void checkPuntosDeReorden() {
        MaestraNotificacion notificacion = maestraNotificacionRepo.findByIdWithUsersGroup(NOTIFICACION_ID).orElse(null);
        if (notificacion == null || notificacion.getUsersGroup() == null || notificacion.getUsersGroup().isEmpty()) {
            log.debug("PuntoReordenAlert: sin maestra notificación {} o sin usuarios en grupo; omitido.", NOTIFICACION_ID);
            return;
        }

        List<User> destinatarios = notificacion.getUsersGroup().stream()
                .filter(Objects::nonNull)
                .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                .toList();
        if (destinatarios.isEmpty()) {
            log.debug("PuntoReordenAlert: ningún usuario con correo en el grupo; omitido.");
            return;
        }

        List<MaterialStockRow> rows = mapMaterialsWithStock(transaccionAlmacenRepo.findAllMaterialsWithStock());
        List<MaterialStockRow> enReorden = new ArrayList<>();
        List<MaterialStockRow> sinPunto = new ArrayList<>();

        for (MaterialStockRow row : rows) {
            double pr = row.material().getPuntoReorden();
            if (pr == PUNTO_REORDEN_IGNORAR) {
                continue;
            }
            if (pr == 0.0) {
                sinPunto.add(row);
                continue;
            }
            if (pr > 0 && row.stock() <= pr) {
                enReorden.add(row);
            }
        }

        if (enReorden.isEmpty()) {
            log.info("PuntoReordenAlert: no hay materiales en o bajo punto de reorden; no se envía correo.");
            return;
        }

        String fecha = LocalDate.now(BOGOTA).toString();
        String subject = "[Exotic] Alerta de Punto de Reorden — " + fecha;
        String html = buildHtmlBody(enReorden, sinPunto);

        for (User user : destinatarios) {
            try {
                emailService.sendHtmlEmail(user.getEmail().trim(), subject, html);
            } catch (MessagingException e) {
                log.warn("PuntoReordenAlert: fallo envío a {}: {}", user.getEmail(), e.getMessage());
            }
        }
        log.info("PuntoReordenAlert: correo enviado a {} destinatario(s), {} material(es) en reorden.",
                destinatarios.size(), enReorden.size());
    }

    private static List<MaterialStockRow> mapMaterialsWithStock(List<Object[]> raw) {
        List<MaterialStockRow> out = new ArrayList<>(raw.size());
        for (Object[] row : raw) {
            Material m = (Material) row[0];
            double stock = row[1] instanceof Number n ? n.doubleValue() : 0.0;
            out.add(new MaterialStockRow(m, stock));
        }
        return out;
    }

    private static String buildHtmlBody(List<MaterialStockRow> enReorden, List<MaterialStockRow> sinPunto) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<html><body>");
        sb.append("<h2>Materiales que requieren Orden de Compra</h2>");
        sb.append("<p>El stock actual es igual o inferior al punto de reorden configurado.</p>");
        sb.append("<table border=\"1\" cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse:collapse;\">");
        sb.append("<thead><tr><th>Código</th><th>Nombre</th><th>Tipo</th><th>Stock actual</th><th>Punto reorden</th></tr></thead><tbody>");
        for (MaterialStockRow r : enReorden) {
            Material m = r.material();
            String unidad = m.getTipoUnidades() != null ? m.getTipoUnidades() : "";
            sb.append("<tr><td>").append(escapeHtml(m.getProductoId())).append("</td><td>")
                    .append(escapeHtml(m.getNombre())).append("</td><td>")
                    .append(escapeHtml(tipoMaterialLabel(m.getTipoMaterial()))).append("</td><td>")
                    .append(formatQty(r.stock())).append(" ").append(escapeHtml(unidad)).append("</td><td>")
                    .append(formatQty(m.getPuntoReorden())).append(" ").append(escapeHtml(unidad))
                    .append("</td></tr>");
        }
        sb.append("</tbody></table>");

        if (!sinPunto.isEmpty()) {
            sb.append("<h3 style=\"margin-top:1.5em;\">Materiales sin punto de reorden configurado</h3>");
            sb.append("<p>Revise y defina un punto de reorden si aplica.</p><ul>");
            for (MaterialStockRow r : sinPunto) {
                Material m = r.material();
                sb.append("<li>").append(escapeHtml(m.getProductoId())).append(" / ")
                        .append(escapeHtml(m.getNombre())).append("</li>");
            }
            sb.append("</ul>");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private static String tipoMaterialLabel(int tipoMaterial) {
        return switch (tipoMaterial) {
            case 1 -> "Materia prima";
            case 2 -> "Material de empaque";
            default -> "Otro";
        };
    }

    private static String formatQty(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "0";
        }
        if (v == (long) v) {
            return String.valueOf((long) v);
        }
        return String.format(java.util.Locale.US, "%.4f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record MaterialStockRow(Material material, double stock) {}
}
