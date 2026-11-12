package exotic.app.planta.service.rehumanos;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfCopy;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;
import exotic.app.planta.model.organizacion.personal.dto.PermisoPersonalDTOs;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Representación del formato FTH-09-19 y anexo íntegro del expediente autorizado. */
@Service
public class PermisoPersonalPdfService {
    private static final String PLANTILLA = "templates/personal/solicitud-permiso-fth-09-19.pdf";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter INSTANTE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final String[] MOTIVOS = {
            "Cita médica / tratamiento médico", "Calamidad doméstica", "Licencia remunerada",
            "Licencia no remunerada", "Atención de asuntos personales",
            "Atención de asuntos asignados por la empresa", "Atención de asuntos académicos propios",
            "Problemas de movilidad", "Día de la familia", "Otros"
    };
    private static final float[] MOTIVO_BASELINES = {528, 512.5f, 498.5f, 483, 467.5f,
            452, 431, 409, 393.5f, 378};

    public byte[] generar(PermisoPersonalDTOs.DocumentoPdf documento) {
        try {
            BaseFont baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false);
            byte[] formato = completarFormato(documento, baseFont);
            byte[] anexo = construirAnexo(documento.detalle(), baseFont);
            return unir(formato, anexo);
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible generar el PDF del permiso laboral.", exception);
        }
    }

    private byte[] completarFormato(PermisoPersonalDTOs.DocumentoPdf documento, BaseFont baseFont)
            throws Exception {
        var d = documento.detalle();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PdfReader reader;
        try (var stream = new ClassPathResource(PLANTILLA).getInputStream()) {
            reader = new PdfReader(stream);
        }
        try {
            PdfStamper stamper = new PdfStamper(reader, output);
            try {
                PdfContentByte canvas = stamper.getOverContent(1);
                texto(canvas, baseFont, 85, 773, 8,
                        d.folio() + "  |  " + d.estado().name() + "  |  Detalle y trazabilidad en anexo");
                campo(canvas, baseFont, fecha(d.fechaSolicitud()), 283, 658, 524, 676);
                campo(canvas, baseFont, d.proceso(), 283, 636.5f, 524, 654.5f);
                campo(canvas, baseFont, d.empleado().nombre(), 283, 615, 524, 633);
                campo(canvas, baseFont, d.empleado().cargo(), 283, 593.5f, 524, 611.5f);
                campo(canvas, baseFont, String.valueOf(d.empleado().id()), 283, 572, 524, 590);
                if (d.motivo() != null) {
                    int motivo = indiceMotivo(d.motivo().name());
                    if (motivo >= 0) texto(canvas, baseFont, 512, MOTIVO_BASELINES[motivo], 11, "X");
                }
                campo(canvas, baseFont, d.descripcion(), 90, 305, 523, 348);
                String soporte = valor(d.documentoSoporte());
                if (d.soporte() != null) soporte += "\nAdjunto: " + d.soporte().nombre();
                campo(canvas, baseFont, soporte, 90, 258, 523, 280);
                campo(canvas, baseFont, d.mecanismoRecuperacion(), 90, 193, 523, 232);
                campo(canvas, baseFont, periodo(d.fechaDesde(), d.fechaHasta()), 318, 166, 524, 189);
                campo(canvas, baseFont, cantidad(d), 318, 151, 524, 164);
                firma(canvas, baseFont, documento.firmaSolicitante(), firmaPorRol(d.firmas(), "SOLICITANTE"),
                        89, 311);
                firma(canvas, baseFont, documento.firmaJefe(), firmaPorRol(d.firmas(), "JEFE"), 318, 523);
                if (documento.firmaJefe() == null && d.motivoDecision() != null) {
                    campo(canvas, baseFont, "Decisión registrada: ver anexo", 320, 66, 521, 105);
                }
                texto(canvas, baseFont, 85, 38, 7,
                        "Registro digital " + d.folio() + " - Estado: " + d.estado().name());
                texto(canvas, baseFont, 85, 27, 7,
                        "La fecha de aprobación del encabezado pertenece al formato FTH-09-19.");
            } finally {
                stamper.close();
            }
        } finally {
            reader.close();
        }
        return output.toByteArray();
    }

    private byte[] construirAnexo(PermisoPersonalDTOs.Detalle d, BaseFont baseFont) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.LETTER, 48, 48, 44, 44);
        PdfWriter.getInstance(document, output);
        document.open();
        try {
            Font normal = new Font(baseFont, 10);
            Font heading = new Font(baseFont, 13, Font.BOLD);
            Paragraph title = new Paragraph("Expediente digital de permiso laboral - " + d.folio(), heading);
            title.setSpacingAfter(14);
            document.add(title);
            PdfPTable table = new PdfPTable(new float[]{1.5f, 3.5f});
            table.setWidthPercentage(100);
            fila(table, normal, "Estado", d.estado().name());
            fila(table, normal, "Fecha de solicitud", fecha(d.fechaSolicitud()));
            fila(table, normal, "Trabajador", d.empleado().nombre());
            fila(table, normal, "Identificación", String.valueOf(d.empleado().id()));
            fila(table, normal, "Cargo", d.empleado().cargo());
            fila(table, normal, "Proceso", d.proceso());
            fila(table, normal, "Jefe asignado", d.jefe() == null ? null : d.jefe().nombre());
            fila(table, normal, "Motivo", d.motivo() == null ? null : etiquetaMotivo(d.motivo().name()));
            fila(table, normal, "Descripción completa", d.descripcion());
            fila(table, normal, "Documento soporte presentado", d.documentoSoporte());
            fila(table, normal, "Recuperación del tiempo", d.mecanismoRecuperacion());
            fila(table, normal, "Fechas del permiso", periodo(d.fechaDesde(), d.fechaHasta()));
            fila(table, normal, "Cantidad declarada", cantidad(d));
            fila(table, normal, "Presentado", instante(d.presentadoEn()));
            fila(table, normal, "Resuelto", instante(d.resueltoEn()));
            fila(table, normal, "Motivo de decisión", d.motivoDecision());
            fila(table, normal, "Motivo de anulación", d.motivoAnulacion());
            fila(table, normal, "SHA-256 del contenido presentado", d.hashContenido());
            if (d.soporte() != null) {
                fila(table, normal, "Archivo soporte", d.soporte().nombre() + "\n"
                        + d.soporte().contentType() + " - " + d.soporte().tamanoBytes() + " bytes");
                fila(table, normal, "SHA-256 del soporte", d.soporte().sha256());
            }
            document.add(table);
            apartado(document, heading, "Firmas del expediente");
            if (d.firmas().isEmpty()) document.add(new Paragraph("Sin firmas registradas.", normal));
            for (var firma : d.firmas()) {
                PdfPTable signed = new PdfPTable(new float[]{1.5f, 3.5f});
                signed.setWidthPercentage(100);
                fila(signed, normal, "Rol", firma.rol());
                fila(signed, normal, "Firmante", firma.nombre() + " (" + firma.username() + ")");
                fila(signed, normal, "Identificación", firma.cedula());
                fila(signed, normal, "Firmado", instante(firma.firmadoEn()));
                fila(signed, normal, "Contenido firmado SHA-256", firma.hashContenido());
                fila(signed, normal, "Imagen de firma SHA-256", firma.sha256());
                document.add(signed);
                document.add(new Paragraph(" ", normal));
            }
            apartado(document, heading, "Historial");
            for (var evento : d.eventos()) {
                Paragraph event = new Paragraph(instante(evento.creadoEn()) + " | " + evento.tipo()
                        + " | " + valor(evento.actorNombre()) + "\n" + valor(evento.detalle()), normal);
                event.setSpacingAfter(9);
                document.add(event);
            }
        } finally {
            document.close();
        }
        return output.toByteArray();
    }

    private static void apartado(Document document, Font font, String text) throws Exception {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setSpacingBefore(16);
        paragraph.setSpacingAfter(9);
        document.add(paragraph);
    }

    private static void fila(PdfPTable table, Font font, String label, String value) {
        for (String text : new String[]{label, valor(value).isBlank() ? "No registrado" : value}) {
            PdfPCell cell = new PdfPCell(new Phrase(text, font));
            cell.setPadding(7);
            cell.setBorderColor(new BaseColor(190, 190, 190));
            table.addCell(cell);
        }
    }

    private static void campo(PdfContentByte canvas, BaseFont font, String value,
                              float left, float bottom, float right, float top) throws Exception {
        String text = valor(value);
        if (text.isBlank()) return;
        ColumnText probe = columna(canvas, font, text, left, bottom, right, top);
        if (ColumnText.hasMoreText(probe.go(true))) text = "Ver contenido completo en anexo";
        columna(canvas, font, text, left, bottom, right, top).go();
    }

    private static ColumnText columna(PdfContentByte canvas, BaseFont font, String text,
                                      float left, float bottom, float right, float top) {
        ColumnText column = new ColumnText(canvas);
        column.setSimpleColumn(new Phrase(text, new Font(font, 8.5f)), left, bottom,
                right, top, 10, Element.ALIGN_LEFT);
        return column;
    }

    private static void texto(PdfContentByte canvas, BaseFont font,
                              float x, float y, float size, String text) {
        canvas.beginText();
        canvas.setFontAndSize(font, size);
        canvas.setTextMatrix(x, y);
        canvas.showText(valor(text));
        canvas.endText();
    }

    private static void firma(PdfContentByte canvas, BaseFont font, byte[] bytes,
                               PermisoPersonalDTOs.Firma firma, float left, float right) throws Exception {
        if (bytes == null || firma == null) return;
        Image image = Image.getInstance(bytes);
        image.scaleToFit(right - left - 12, 29);
        image.setAbsolutePosition(left + (right - left - image.getScaledWidth()) / 2, 81);
        canvas.addImage(image);
        campo(canvas, font, firma.nombre(), left + 3, 70, right - 3, 80);
        texto(canvas, font, left + 3, 63, 7, instante(firma.firmadoEn()));
    }

    private static PermisoPersonalDTOs.Firma firmaPorRol(List<PermisoPersonalDTOs.Firma> firmas, String rol) {
        return firmas.stream().filter(f -> f.rol().equals(rol)
                || ("JEFE".equals(rol) && f.rol().equals("JEFE_INMEDIATO"))).findFirst().orElse(null);
    }

    private static int indiceMotivo(String code) {
        return switch (code) {
            case "CITA_MEDICA", "CITA_TRATAMIENTO_MEDICO" -> 0;
            case "CALAMIDAD_DOMESTICA" -> 1;
            case "LICENCIA_REMUNERADA" -> 2;
            case "LICENCIA_NO_REMUNERADA" -> 3;
            case "ASUNTOS_PERSONALES" -> 4;
            case "DILIGENCIAS_EMPRESA", "ASUNTOS_EMPRESA", "ASUNTOS_ASIGNADOS_EMPRESA" -> 5;
            case "ASUNTOS_ACADEMICOS" -> 6;
            case "MOVILIDAD", "PROBLEMAS_MOVILIDAD" -> 7;
            case "DIA_FAMILIA", "DIA_DE_LA_FAMILIA" -> 8;
            case "OTROS" -> 9;
            default -> -1;
        };
    }

    private static String etiquetaMotivo(String code) {
        int index = indiceMotivo(code);
        return index >= 0 ? MOTIVOS[index] : code.replace('_', ' ');
    }

    private static String periodo(LocalDate desde, LocalDate hasta) {
        return desde == null ? "" : fecha(desde) + (hasta == null || hasta.equals(desde)
                ? "" : " al " + fecha(hasta));
    }

    private static String cantidad(PermisoPersonalDTOs.Detalle d) {
        return d.cantidad() == null ? "" : d.cantidad().stripTrailingZeros().toPlainString()
                + (d.unidad() == null ? "" : " " + d.unidad().name());
    }

    private static String fecha(LocalDate fecha) { return fecha == null ? "" : FECHA.format(fecha); }
    private static String instante(LocalDateTime fecha) { return fecha == null ? "" : INSTANTE.format(fecha); }
    private static String valor(String value) { return value == null ? "" : value; }

    private static byte[] unir(byte[]... documentos) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document();
        PdfCopy copy = new PdfCopy(document, output);
        document.open();
        try {
            for (byte[] bytes : documentos) {
                PdfReader reader = new PdfReader(bytes);
                try {
                    for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                        copy.addPage(copy.getImportedPage(reader, page));
                    }
                    copy.freeReader(reader);
                } finally {
                    reader.close();
                }
            }
        } finally {
            document.close();
        }
        return output.toByteArray();
    }
}
