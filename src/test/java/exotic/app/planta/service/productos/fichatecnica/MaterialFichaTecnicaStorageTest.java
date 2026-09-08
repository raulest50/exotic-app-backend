package exotic.app.planta.service.productos.fichatecnica;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialFichaTecnicaStorageTest {

    @TempDir
    Path tempDir;

    private MaterialFichaTecnicaStorage storage;

    @BeforeEach
    void setUp() {
        storage = new MaterialFichaTecnicaStorage(tempDir, "fichas_tecnicas_mp");
    }

    @Test
    void storesValidPdfWithRelativeGeneratedKey() throws Exception {
        byte[] pdf = validPdf();

        String storedReference = storage.store(pdfFile("proveedor.pdf", pdf));

        assertTrue(storedReference.matches("fichas_tecnicas_mp/[0-9a-f-]+\\.pdf"));
        assertTrue(Files.isRegularFile(tempDir.resolve(storedReference)));
        assertArrayEquals(pdf, storage.load(storedReference).orElseThrow().resource().getContentAsByteArray());
    }

    @Test
    void loadsLegacyAbsolutePathOnlyInsideTechnicalSheetRoot() throws Exception {
        byte[] pdf = validPdf();
        Path allowed = tempDir.resolve("fichas_tecnicas_mp/legacy.pdf");
        Files.createDirectories(allowed.getParent());
        Files.write(allowed, pdf);
        Path outside = tempDir.resolve("outside.pdf");
        Files.write(outside, pdf);

        assertTrue(storage.isAvailable(allowed.toAbsolutePath().toString()));
        assertFalse(storage.isAvailable(outside.toAbsolutePath().toString()));
    }

    @Test
    void rejectsExternalAndTraversalReferences() throws Exception {
        byte[] pdf = validPdf();
        Path allowed = tempDir.resolve("fichas_tecnicas_mp/allowed.pdf");
        Files.createDirectories(allowed.getParent());
        Files.write(allowed, pdf);

        assertFalse(storage.isAvailable("https://example.com/ficha.pdf"));
        assertFalse(storage.isAvailable("fichas_tecnicas_mp/../../outside.pdf"));
        assertFalse(storage.isAvailable("allowed.pdf"));
    }

    @Test
    void rejectsEmptyOversizedWrongExtensionAndMalformedFiles() {
        assertThrows(IllegalArgumentException.class, () -> storage.store(pdfFile("empty.pdf", new byte[0])));
        assertThrows(IllegalArgumentException.class, () -> storage.store(pdfFile(
                "large.pdf",
                new byte[(int) MaterialFichaTecnicaStorage.MAX_PDF_SIZE_BYTES + 1]
        )));
        assertThrows(IllegalArgumentException.class, () -> storage.store(pdfFile("document.txt", validPdf())));
        assertThrows(IllegalArgumentException.class, () -> storage.store(pdfFile(
                "broken.pdf",
                "%PDF-not-a-document".getBytes()
        )));
    }

    @Test
    void deletesStoredFile() throws Exception {
        String storedReference = storage.store(pdfFile("proveedor.pdf", validPdf()));

        storage.delete(storedReference);

        assertFalse(Files.exists(tempDir.resolve(storedReference)));
    }

    private static MockMultipartFile pdfFile(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/pdf", content);
    }

    private static byte[] validPdf() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PdfDocument document = new PdfDocument(new PdfWriter(output))) {
            document.addNewPage();
        }
        return output.toByteArray();
    }
}
