package exotic.app.planta.service.organigrama;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.regex.Pattern;

@Component
public class MisionVisionHtmlSanitizer {

    private static final Pattern ALIGNMENT_STYLE = Pattern.compile(
            "(?i)^\\s*text-align\\s*:\\s*(left|center|right|justify)\\s*;?\\s*$"
    );
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "strong", "em", "u", "ul", "ol", "li", "a")
            .allowUrlProtocols("http", "https", "mailto")
            .allowAttributes("href").onElements("a")
            .allowAttributes("style").matching(ALIGNMENT_STYLE).onElements("p")
            .requireRelNofollowOnLinks()
            .toFactory();

    public String sanitizeRequired(String rawHtml, String fieldLabel) {
        String safeHtml = POLICY.sanitize(rawHtml == null ? "" : rawHtml.trim()).trim();
        if (!hasVisibleText(safeHtml)) {
            throw new IllegalArgumentException(fieldLabel + " no puede estar vacio.");
        }
        return safeHtml;
    }

    private static boolean hasVisibleText(String safeHtml) {
        String withoutTags = HTML_TAG.matcher(safeHtml).replaceAll("");
        String visibleText = HtmlUtils.htmlUnescape(withoutTags)
                .replace('\u00A0', ' ')
                .replace("\u200B", "")
                .trim();
        return !visibleText.isEmpty();
    }
}
