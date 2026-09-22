package exotic.app.planta.resource.controles;

import exotic.app.planta.service.controles.CodigoPlanDuplicadoException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class ControlPlanCodeExceptionTest {
    private final ControlApiExceptionHandler handler = new ControlApiExceptionHandler();

    @Test
    void duplicadoIndicaCampoYMotivoSinExponerLaCausaSql() {
        var response = handler.codigoDuplicado(new CodigoPlanDuplicadoException(new RuntimeException("SQL interno")));
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("PLAN_CODE_ALREADY_EXISTS", body.errorCode());
        assertEquals("codigo", body.field());
        assertFalse(body.message().contains("SQL interno"));
    }

    @Test
    void otroConflictoNoSePresentaComoCodigoDuplicado() {
        var body = handler.conflicto(new DataIntegrityViolationException("Otra restricción")).getBody();
        assertNotNull(body);
        assertNull(body.errorCode());
        assertNull(body.field());
    }
}
