package exotic.app.planta.config;

import exotic.app.planta.config.runtime.ApplicationRuntimeEnvironmentResolver;
import exotic.app.planta.security.JwtAuthenticationFilter;
import exotic.app.planta.security.JwtTokenProvider;
import exotic.app.planta.security.MigrationAuthenticationProvider;
import exotic.app.planta.service.users.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.CorsFilter;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CorsFilter corsFilter;
    private final JwtTokenProvider jwtTokenProvider;
    private final MigrationAuthenticationProvider migrationAuthenticationProvider;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationRuntimeEnvironmentResolver applicationRuntimeEnvironmentResolver;
    private final UserDetailsServiceImpl userDetailsService;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        boolean isDevelopment = applicationRuntimeEnvironmentResolver.isLocal();
        boolean allowDatabasePurge = applicationRuntimeEnvironmentResolver.isLocalOrStaging();

        http
                .cors(withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll();
                    auth.requestMatchers("/api/auth/**").permitAll();

                    if (allowDatabasePurge) {
                        auth.requestMatchers("/api/eliminaciones-forzadas/base-datos").authenticated();
                    } else {
                        auth.requestMatchers("/api/eliminaciones-forzadas/base-datos").denyAll();
                    }

                    if (isDevelopment) {
                        auth.requestMatchers("/api/backend-info/**").permitAll();
                    } else {
                        auth.requestMatchers("/api/backend-info/**").denyAll();
                    }

                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(corsFilter, JwtAuthenticationFilter.class)
                .authenticationProvider(migrationAuthenticationProvider);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }
}
