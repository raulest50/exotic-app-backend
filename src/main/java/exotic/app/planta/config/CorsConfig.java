package exotic.app.planta.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;


import java.util.List;

import static org.springframework.http.HttpHeaders.*;

@Configuration
public class CorsConfig {


    @Bean
    public CorsFilter corsFilter(){

        var urlBasedCorsConfigurationSource = new UrlBasedCorsConfigurationSource();
        var corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.setAllowedOrigins(List.of(
            // Local
            "http://localhost:5173",
            "http://localhost:3000",
            // Staging
            "https://lac-manufacture-frontend-staging.onrender.com",
            "https://lac-manufacture-backend-staging.onrender.com",
            "https://exotic-app-frontend-staging.onrender.com",
            // Producción
            "https://lac-manufacture-backend.onrender.com",
            "https://lac-manufacture-frontend.onrender.com"
        ));

        corsConfiguration.setAllowedHeaders(List.of(
                ORIGIN,
                CONTENT_TYPE,
                ACCEPT,
                AUTHORIZATION,
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
