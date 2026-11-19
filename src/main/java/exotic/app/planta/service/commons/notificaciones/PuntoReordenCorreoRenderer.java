package exotic.app.planta.service.commons.notificaciones;

import exotic.app.planta.model.commons.notificaciones.PuntoReordenCorreoData;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Presenta el snapshot del correo con un motor dedicado, independiente de MVC y SMTP. */
@Component
public class PuntoReordenCorreoRenderer {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale LOCALE = Locale.forLanguageTag("es-CO");
    private static final String LOGO_CONTENT_ID = "novum-logo";

    private final TemplateEngine templateEngine;

    public PuntoReordenCorreoRenderer() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/email/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCheckExistence(true);
        resolver.setCacheable(true);

        templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
    }

    public String render(PuntoReordenCorreoData data) {
        Objects.requireNonNull(data, "El modelo del correo es obligatorio.");
        Context context = new Context(LOCALE);
        context.setVariable("correo", toView(data));
        return templateEngine.process("punto-reorden", context);
    }

    /** Same PNG used by the login, packaged with the email for CID display. */
    public Map<String, Resource> inlineImages() {
        Resource logo = new ClassPathResource("email/assets/logo-novum.png");
        if (!logo.exists() || !logo.isReadable()) {
            throw new IllegalStateException("No se encontró el logo de Novum incluido en el correo.");
        }
        return Map.of(LOGO_CONTENT_ID, logo);
    }

    private static Map<String, Object> toView(PuntoReordenCorreoData data) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("logoSrc", "cid:" + LOGO_CONTENT_ID);
        view.put("fechaCorte", formatDate(data.fechaCorte()));
        view.put("identidad", Map.of(
                "nombreComercial", text(data.identidad().nombreComercial()),
                "razonSocial", text(data.identidad().razonSocial()),
                "telefono", text(data.identidad().telefono()),
                "email", text(data.identidad().email())
        ));
        view.put("totalEnAlerta", data.totalEnAlerta());
        view.put("pendientesOrdenar", data.pendientesOrdenar().stream()
                .map(PuntoReordenCorreoRenderer::materialView).toList());
        view.put("pendientesIngresoAlmacen", data.pendientesIngresoAlmacen().stream()
                .map(PuntoReordenCorreoRenderer::materialConOcmView).toList());
        view.put("sinPuntoReorden", data.sinPuntoReorden().stream()
                .map(PuntoReordenCorreoRenderer::materialView).toList());
        view.put("seguimiento", data.seguimiento().stream()
                .map(PuntoReordenCorreoRenderer::seguimientoView).toList());
        view.put("vencidas", data.vencidas().stream()
                .map(PuntoReordenCorreoRenderer::seguimientoView).toList());
        view.put("materialesEnSeguimiento", distinctMaterialCount(data.seguimiento()));
        view.put("materialesConPlazoSuperado", distinctMaterialCount(data.vencidas()));
        view.put("paresSinDatosSeguimiento", data.paresSinDatosSeguimiento());
        view.put("porcentajeSeguimiento", data.porcentajeSeguimiento());
        view.put("minimoLeadTimeDias", data.minimoLeadTimeDias());
        return view;
    }

    private static Map<String, Object> materialView(PuntoReordenCorreoData.MaterialFila material) {
        return Map.of(
                "productoId", text(material.productoId()),
                "nombre", text(material.nombre()),
                "tipoMaterialLabel", text(material.tipoMaterialLabel()),
                "stockActual", formatQuantity(material.stockActual()),
                "puntoReorden", formatQuantity(material.puntoReorden()),
                "tipoUnidades", text(material.tipoUnidades())
        );
    }

    private static Map<String, Object> materialConOcmView(PuntoReordenCorreoData.MaterialConOcmFila fila) {
        return Map.of(
                "material", materialView(fila.material()),
                "ocms", fila.ocms().stream().map(ocm -> Map.of(
                        "ordenCompraId", ocm.ordenCompraId(),
                        "fechaEmision", formatDateTime(ocm.fechaEmision())
                )).toList()
        );
    }

    private static Map<String, Object> seguimientoView(PuntoReordenCorreoData.SeguimientoFila fila) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("material", materialView(fila.material()));
        view.put("ordenCompraId", fila.ordenCompraId());
        view.put("proveedor", text(fila.proveedor()));
        view.put("fechaEnvio", formatDate(fila.fechaEnvio()));
        view.put("fechaLimite", formatDate(fila.fechaLimite()));
        view.put("leadTimeDias", fila.leadTimeDias());
        view.put("diasTranscurridos", fila.diasTranscurridos());
        view.put("diasExceso", fila.diasExceso());
        view.put("cantidadPendiente", formatQuantity(fila.cantidadPendiente()));
        return view;
    }

    private static long distinctMaterialCount(List<PuntoReordenCorreoData.SeguimientoFila> filas) {
        return filas.stream().map(fila -> fila.material().productoId()).distinct().count();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String formatDate(LocalDate value) {
        return value == null ? "Sin fecha" : FECHA.format(value);
    }

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? "Sin fecha de emisión" : FECHA_HORA.format(value);
    }

    private static String formatQuantity(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }
}
