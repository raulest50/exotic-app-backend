package exotic.app.planta.service.organigrama;

import exotic.app.planta.model.organigrama.MisionVisionValor;
import exotic.app.planta.model.organigrama.MisionVisionVersion;
import exotic.app.planta.model.organigrama.dto.MisionVisionRestoreRequest;
import exotic.app.planta.model.organigrama.dto.MisionVisionValorRequest;
import exotic.app.planta.model.organigrama.dto.MisionVisionVersionRequest;
import exotic.app.planta.model.organigrama.dto.MisionVisionVersionResponse;
import exotic.app.planta.repo.organigrama.MisionVisionVersionRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CONFLICT;

class MisionVisionServiceTest {

    private MisionVisionVersionRepo repo;
    private MisionVisionService service;

    @BeforeEach
    void setUp() {
        repo = mock(MisionVisionVersionRepo.class);
        service = new MisionVisionService(repo, new MisionVisionHtmlSanitizer());
        when(repo.save(any(MisionVisionVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void crearNuevaVersion_retiresCurrentSanitizesContentAndPreservesOrder() {
        MisionVisionVersion current = version(1L, 1, MisionVisionVersion.Estado.VIGENTE);
        when(repo.findByEstadoForUpdate(MisionVisionVersion.Estado.VIGENTE)).thenReturn(Optional.of(current));
        when(repo.findMaxVersion()).thenReturn(1);

        MisionVisionVersionRequest request = validRequest();
        request.setMisionHtml("<p><strong>Nueva mision</strong><script>alert(1)</script></p>");

        MisionVisionVersionResponse created = service.crearNuevaVersion(request, " editor ");

        assertEquals(MisionVisionVersion.Estado.RETIRADA, current.getEstado());
        assertNotNull(current.getVigenteHasta());
        assertEquals(2, created.version());
        assertEquals(MisionVisionVersion.Estado.VIGENTE, created.estado());
        assertEquals("editor", created.creadoPor());
        assertEquals("Actualizacion corporativa", created.motivoCambio());
        assertEquals(List.of("Integridad", "Excelencia"), created.valores().stream().map(value -> value.titulo()).toList());
        assertFalse(created.misionHtml().contains("script"));
        verify(repo).save(current);
    }

    @Test
    void crearNuevaVersion_rejectsStaleBaseVersionWithoutWriting() {
        MisionVisionVersion current = version(1L, 3, MisionVisionVersion.Estado.VIGENTE);
        when(repo.findByEstadoForUpdate(MisionVisionVersion.Estado.VIGENTE)).thenReturn(Optional.of(current));

        MisionVisionVersionRequest request = validRequest();
        request.setVersionBase(2);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.crearNuevaVersion(request, "editor")
        );

        assertEquals(CONFLICT, exception.getStatusCode());
        verify(repo, never()).save(any(MisionVisionVersion.class));
    }

    @Test
    void restaurarVersion_createsNewVersionAndRecordsOrigin() {
        MisionVisionVersion source = version(5L, 2, MisionVisionVersion.Estado.RETIRADA);
        source.setMisionHtml("<p>Mision anterior</p>");
        source.setVisionHtml("<p>Vision anterior</p>");
        addValue(source, 0, "Integridad", "<p>Descripcion anterior</p>");

        MisionVisionVersion current = version(8L, 4, MisionVisionVersion.Estado.VIGENTE);
        when(repo.findById(5L)).thenReturn(Optional.of(source));
        when(repo.findByEstadoForUpdate(MisionVisionVersion.Estado.VIGENTE)).thenReturn(Optional.of(current));
        when(repo.findMaxVersion()).thenReturn(4);

        MisionVisionRestoreRequest request = new MisionVisionRestoreRequest();
        request.setVersionBase(4);
        request.setMotivoCambio("Recuperar contenido aprobado");

        MisionVisionVersionResponse restored = service.restaurarVersion(5L, request, "admin");

        assertEquals(5, restored.version());
        assertEquals(2, restored.origenVersion());
        assertEquals("Mision anterior", restored.misionHtml().replace("<p>", "").replace("</p>", ""));
        assertSame(source, capturedNewVersionOrigin());
    }

    private MisionVisionVersion capturedNewVersionOrigin() {
        org.mockito.ArgumentCaptor<MisionVisionVersion> captor = org.mockito.ArgumentCaptor.forClass(MisionVisionVersion.class);
        verify(repo, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream()
                .filter(value -> value.getEstado() == MisionVisionVersion.Estado.VIGENTE)
                .findFirst()
                .orElseThrow()
                .getOrigenVersion();
    }

    private static MisionVisionVersionRequest validRequest() {
        MisionVisionVersionRequest request = new MisionVisionVersionRequest();
        request.setVersionBase(1);
        request.setMisionHtml("<p>Mision</p>");
        request.setVisionHtml("<p>Vision</p>");
        request.setValores(List.of(
                valueRequest(" Integridad ", "<p>Actuamos correctamente</p>"),
                valueRequest("Excelencia", "<p>Buscamos calidad</p>")
        ));
        request.setMotivoCambio(" Actualizacion corporativa ");
        return request;
    }

    private static MisionVisionValorRequest valueRequest(String title, String description) {
        MisionVisionValorRequest request = new MisionVisionValorRequest();
        request.setTitulo(title);
        request.setDescripcionHtml(description);
        return request;
    }

    private static MisionVisionVersion version(
            Long id,
            int number,
            MisionVisionVersion.Estado status
    ) {
        MisionVisionVersion version = new MisionVisionVersion();
        version.setId(id);
        version.setVersion(number);
        version.setEstado(status);
        version.setMisionHtml("<p>Mision</p>");
        version.setVisionHtml("<p>Vision</p>");
        return version;
    }

    private static void addValue(
            MisionVisionVersion version,
            int order,
            String title,
            String description
    ) {
        MisionVisionValor value = new MisionVisionValor();
        value.setMisionVisionVersion(version);
        value.setOrden(order);
        value.setTitulo(title);
        value.setDescripcionHtml(description);
        version.getValores().add(value);
    }
}
