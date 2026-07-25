package exotic.app.planta.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.config.AppTime;
import exotic.app.planta.security.dto.SecurityErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String ERROR_CODE_ATTRIBUTE =
            RestAuthenticationEntryPoint.class.getName() + ".ERROR_CODE";
    public static final String AUTHENTICATION_REQUIRED = "AUTHENTICATION_REQUIRED";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String INVALID_TOKEN = "INVALID_TOKEN";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException
    ) throws IOException {
        String code = resolveCode(request);
        String message = switch (code) {
            case TOKEN_EXPIRED -> "La sesion expiro.";
            case INVALID_TOKEN -> "El token de autenticacion no es valido.";
            default -> "Se requiere autenticacion.";
        };

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("WWW-Authenticate", buildAuthenticateHeader(code));
        objectMapper.writeValue(
                response.getOutputStream(),
                new SecurityErrorResponse(code, message, AppTime.now(), request.getRequestURI())
        );
    }

    private static String resolveCode(HttpServletRequest request) {
        Object value = request.getAttribute(ERROR_CODE_ATTRIBUTE);
        return value instanceof String code ? code : AUTHENTICATION_REQUIRED;
    }

    private static String buildAuthenticateHeader(String code) {
        return switch (code) {
            case TOKEN_EXPIRED -> "Bearer error=\"invalid_token\", error_description=\"The access token expired\"";
            case INVALID_TOKEN -> "Bearer error=\"invalid_token\", error_description=\"The access token is invalid\"";
            default -> "Bearer";
        };
    }
}
