package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordFirmaRepo;
import exotic.app.planta.repo.usuarios.FirmaVisualUsuarioVersionRepo;
import exotic.app.planta.service.productos.procesos.ProcesoProduccionDocumentoService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchRecordMainPdfRendererTest {

    private static final byte[] TEST_LOGO = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rendersHumanReportAndOmitsTechnicalAuditFields() throws Exception {
        BatchRecordFirmaRepo firmaRepo = mock(BatchRecordFirmaRepo.class);
        FirmaVisualUsuarioVersionRepo visualRepo = mock(FirmaVisualUsuarioVersionRepo.class);
        BatchRecordMainPdfRenderer renderer =
                new BatchRecordMainPdfRenderer(firmaRepo, visualRepo, objectMapper);
        JsonNode root = completeCanonicalRecord();
        BatchRecordMainPdfRenderer.RenderContext context =
                new BatchRecordMainPdfRenderer.RenderContext(
                        "Revisión 4 · CIERRE",
                        "a".repeat(64),
                        false,
                        null,
                        "batch-record-pdf-v5",
                        new BatchRecordMainPdfRenderer.LogoDocumental(
                                qaLogo(), 2, "logo-sha"));

        byte[] pdf = renderer.render(root, context);
        Path qaDirectory = Path.of("build", "reports", "pdf-qa");
        Files.createDirectories(qaDirectory);
        Files.write(qaDirectory.resolve("batch-record-v5-sample.pdf"), pdf);

        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(2);
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                PdfDictionary resources = reader.getPageN(page).getAsDict(PdfName.RESOURCES);
                assertThat(resources.getAsDict(PdfName.XOBJECT))
                        .as("logo o recurso gráfico en la página %s", page)
                        .isNotNull();
            }

            String text = extractText(reader);
            assertThat(text)
                    .contains("EXPEDIENTE DIGITAL DE FABRICACIÓN")
                    .contains("1. Resumen del lote")
                    .contains("2. Conciliación de materiales")
                    .contains("3. Ejecución por etapas")
                    .contains("4. Controles de proceso y calidad")
                    .contains("5. Desviaciones")
                    .contains("6. Correcciones y trazabilidad excepcional")
                    .contains("7. Decisiones de Calidad")
                    .contains("8. Firmas y aprobaciones")
                    .contains("Champú Reparación Intensiva")
                    .contains("Agua purificada")
                    .contains("Mezcla")
                    .contains("Control de pH")
                    .contains("Diferencia")
                    .contains("No representa por sí sola una conclusión de conformidad")
                    .contains("Laura Calidad");
            assertThat(text)
                    .doesNotContain("RAW-JSON-SHOULD-NOT-APPEAR")
                    .doesNotContain("10.1.2.3")
                    .doesNotContain("SECRET-USER-AGENT")
                    .doesNotContain("123456789")
                    .doesNotContain("usuario.interno")
                    .doesNotContain("firma-hash-interno")
                    .doesNotContain("Vendedor Que No Debe Aparecer")
                    .doesNotContain("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        } finally {
            reader.close();
        }
    }

    @Test
    void rendersHistoricalManufacturingOrderWithLegacyControlsAndMissingValues() throws Exception {
        BatchRecordMainPdfRenderer renderer = new BatchRecordMainPdfRenderer(
                mock(BatchRecordFirmaRepo.class),
                mock(FirmaVisualUsuarioVersionRepo.class),
                objectMapper);
        JsonNode root = objectMapper.readTree("""
                {
                  "esquemaVersion":"batch-record-v4",
                  "registradoEn":"2026-08-01T10:00:00",
                  "codigo":"BR-OF-77",
                  "estado":"EN_EJECUCION",
                  "revisionDocumental":2,
                  "orden":{"tipo":"ORDEN_FABRICACION","id":77,"estado":"EN_EJECUCION"},
                  "lote":{"numero":"ST-77","estadoCalidad":"CUARENTENA"},
                  "producto":{"id":"ST-01","nombre":"Base capilar","unidad":"kg"},
                  "manufactura":{"versionNumero":1},
                  "requerimientosMaterialesJson":"[]",
                  "cantidades":{"planificada":50,"obtenida":null,"unidad":"kg"},
                  "etapas":[],
                  "consumos":[],
                  "dispensaciones":[],
                  "controlesUnificados":{"requisitos":[]},
                  "controles":[{
                    "resultado":"CONFORME","areaNombre":"Fabricación","plantillaVersion":1,
                    "fechaRegistro":"2026-08-01T11:00:00",
                    "registradoPor":{"nombre":"Operario Histórico","username":"legacy.secret","cedula":"555"},
                    "muestras":[{"caracteristica":"Inspección visual","numeroMuestra":1,
                      "valorBooleanoEsperado":true,"lecturas":[{"valorBooleano":true}]}]
                  }],
                  "desviaciones":[],"correcciones":[],"ciclosRevision":[],
                  "seccionesCorreccion":[],"solicitudesReapertura":[],
                  "decisionesCalidad":[],"firmas":[]
                }
                """);
        BatchRecordMainPdfRenderer.RenderContext context =
                new BatchRecordMainPdfRenderer.RenderContext(
                        "Revisión 2 · ENVIO_CALIDAD",
                        "b".repeat(64),
                        false,
                        null,
                        "batch-record-pdf-v5",
                        new BatchRecordMainPdfRenderer.LogoDocumental(qaLogo(), 2, "logo-sha"));

        byte[] pdf = renderer.render(root, context);
        PdfReader reader = new PdfReader(pdf);
        try {
            String text = extractText(reader);
            assertThat(text)
                    .contains("Orden de fabricación 77")
                    .contains("Base capilar")
                    .contains("Rendimiento")
                    .contains("No disponible")
                    .contains("Información histórica presentada desde el modelo de controles legado")
                    .contains("Inspección visual")
                    .contains("No se registraron requerimientos ni consumos de materiales")
                    .contains("No se registraron etapas de fabricación")
                    .doesNotContain("legacy.secret")
                    .doesNotContain("555");
        } finally {
            reader.close();
        }
    }

    @Test
    void composesMainReportWithExistingOrderDispensationAndPoeAnnexes() throws Exception {
        BatchRecordMainPdfRenderer renderer = new BatchRecordMainPdfRenderer(
                mock(BatchRecordFirmaRepo.class),
                mock(FirmaVisualUsuarioVersionRepo.class),
                objectMapper);
        JsonNode root = completeCanonicalRecord();
        byte[] mainPdf = renderer.render(root,
                new BatchRecordMainPdfRenderer.RenderContext(
                        "Revisión 4 · CIERRE",
                        "a".repeat(64),
                        false,
                        null,
                        "batch-record-pdf-v5",
                        new BatchRecordMainPdfRenderer.LogoDocumental(qaLogo(), 2, "logo-sha")));
        String poeHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(mainPdf));
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("etapas").get(0).path("poe"))
                .put("sha256", poeHash);

        ProcesoProduccionDocumentoService documentService =
                mock(ProcesoProduccionDocumentoService.class);
        when(documentService.getDescarga(12, 500L)).thenReturn(
                new ProcesoProduccionDocumentoService.DescargaDocumento(
                        new ByteArrayResource(mainPdf),
                        "poe-mezcla.pdf",
                        "application/pdf",
                        (long) mainPdf.length,
                        poeHash));
        BatchRecordPdfAnnexService annexService =
                new BatchRecordPdfAnnexService(documentService, objectMapper);

        byte[] completePdf = annexService.componer(mainPdf, root);
        PdfReader mainReader = new PdfReader(mainPdf);
        PdfReader completeReader = new PdfReader(completePdf);
        try {
            assertThat(completeReader.getNumberOfPages())
                    .isGreaterThan(mainReader.getNumberOfPages());
            String text = extractText(completeReader);
            assertThat(text)
                    .contains("ÍNDICE DE DOCUMENTOS ANEXOS")
                    .contains("ANEXO - ORDEN DE PRODUCCIÓN")
                    .contains("ANEXO - DISPENSACIÓN")
                    .contains("ANEXO - PROCEDIMIENTO OPERATIVO ESTÁNDAR (POE)")
                    .contains("página 1 de ");
        } finally {
            completeReader.close();
            mainReader.close();
        }
    }

    private JsonNode completeCanonicalRecord() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {
                  "esquemaVersion": "batch-record-v5",
                  "registradoEn": "2026-09-07T09:30:00",
                  "codigo": "BR-OP-901",
                  "estado": "CERRADO",
                  "revisionDocumental": 4,
                  "marcaDocumental": {
                    "logoVersionId": 2,
                    "logoVersion": 2,
                    "logoSha256": "logo-sha"
                  },
                  "orden": {
                    "tipo": "ORDEN_PRODUCCION",
                    "id": 901,
                    "estado": "FINALIZADA",
                    "areaOperativa": "Producción",
                    "departamentoOperativo": "Manufactura",
                    "responsable": {"nombre": "Vendedor Que No Debe Aparecer", "cedula": "9988"}
                  },
                  "lote": {
                    "id": 66,
                    "numero": "L-260907",
                    "estadoCalidad": "LIBERADO",
                    "fechaVencimiento": "2028-09-07"
                  },
                  "producto": {
                    "id": "PT-001",
                    "nombre": "Champú Reparación Intensiva",
                    "tipo": "TERMINADO",
                    "unidad": "kg"
                  },
                  "manufactura": {
                    "versionId": 14,
                    "versionNumero": 3,
                    "insumosJson": "RAW-JSON-SHOULD-NOT-APPEAR",
                    "procesoJson": "RAW-PROCESS",
                    "casePackJson": "RAW-PACK"
                  },
                  "cantidades": {"planificada": 100, "obtenida": 96.5, "unidad": "kg"},
                  "iniciadoEn": "2026-09-07T07:00:00",
                  "enviadoRevisionEn": "2026-09-07T13:00:00",
                  "cerradoEn": "2026-09-07T15:30:00",
                  "cicloRevisionActual": 1,
                  "observaciones": "Fabricación completada sin incidentes adicionales.",
                  "etapas": [{
                    "id": 701,
                    "secuencia": 1,
                    "nombre": "Mezcla",
                    "areaNombre": "Fabricación",
                    "estado": "COMPLETADA",
                    "iniciadaEn": "2026-09-07T07:05:00",
                    "completadaEn": "2026-09-07T09:10:00",
                    "reportadaPor": {"id": 81, "username": "operario.1", "nombre": "Ana Operaria", "cedula": "777"},
                    "poe": {"procesoProduccionId": 12, "documentoVersionId": 500,
                      "procesoProduccionNombre": "Mezcla capilar", "version": 8,
                      "nombreArchivo": "poe-mezcla.pdf", "sha256": "poe-hash"}
                  }],
                  "consumos": [
                    {"id": 1, "productoId": "MP-01", "productoNombre": "Agua purificada", "loteOrigen": "AG-1", "cantidad": 8, "unidad": "kg", "movimientoId": 801},
                    {"id": 2, "productoId": "MP-01", "productoNombre": "Agua purificada", "loteOrigen": "AG-1", "cantidad": -1, "unidad": "kg", "movimientoId": 802},
                    {"id": 3, "productoId": "MP-X", "productoNombre": "Ajuste no planificado", "loteOrigen": "AX-2", "cantidad": 0.5, "unidad": "kg", "movimientoId": 803}
                  ],
                  "dispensaciones": [{"transaccionId": 3001}, {"transaccionId": 3002}],
                  "controles": [{"id": 888, "resultado": "NO_CONFORME", "userAgent": "LEGACY-SECRET"}],
                  "controlesUnificados": {"requisitos": [{
                    "id": 900,
                    "planCodigo": "QC-PH",
                    "planNombre": "Control de pH",
                    "versionNumero": 2,
                    "estado": "CONFORME",
                    "ambito": "PROCESO",
                    "etapaNombre": "Mezcla",
                    "puntoAplicacion": "POR_ETAPA",
                    "momento": "DURANTE_PROCESO",
                    "responsableEjecucion": "PRODUCCION",
                    "responsableRevision": "CALIDAD",
                    "caracteristicas": [{
                      "id": 910,
                      "nombre": "pH",
                      "limiteInferior": 5.0,
                      "limiteSuperior": 6.0,
                      "unidadSimbolo": "pH"
                    }],
                    "ejecuciones": [{
                      "id": 920,
                      "fechaRegistro": "2026-09-07T08:15:00",
                      "resultado": "CONFORME",
                      "usuario": {"id": 82, "username": "usuario.interno", "nombre": "Laura Calidad", "cedula": "123456789"},
                      "muestras": [{"id": 930, "caracteristicaId": 910, "numeroMuestra": 1,
                        "lecturas": [{"id": 940, "indiceUnidad": 1, "valorNumerico": 5.4}]}]
                    }],
                    "revalidaciones": [],
                    "desviaciones": []
                  }]},
                  "desviaciones": [{
                    "id": 1001,
                    "codigo": "DES-01",
                    "estado": "CERRADA",
                    "descripcion": "Variación temporal de temperatura",
                    "detectadaEn": "2026-09-07T08:00:00",
                    "detectadaPor": {"nombre": "Laura Calidad", "username": "usuario.interno"},
                    "resolucion": "Sin impacto sobre el lote"
                  }],
                  "correcciones": [{
                    "id": 1100,
                    "corregidaEn": "2026-09-07T10:00:00",
                    "corregidaPor": {"nombre": "Ana Operaria"},
                    "motivo": "Corrección de transcripción",
                    "valorAnterior": "{\\"internalId\\":99}",
                    "valorNuevo": "Dato corregido"
                  }],
                  "ciclosRevision": [{
                    "id": 1200, "numero": 1, "origen": "ENVIO_INICIAL", "estado": "CERRADO",
                    "enviadoEn": "2026-09-07T13:00:00", "enviadoPor": {"nombre": "Ana Operaria"},
                    "cerradoEn": "2026-09-07T15:00:00", "cerradoPor": {"nombre": "Laura Calidad"},
                    "motivoEnvio": "Revisión final"
                  }],
                  "seccionesCorreccion": [],
                  "solicitudesReapertura": [],
                  "decisionesCalidad": [{
                    "id": 1300, "cicloRevision": 1, "decision": "APROBAR", "motivo": "Cumple especificaciones",
                    "decididaEn": "2026-09-07T15:00:00", "decididaPor": {"nombre": "Laura Calidad"},
                    "alcanceDevolucionJson": "{\\"secret\\":true}"
                  }],
                  "firmas": [{
                    "id": 1400, "alcance": "CALIDAD", "decision": "APROBAR",
                    "firmadoEn": "2026-09-07T15:05:00", "nombre": "Laura Calidad", "rol": "JEFE_CALIDAD",
                    "manifestacion": "Apruebo el expediente", "comentario": "Revisión satisfactoria",
                    "username": "usuario.interno", "cedula": "123456789", "ipOrigen": "10.1.2.3",
                    "userAgent": "SECRET-USER-AGENT", "hash": "firma-hash-interno", "algoritmoHash": "SHA-256"
                  }]
                }
                """);
        ((com.fasterxml.jackson.databind.node.ObjectNode) root).put(
                "requerimientosMaterialesJson",
                objectMapper.writeValueAsString(List.of(
                        objectMapper.readTree("""
                                {"productoId":"MP-01","productoNombre":"Agua purificada",
                                 "unidadMedida":"kg","cantidad":10,"inventareable":true,"consumoDirecto":false}
                                """))));
        return root;
    }

    private String extractText(PdfReader reader) throws Exception {
        StringBuilder result = new StringBuilder();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            result.append(PdfTextExtractor.getTextFromPage(reader, page)).append('\n');
        }
        return result.toString();
    }

    private byte[] qaLogo() throws Exception {
        Path officialLogo = Path.of("..", "exotic-app-frontend", "public", "logo_novum.png");
        return Files.exists(officialLogo) ? Files.readAllBytes(officialLogo) : TEST_LOGO;
    }
}
