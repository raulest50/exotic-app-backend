package exotic.app.planta.config;

import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.security.JwtAuthenticationFilter;
import exotic.app.planta.security.JwtTokenProvider;
import exotic.app.planta.security.MigrationAuthenticationProvider;
import exotic.app.planta.service.users.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.*;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.CorsFilter;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserRepository userRepository;
    private final Environment environment;
    private final CorsFilter corsFilter;
    private final JwtTokenProvider jwtTokenProvider;
    private final MigrationAuthenticationProvider migrationAuthenticationProvider;
    private final PasswordEncoder passwordEncoder;

    private final UserDetailsServiceImpl userDetailsService;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Check if we're running in development mode (locally)
        boolean isDevelopment = isDevelopmentEnvironment();

        http
                .cors(withDefaults()) // Enable CORS
                .csrf(AbstractHttpConfigurer::disable)
                // Use stateless session management for JWT
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // Allow preflight (OPTIONS) requests without authentication
                    auth.requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll();

                    // Allow authentication endpoints without authentication
                    auth.requestMatchers("/api/auth/**").permitAll();

                    // For /api/backend-info endpoints:
                    // - In development: allow without authentication
                    // - In production: block access completely
                    if (isDevelopment) {
                        auth.requestMatchers("/api/backend-info/**").permitAll();
                    } else {
                        auth.requestMatchers("/api/backend-info/**").denyAll();
                    }

                    // For all other endpoints, require authentication
                    auth.anyRequest().authenticated();
                })
                // Add JWT filter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                // Add CORS filter before JWT filter
                .addFilterBefore(corsFilter, JwtAuthenticationFilter.class)
                // Use our custom authentication provider for password migration
                .authenticationProvider(migrationAuthenticationProvider);

        return http.build();
    }

    /**
     * Determines if the application is running in a development environment.
     * This is true when running locally and false when running in a Docker container on render.com.
     */
    private boolean isDevelopmentEnvironment() {
        // Check if Spring Boot DevTools is active, which indicates a development environment
        for (String profile : environment.getActiveProfiles()) {
            if (profile.equals("dev")) {
                return true;
            }
        }

        // If no active profiles, check if we're running locally (not in Docker)
        String hostname = System.getenv("HOSTNAME");
        return hostname == null || !hostname.startsWith("render-");
    }


    /**
     * Provide a custom authentication provider that uses our CustomUserDetailsService.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

}
