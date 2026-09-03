package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.pdf.PdfReader;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordFirmaRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRevisionRepo;
import exotic.app.planta.repo.usuarios.FirmaVisualUsuarioVersionRepo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchRecordPdfServiceTest {

    @Test
    void entregaElPdfPrincipalAlCompositorDeAnexos() throws Exception {
        BatchRecordService batchRecordService = mock(BatchRecordService.class);
        BatchRecordPdfAnnexService annexService = mock(BatchRecordPdfAnnexService.class);
        when(batchRecordService.construirBorradorCanonico(anyLong())).thenReturn("""
                {
                  "esquemaVersion":"batch-record-v4",
                  "codigo":"BR-OP-8",
                  "estado":"BORRADOR",
                  "revisionDocumental":0,
                  "orden":{"tipo":"ORDEN_PRODUCCION","id":8},
                  "lote":{},
                  "producto":{},
                  "manufactura":{},
                  "requerimientosMaterialesJson":"[]",
                  "cantidades":{},
                  "etapas":[],
                  "consumos":[],
                  "controles":[],
                  "controlesUnificados":{"requisitos":[]},
                  "desviaciones":[],
                  "correcciones":[],
                  "decisionesCalidad":[],
                  "ciclosRevision":[],
                  "seccionesCorreccion":[],
                  "solicitudesReapertura":[],
                  "firmas":[]
                }
                """);
        when(annexService.componer(any(byte[].class), any(JsonNode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BatchRecordPdfService service = new BatchRecordPdfService(
                batchRecordService,
                mock(BatchRecordRevisionRepo.class),
                mock(BatchRecordFirmaRepo.class),
                mock(FirmaVisualUsuarioVersionRepo.class),
                new ObjectMapper(),
                annexService);

        BatchRecordPdfService.PdfResult result = service.generar(8L, null, true);

        PdfReader reader = new PdfReader(result.contenido());
        try {
            assertTrue(reader.getNumberOfPages() >= 1);
        } finally {
            reader.close();
        }
        assertEquals("BR-OP-8-borrador.pdf", result.nombreArchivo());
        verify(annexService).componer(any(byte[].class), any(JsonNode.class));
    }
}
