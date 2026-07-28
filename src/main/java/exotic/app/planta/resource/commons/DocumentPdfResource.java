package exotic.app.planta.resource.commons;

import exotic.app.planta.service.commons.DocumentPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

/**
 * Resource for PDF document generation and download.
 */
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentPdfResource {

    private final DocumentPdfService documentPdfService;

    /**
     * Endpoint to download a PDF of a purchase order.
     * 
     * @param ordenCompraId the ID of the purchase order
     * @return a ResponseEntity containing the PDF file for download
     */
    @GetMapping("/orden-compra/{ordenCompraId}/pdf")
    public ResponseEntity<byte[]> downloadOrdenCompraPdf(@PathVariable int ordenCompraId) {
        try {
            byte[] pdfBytes = documentPdfService.generateOrdenCompraPdf(ordenCompraId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "orden-compra-" + ordenCompraId + ".pdf");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
