package exotic.app.planta.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import exotic.app.planta.service.controles.ControlIdempotencyService;


import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.springframework.http.HttpHeaders.*;

@Configuration
public class CorsConfig implements EnvironmentAware {

    private List<String> allowedOrigins = List.of(
            // Local
            "http://localhost:5173",
            "http://localhost:3000",
            // Staging
            "https://exotic-app-frontend-staging.onrender.com",
            "https://exotic-app-backend-staging.onrender.com",
            // Producción
            "https://lac-manufacture-backend.onrender.com",
            "https://lac-manufacture-frontend.onrender.com"
    );

    @Override
    public void setEnvironment(Environment environment) {
        String configuredOrigins = environment.getProperty("app.cors.allowed-origins");
        if (configuredOrigins != null) {
            // An explicit list replaces every default origin, including Render and localhost.
            allowedOrigins = Arrays.stream(configuredOrigins.split(",", -1))
                    .map(String::trim)
                    .map(CorsConfig::validateOrigin)
                    .distinct()
                    .toList();
        }
    }

    private static String validateOrigin(String origin) {
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException exception) {
            // Do not echo configuration values, which may accidentally contain credentials.
            throw invalidOrigins();
        }
        if (origin.contains("*")
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || !"".equals(uri.getRawPath())
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || uri.getPort() == 0
                || uri.getPort() > 65535
                || uri.getRawAuthority().endsWith(":")) {
            throw invalidOrigins();
        }
        return origin;
    }

    private static IllegalArgumentException invalidOrigins() {
        return new IllegalArgumentException("app.cors.allowed-origins must be a non-empty comma-separated list "
                + "of exact HTTP(S) origins without wildcards, credentials, paths, queries or fragments");
    }


    @Bean
    public CorsFilter corsFilter(){

        var urlBasedCorsConfigurationSource = new UrlBasedCorsConfigurationSource();
        var corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.setAllowedOrigins(allowedOrigins);
        corsConfiguration.validateAllowCredentials();

        corsConfiguration.setAllowedHeaders(List.of(
                ORIGIN,
                CONTENT_TYPE,
                ACCEPT,
                AUTHORIZATION,
                IF_NONE_MATCH,
                ControlIdempotencyService.HEADER,
                "x-requested-with",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                "Access-Control-Allow-Origin",
                "Access-Control-Allow-Credentials"
        ));

        corsConfiguration.setExposedHeaders(List.of(
                ORIGIN,
                CONTENT_TYPE,
                ACCEPT,
                AUTHORIZATION,
                ETAG,
                CACHE_CONTROL,
                "x-requested-with",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                "Access-Control-Allow-Origin",
                "Access-Control-Allow-Credentials"
        ));

        corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        urlBasedCorsConfigurationSource.registerCorsConfiguration("/**", corsConfiguration);
        return new CorsFilter(urlBasedCorsConfigurationSource);

    }
}
