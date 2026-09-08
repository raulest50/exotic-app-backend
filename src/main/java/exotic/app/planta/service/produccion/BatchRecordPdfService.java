package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.empresa.EmpresaLogoDocumentalVersion;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordRevision;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRevisionRepo;
import exotic.app.planta.service.empresa.EmpresaLogoDocumentalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reconstruye el PDF bajo demanda; nunca persiste el archivo generado. */
@Service
@RequiredArgsConstructor
public class BatchRecordPdfService {

    /** Logo Novum aprobado para representar revisiones v2-v4 sin marca congelada. */
    static final String LEGACY_NOVUM_LOGO_SHA256 =
            "5969022ea9b613c427786b67c388b3d05f617abc15bccd3dda50e84d6e3545d1";

    private final BatchRecordService batchRecordService;
    private final BatchRecordRevisionRepo revisionRepo;
    private final ObjectMapper objectMapper;
    private final BatchRecordPdfAnnexService annexService;
    private final EmpresaLogoDocumentalService empresaLogoDocumentalService;
    private final BatchRecordMainPdfRenderer mainPdfRenderer;

    public record PdfResult(byte[] contenido, String nombreArchivo, boolean borrador) {
    }

    @Transactional(readOnly = true)
    public PdfResult generar(Long batchRecordId, Integer revisionNumero, boolean actual) {
        if (actual && revisionNumero != null) {
            throw new IllegalArgumentException(
                    "Seleccione una revisión emitida o la vista actual, no ambas.");
        }
        String contenidoCanonico;
        String hash;
        String etiquetaRevision;
        boolean borrador;

        BatchRecordRevision revision = actual
                ? null
                : revisionNumero == null
                    ? revisionRepo.findTopByBatchRecord_IdOrderByNumeroDesc(batchRecordId).orElse(null)
                    : batchRecordService.requireRevision(batchRecordId, revisionNumero);
        if (revision == null) {
            contenidoCanonico = batchRecordService.construirBorradorCanonico(batchRecordId);
            hash = null;
            etiquetaRevision = actual
                    ? "VISTA ACTUAL NO CONTROLADA"
                    : "BORRADOR NO CONTROLADO";
            borrador = true;
        } else {
            contenidoCanonico = revision.getContenidoCanonico();
            hash = revision.getContenidoSha256();
            etiquetaRevision = "Revisión " + revision.getNumero()
                    + " · " + revision.getTipo().name();
            borrador = false;
        }

        try {
            JsonNode root = objectMapper.readTree(contenidoCanonico);
            BatchRecordMainPdfRenderer.LogoDocumental logo = resolverLogo(root);
            BatchRecordMainPdfRenderer.RenderContext context =
                    new BatchRecordMainPdfRenderer.RenderContext(
                            etiquetaRevision,
                            hash,
                            borrador,
                            revision,
                            BatchRecordService.PLANTILLA_PDF_VERSION,
                            logo);
            byte[] pdfPrincipal = mainPdfRenderer.render(root, context);
            byte[] pdf = annexService.componer(pdfPrincipal, root);
            String codigo = texto(root.path("codigo"), "batch-record");
            String nombre = codigo.replaceAll("[^A-Za-z0-9._-]", "-")
                    + (borrador ? "-borrador" : "-rev-" + revision.getNumero())
                    + ".pdf";
            return new PdfResult(pdf, nombre, borrador);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "No fue posible reconstruir el PDF del expediente digital.", exception);
        }
    }

    private BatchRecordMainPdfRenderer.LogoDocumental resolverLogo(JsonNode root) {
        JsonNode marca = root.path("marcaDocumental");
        EmpresaLogoDocumentalVersion version;
        String hashEsperado;
        if (marca.path("logoVersionId").canConvertToLong()) {
            version = empresaLogoDocumentalService.getVersion(
                    marca.path("logoVersionId").longValue());
            hashEsperado = texto(marca.path("logoSha256"), "");
        } else {
            version = empresaLogoDocumentalService.getVersionPorSha256(
                    LEGACY_NOVUM_LOGO_SHA256);
            hashEsperado = LEGACY_NOVUM_LOGO_SHA256;
        }

        if (version.getContenido() == null || version.getContenido().length == 0) {
            throw new IllegalStateException(
                    "La versión seleccionada del logo Novum no contiene una imagen.");
        }
        if (hashEsperado.isBlank()
                || version.getSha256() == null
                || !hashEsperado.equalsIgnoreCase(version.getSha256())) {
            throw new IllegalStateException(
                    "La versión del logo documental no coincide con la referencia congelada.");
        }
        return new BatchRecordMainPdfRenderer.LogoDocumental(
                version.getContenido(), version.getVersion(), version.getSha256());
    }

    private String texto(JsonNode node, String fallback) {
        return node == null || node.isMissingNode() || node.isNull()
                ? fallback : node.asText(fallback);
    }
}
