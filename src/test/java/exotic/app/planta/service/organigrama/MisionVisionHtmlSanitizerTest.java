package exotic.app.planta.service.organigrama;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MisionVisionHtmlSanitizerTest {

    private final MisionVisionHtmlSanitizer sanitizer = new MisionVisionHtmlSanitizer();

    @Test
    void sanitizeRequired_removesDangerousMarkupAndKeepsCorporateFormatting() {
        String sanitized = sanitizer.sanitizeRequired(
                "<p style=\"text-align: center\" onclick=\"alert(1)\"><strong>Mision</strong>"
                        + "<script>alert(2)</script><a href=\"javascript:alert(3)\">enlace</a></p>",
                "La mision"
        );

        assertTrue(sanitized.contains("<strong>Mision</strong>"));
        assertTrue(sanitized.contains("text-align"));
        assertFalse(sanitized.contains("script"));
        assertFalse(sanitized.contains("onclick"));
        assertFalse(sanitized.contains("javascript:"));
    }

    @Test
    void sanitizeRequired_rejectsContentWithoutVisibleText() {
        assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.sanitizeRequired("<p><br></p><script>alert(1)</script>", "La vision")
        );
    }
}
