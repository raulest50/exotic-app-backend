package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.empresa.EmpresaLogoDocumentalVersion;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordRevision;
import exotic.app.planta.model.produccion.batchrecord.TipoRevisionBatchRecord;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRevisionRepo;
import exotic.app.planta.service.empresa.EmpresaLogoDocumentalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchRecordPdfServiceTest {

    @Mock private BatchRecordService batchRecordService;
    @Mock private BatchRecordRevisionRepo revisionRepo;
    @Mock private BatchRecordPdfAnnexService annexService;
    @Mock private EmpresaLogoDocumentalService logoService;
    @Mock private BatchRecordMainPdfRenderer renderer;

    private BatchRecordPdfService service;

    @BeforeEach
    void setUp() {
        service = new BatchRecordPdfService(
                batchRecordService,
                revisionRepo,
                new ObjectMapper(),
                annexService,
                logoService,
                renderer);
    }

    @Test
    void historicalRevisionUsesApprovedNovumLogoByHash() throws Exception {
        BatchRecordRevision revision = revision("""
                {"esquemaVersion":"batch-record-v4","codigo":"BR-OP-44"}
                """);
        EmpresaLogoDocumentalVersion logo = logo(
                2, BatchRecordPdfService.LEGACY_NOVUM_LOGO_SHA256, new byte[]{1, 2, 3});
        when(revisionRepo.findTopByBatchRecord_IdOrderByNumeroDesc(44L))
                .thenReturn(Optional.of(revision));
        when(logoService.getVersionPorSha256(BatchRecordPdfService.LEGACY_NOVUM_LOGO_SHA256))
                .thenReturn(logo);
        when(renderer.render(any(), any())).thenReturn(new byte[]{4, 5});
        when(annexService.preparar(any())).thenReturn(mock(BatchRecordPdfAnnexService.AnexosPreparados.class));
        when(annexService.componer(any(), any(), any())).thenReturn(new byte[]{6, 7, 8});

        BatchRecordPdfService.PdfResult result = service.generar(44L, null, false);

        assertThat(result.contenido()).containsExactly(6, 7, 8);
        assertThat(result.nombreArchivo()).isEqualTo("BR-OP-44-rev-3.pdf");
        assertThat(result.borrador()).isFalse();
        verify(logoService).getVersionPorSha256(BatchRecordPdfService.LEGACY_NOVUM_LOGO_SHA256);
        verify(logoService, never()).getVersion(any());
        verify(annexService).componer(any(), any(), any());
    }

    @Test
    void emittedRevisionKeepsItsIdentityAndCanonicalContentWithUnavailableAnnexes() throws Exception {
        String canonical = """
                {"codigo":"BR-OP-44","marcaDocumental":{"logoVersionId":2,"logoSha256":"logo"},
                 "firmas":[{"id":91,"hash":"firma-original"}]}
                """;
        BatchRecordRevision revision = revision(canonical);
        when(batchRecordService.requireRevision(44L, 3)).thenReturn(revision);
        when(logoService.getVersion(2L)).thenReturn(logo(2, "logo", new byte[]{1}));
        BatchRecordPdfAnnexService.AnexosPreparados annexes = mock(BatchRecordPdfAnnexService.AnexosPreparados.class);
        when(annexes.numeroIncidencias()).thenReturn(3);
        when(annexService.preparar(any())).thenReturn(annexes);
        when(renderer.render(any(), any())).thenReturn(new byte[]{4});
        when(annexService.componer(any(), any(), eq(annexes))).thenReturn(new byte[]{5});

        BatchRecordPdfService.PdfResult result = service.generar(44L, 3, false);

        ArgumentCaptor<BatchRecordMainPdfRenderer.RenderContext> context =
                ArgumentCaptor.forClass(BatchRecordMainPdfRenderer.RenderContext.class);
        verify(renderer).render(any(), context.capture());
        assertThat(context.getValue().anexosNoDisponibles()).isEqualTo(3);
        assertThat(context.getValue().revision()).isSameAs(revision);
        assertThat(context.getValue().hash()).isEqualTo("a".repeat(64));
        assertThat(revision.getContenidoCanonico()).isEqualTo(canonical);
        assertThat(result.borrador()).isFalse();
        assertThat(result.nombreArchivo()).isEqualTo("BR-OP-44-rev-3.pdf");
        verify(revisionRepo, never()).save(any());
    }

    @Test
    void currentViewKeepsDraftLabelWithUnavailableAnnexes() throws Exception {
        when(batchRecordService.construirBorradorCanonico(44L)).thenReturn("""
                {"codigo":"BR-OP-44","marcaDocumental":{"logoVersionId":2,"logoSha256":"logo"}}
                """);
        when(logoService.getVersion(2L)).thenReturn(logo(2, "logo", new byte[]{1}));
        BatchRecordPdfAnnexService.AnexosPreparados annexes = mock(BatchRecordPdfAnnexService.AnexosPreparados.class);
        when(annexes.numeroIncidencias()).thenReturn(1);
        when(annexService.preparar(any())).thenReturn(annexes);
        when(renderer.render(any(), any())).thenReturn(new byte[]{4});
        when(annexService.componer(any(), any(), eq(annexes))).thenReturn(new byte[]{5});

        BatchRecordPdfService.PdfResult result = service.generar(44L, null, true);

        ArgumentCaptor<BatchRecordMainPdfRenderer.RenderContext> context =
                ArgumentCaptor.forClass(BatchRecordMainPdfRenderer.RenderContext.class);
        verify(renderer).render(any(), context.capture());
        assertThat(context.getValue().etiquetaRevision()).isEqualTo("VISTA ACTUAL NO CONTROLADA");
        assertThat(context.getValue().anexosNoDisponibles()).isEqualTo(1);
        assertThat(result.borrador()).isTrue();
        assertThat(result.nombreArchivo()).isEqualTo("BR-OP-44-borrador.pdf");
    }

    @Test
    void versionFiveUsesFrozenLogoAndRejectsHashMismatch() throws Exception {
        BatchRecordRevision revision = revision("""
                {"esquemaVersion":"batch-record-v5","codigo":"BR-OP-45",
                 "marcaDocumental":{"logoVersionId":22,"logoVersion":5,"logoSha256":"expected"}}
                """);
        when(revisionRepo.findTopByBatchRecord_IdOrderByNumeroDesc(45L))
                .thenReturn(Optional.of(revision));
        when(logoService.getVersion(22L))
                .thenReturn(logo(5, "different", new byte[]{1, 2, 3}));

        assertThatThrownBy(() -> service.generar(45L, null, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No fue posible reconstruir el PDF del expediente digital.")
                .hasRootCauseMessage(
                        "La versión del logo documental no coincide con la referencia congelada.");
        verify(logoService).getVersion(22L);
        verify(renderer, never()).render(any(), any());
    }

    private BatchRecordRevision revision(String content) {
        BatchRecordRevision revision = new BatchRecordRevision();
        revision.setId(70L);
        revision.setNumero(3);
        revision.setTipo(TipoRevisionBatchRecord.CIERRE);
        revision.setContenidoCanonico(content);
        revision.setContenidoSha256("a".repeat(64));
        return revision;
    }

    private EmpresaLogoDocumentalVersion logo(int version, String sha256, byte[] content) {
        EmpresaLogoDocumentalVersion logo = new EmpresaLogoDocumentalVersion();
        logo.setId((long) version);
        logo.setVersion(version);
        logo.setSha256(sha256);
        logo.setContenido(content);
        return logo;
    }
}
