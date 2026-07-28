package exotic.app.planta.service.commons;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import exotic.app.planta.model.compras.ItemOrdenCompra;
import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.empresa.EmpresaIdentidadLegalVersion;
import exotic.app.planta.model.empresa.EmpresaLogoDocumentalVersion;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import exotic.app.planta.service.empresa.EmpresaIdentidadLegalService;
import exotic.app.planta.service.empresa.EmpresaLogoDocumentalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.NoSuchElementException;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentPdfService {

    private final OrdenCompraRepo ordenCompraRepo;
    private final EmpresaIdentidadLegalService empresaIdentidadLegalService;
    private final EmpresaLogoDocumentalService empresaLogoDocumentalService;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    
    /**
     * Generates a PDF for a purchase order.
     * 
     * @param ordenCompraId the ID of the purchase order
     * @return a byte array containing the PDF file that can be used as an email attachment
     * @throws RuntimeException if the purchase order is not found or if there's an error generating the PDF
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public byte[] generateOrdenCompraPdf(int ordenCompraId) {
        OrdenCompraMateriales orden = ordenCompraRepo.findById(ordenCompraId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe la orden de compra de materiales con id: " + ordenCompraId
                ));
        EmpresaIdentidadLegalVersion identidadLegal = resolveIdentidadLegal(orden);
        EmpresaLogoDocumentalVersion logoDocumental = resolveLogoDocumental(orden);
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // Create a new document
            Document document = new Document(PageSize.A4);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            document.open();
            
            // Add logo
            addLogo(document, logoDocumental, identidadLegal);
            
            // Add title
            addTitle(document, "ORDEN DE COMPRA");
            
            // Add order details
            addOrderDetails(document, orden);
            
            // Add supplier information
            addSupplierInfo(document, orden);
            
            // Add delivery and payment information
            addDeliveryAndPaymentInfo(document, orden, identidadLegal);
            
            // Add items table
            addItemsTable(document, orden);
            
            // Add totals
            addTotals(document, orden);

            // Add observations
            addObservations(document, orden);
            
            document.close();
            
            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Error generating PDF for orden compra: {}", ordenCompraId, e);
            throw new RuntimeException("Error generating PDF for orden compra: " + ordenCompraId, e);
        }
    }
    
    private void addLogo(
            Document document,
            EmpresaLogoDocumentalVersion logoDocumental,
            EmpresaIdentidadLegalVersion identidadLegal
    ) throws DocumentException, IOException {
        byte[] contenido = logoDocumental.getContenido();
        if (contenido == null || contenido.length == 0) {
            throw new IllegalStateException(
                    "La version de logo documental " + logoDocumental.getId() + " no contiene una imagen."
            );
        }

        Image logo = Image.getInstance(contenido);
        logo.scaleToFit(170, 130);
        logo.setAlignment(Element.ALIGN_LEFT);
        document.add(logo);
        
        // Add company information
        Paragraph companyInfo = new Paragraph();
        companyInfo.setAlignment(Element.ALIGN_LEFT);
        
        Font companyFont = new Font(Font.FontFamily.HELVETICA, 9);
        
        companyInfo.add(new Chunk(identidadLegal.getRazonSocial() + "\n", companyFont));
        companyInfo.add(new Chunk(formatIdentificacion(identidadLegal) + "\n", companyFont));
        companyInfo.add(new Chunk("Tel: " + identidadLegal.getTelefonoPrincipal() + "\n", companyFont));
        companyInfo.add(new Chunk(identidadLegal.getEmailPrincipal(), companyFont));
        
        document.add(companyInfo);
        document.add(Chunk.NEWLINE);
    }
    
    private void addTitle(Document document, String title) throws DocumentException {
        Font titleFont = new Font(Font.FontFamily.HELVETICA, 24, Font.BOLD, new BaseColor(231, 38, 141));
        Paragraph titleParagraph = new Paragraph(title, titleFont);
        titleParagraph.setAlignment(Element.ALIGN_CENTER);
        document.add(titleParagraph);
        document.add(Chunk.NEWLINE);
    }
    
    private void addOrderDetails(Document document, OrdenCompraMateriales orden) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        
        Font normalFont = new Font(Font.FontFamily.HELVETICA, 11);
        
        // Order date
        table.addCell(createLabelCell("FECHA EMISION ORDEN DE COMPRA", normalFont));
        table.addCell(createValueCell(orden.getFechaEmision() != null ? 
                orden.getFechaEmision().format(DATE_FORMATTER) : "", normalFont));
        
        // Order number
        table.addCell(createLabelCell("NUMERO DE ORDEN DE COMPRA", normalFont));
        table.addCell(createValueCell(String.valueOf(orden.getOrdenCompraId()), normalFont));
        
        // Expiration date
        table.addCell(createLabelCell("FECHA DE VENCIMIENTO DE LA ORDEN", normalFont));
        table.addCell(createValueCell(orden.getFechaVencimiento() != null ? 
                orden.getFechaVencimiento().format(DATE_FORMATTER) : "", normalFont));
        
        document.add(table);
        document.add(Chunk.NEWLINE);
    }
    
    private void addSupplierInfo(Document document, OrdenCompraMateriales orden) throws DocumentException {
        Font headerFont = new Font(Font.FontFamily.HELVETICA, 14, Font.NORMAL, new BaseColor(242, 220, 219));
        Font normalFont = new Font(Font.FontFamily.HELVETICA, 11);
        
        Paragraph header = new Paragraph("PROVEEDOR", headerFont);
        header.setAlignment(Element.ALIGN_LEFT);
        document.add(header);
        
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(50);
        
        // Supplier name
        table.addCell(createValueCell(orden.getProveedor().getNombre(), normalFont));
        
        // Supplier ID
        table.addCell(createValueCell("NIT: " + orden.getProveedor().getId(), normalFont));
        
        // Supplier department
        if (orden.getProveedor().getDepartamento() != null) {
            table.addCell(createValueCell(orden.getProveedor().getDepartamento(), normalFont));
        }
        
        // Supplier address
        if (orden.getProveedor().getDireccion() != null) {
            table.addCell(createValueCell(orden.getProveedor().getDireccion(), normalFont));
        }
        
        // Supplier city
        if (orden.getProveedor().getCiudad() != null) {
            table.addCell(createValueCell(orden.getProveedor().getCiudad(), normalFont));
        }
        
        // Supplier contact phone
        if (!orden.getProveedor().getContactos().isEmpty() && orden.getProveedor().getContactos().get(0).getCel() != null) {
            table.addCell(createValueCell(orden.getProveedor().getContactos().get(0).getCel(), normalFont));
        }
        
        // Supplier tax regime
        table.addCell(createValueCell(getRegimenTributarioText(orden.getProveedor().getRegimenTributario()), normalFont));
        
        // Supplier contact email
        if (!orden.getProveedor().getContactos().isEmpty() && orden.getProveedor().getContactos().get(0).getEmail() != null) {
            table.addCell(createValueCell(orden.getProveedor().getContactos().get(0).getEmail(), normalFont));
        }
        
        document.add(table);
        document.add(Chunk.NEWLINE);
    }
    
    private void addDeliveryAndPaymentInfo(
            Document document,
            OrdenCompraMateriales orden,
            EmpresaIdentidadLegalVersion identidadLegal
    ) throws DocumentException {
        Font headerFont = new Font(Font.FontFamily.HELVETICA, 14, Font.NORMAL, new BaseColor(242, 220, 219));
        Font normalFont = new Font(Font.FontFamily.HELVETICA, 11);
        
        Paragraph header = new Paragraph("LUGAR DE ENTREGA Y CONDICIONES DE PAGO", headerFont);
        header.setAlignment(Element.ALIGN_LEFT);
        document.add(header);
        
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(50);
        
        table.addCell(createValueCell(
                "Empresa: " + identidadLegal.getRazonSocial() + " - " + identidadLegal.getNombreComercial(),
                normalFont
        ));
        table.addCell(createValueCell("Direccion : vía 11, Juan Mina #4 100", normalFont));
        table.addCell(createValueCell("Barranquilla, Atlántico", normalFont));
        table.addCell(createValueCell(identidadLegal.getTelefonoPrincipal(), normalFont));
        table.addCell(createValueCell(getCondicionPagoText(orden.getCondicionPago()), normalFont));
        table.addCell(createValueCell("PLAZO PAGO " + orden.getPlazoPago() + " DIAS", normalFont));
        table.addCell(createValueCell("PLAZO ENTREGA " + orden.getTiempoEntrega() + " DIAS", normalFont));
        table.addCell(createValueCell("CONDICION ENTREGA: PUESTA EN PLANTA", normalFont));
        
        document.add(table);
        document.add(Chunk.NEWLINE);
    }
    
    private void addItemsTable(Document document, OrdenCompraMateriales orden) throws DocumentException {
        Font headerFont = new Font(Font.FontFamily.HELVETICA, 11, Font.NORMAL, new BaseColor(242, 220, 219));
        Font normalFont = new Font(Font.FontFamily.HELVETICA, 11);
        
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        try {
            table.setWidths(new float[]{1, 3, 1, 2, 2});
        } catch (DocumentException e) {
            log.error("Error setting table widths", e);
        }
        
        // Add table headers
        table.addCell(createHeaderCell("CODIGO", headerFont));
        table.addCell(createHeaderCell("DESCRIPCION", headerFont));
        table.addCell(createHeaderCell("CANTIDAD", headerFont));
        table.addCell(createHeaderCell("PRECIO UNITARIO", headerFont));
        table.addCell(createHeaderCell("SUBTOTAL", headerFont));
        
        // Add items
        for (ItemOrdenCompra item : orden.getItemsOrdenCompra()) {
            table.addCell(createValueCell(String.valueOf(item.getMaterial().getProductoId()), normalFont));
            table.addCell(createValueCell(item.getMaterial().getNombre(), normalFont));
            table.addCell(createValueCell(String.valueOf(item.getCantidad()), normalFont));
            table.addCell(createValueCell(String.valueOf(item.getPrecioUnitario()), normalFont));
            table.addCell(createValueCell(String.valueOf(item.getSubTotal()), normalFont));
        }
        
        document.add(table);
        document.add(Chunk.NEWLINE);
    }
    
    private void addTotals(Document document, OrdenCompraMateriales orden) throws DocumentException {
        Font normalFont = new Font(Font.FontFamily.HELVETICA, 11);
        
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(50);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        table.addCell(createLabelCell("Sub Total", normalFont));
        table.addCell(createValueCell(String.valueOf(orden.getSubTotal()), normalFont));
        
        table.addCell(createLabelCell("Iva 19%", normalFont));
        table.addCell(createValueCell(String.valueOf(orden.getIvaCOP()), normalFont));
        
        table.addCell(createLabelCell("Total Pagar", normalFont));
        table.addCell(createValueCell(String.valueOf(orden.getTotalPagar()), normalFont));
        
        document.add(table);
        document.add(Chunk.NEWLINE);
    }
    
    private void addObservations(Document document, OrdenCompraMateriales orden) throws DocumentException {
        Font headerFont = new Font(Font.FontFamily.HELVETICA, 14, Font.NORMAL, new BaseColor(242, 220, 219));

        Paragraph header = new Paragraph("OBSERVACIONES", headerFont);
        header.setAlignment(Element.ALIGN_LEFT);
        document.add(header);

        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);

        String obs = orden.getObservaciones() != null ? orden.getObservaciones() : "\n";
        PdfPCell cell = new PdfPCell(new Phrase(obs));
        cell.setBorder(Rectangle.BOX);
        table.addCell(cell);

        document.add(table);
    }
    
    private PdfPCell createHeaderCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBackgroundColor(new BaseColor(242, 220, 219));
        cell.setPadding(5);
        return cell;
    }
    
    private PdfPCell createLabelCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        return cell;
    }
    
    private PdfPCell createValueCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        return cell;
    }
    
    private String getCondicionPagoText(String condicionPago) {
        if (condicionPago == null) return "";
        
        switch (condicionPago) {
            case "0":
                return "CONDICION DE PAGO: CREDITO";
            case "1":
                return "CONDICION DE PAGO: CONTADO";
            default:
                return "CONDICION DE PAGO: " + condicionPago;
        }
    }
    
    private String getRegimenTributarioText(int regimenTributario) {
        switch (regimenTributario) {
            case 0:
                return "Régimen Común";
            case 1:
                return "Régimen Simplificado";
            case 2:
                return "Régimen Especial";
            default:
                return String.valueOf(regimenTributario);
        }
    }

    private EmpresaIdentidadLegalVersion resolveIdentidadLegal(OrdenCompraMateriales orden) {
        if (orden.getEmpresaIdentidadLegalVersion() != null) {
            return orden.getEmpresaIdentidadLegalVersion();
        }
        return empresaIdentidadLegalService.getVigente();
    }

    private EmpresaLogoDocumentalVersion resolveLogoDocumental(OrdenCompraMateriales orden) {
        if (orden.getEmpresaLogoDocumentalVersion() != null) {
            return orden.getEmpresaLogoDocumentalVersion();
        }
        return empresaLogoDocumentalService.getVigente();
    }

    private String formatIdentificacion(EmpresaIdentidadLegalVersion identidadLegal) {
        String digitoVerificacion = identidadLegal.getDigitoVerificacion();
        String numero = digitoVerificacion == null || digitoVerificacion.isBlank()
                ? identidadLegal.getNumeroIdentificacion()
                : identidadLegal.getNumeroIdentificacion() + "-" + digitoVerificacion;
        return identidadLegal.getTipoIdentificacion() + ": " + numero;
    }
}
