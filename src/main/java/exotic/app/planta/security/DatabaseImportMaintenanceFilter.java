package exotic.app.planta.security;

import exotic.app.planta.service.commons.DatabaseImportMaintenanceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/** Keeps the import receipt readable without allowing business queries into a replaced schema. */
public class DatabaseImportMaintenanceFilter extends OncePerRequestFilter {
    private static final Pattern RECEIPT = Pattern.compile(
            "/api/importacion-datos/backup-total(?:-v2)?/jobs/[a-fA-F0-9-]{36}");
    private final DatabaseImportMaintenanceService maintenance;

    public DatabaseImportMaintenanceFilter(DatabaseImportMaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        String path = request.getServletPath();
        if ("OPTIONS".equals(request.getMethod())
                || ("GET".equals(request.getMethod()) && RECEIPT.matcher(path).matches())) {
            chain.doFilter(request, response);
            return;
        }
        try (var access = maintenance.tryEnterWork()) {
            if (access == null) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setHeader("Retry-After", "5");
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"code\":\"DATABASE_IMPORT_MAINTENANCE\","
                        + "\"message\":\"Base de datos en mantenimiento por importacion total. "
                        + "Consulte el resultado de la importacion antes de continuar.\"}");
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
