package exotic.app.planta.config;

import exotic.app.planta.service.controles.ControlIdempotencyService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.HttpHeaders.*;

class CorsConfigTest {
    @Test
    void permiteGuardarControlesDesdeStagingConAutorizacionEIdempotencia() throws Exception {
        for (String path : new String[]{
                "/api/calidad/controles-calidad/ejecuciones",
                "/api/produccion/controles-proceso/ejecuciones"}) {
            var request = new MockHttpServletRequest("OPTIONS", path);
            request.addHeader(ORIGIN, "https://exotic-app-frontend-staging.onrender.com");
            request.addHeader(ACCESS_CONTROL_REQUEST_METHOD, "POST");
            request.addHeader(ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,idempotency-key");
            var response = new MockHttpServletResponse();

            new CorsConfig().corsFilter().doFilter(request, response, new MockFilterChain());

            assertEquals(200, response.getStatus());
            assertEquals("https://exotic-app-frontend-staging.onrender.com", response.getHeader(ACCESS_CONTROL_ALLOW_ORIGIN));
            assertEquals("true", response.getHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS));
            String allowedHeaders = response.getHeader(ACCESS_CONTROL_ALLOW_HEADERS);
            assertNotNull(allowedHeaders);
            assertTrue(allowedHeaders.toLowerCase(Locale.ROOT).contains(ControlIdempotencyService.HEADER.toLowerCase(Locale.ROOT)));
            assertTrue(allowedHeaders.toLowerCase(Locale.ROOT).contains("authorization"));
            assertTrue(allowedHeaders.toLowerCase(Locale.ROOT).contains("content-type"));
        }
    }

    @Test
    void laNuevaCabeceraNoHabilitaOrigenesDesconocidos() throws Exception {
        var request = new MockHttpServletRequest("OPTIONS", "/api/calidad/controles-calidad/ejecuciones");
        request.addHeader(ORIGIN, "https://origen-desconocido.example");
        request.addHeader(ACCESS_CONTROL_REQUEST_METHOD, "POST");
        request.addHeader(ACCESS_CONTROL_REQUEST_HEADERS, "idempotency-key");
        var response = new MockHttpServletResponse();

        new CorsConfig().corsFilter().doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
        assertNull(response.getHeader(ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
