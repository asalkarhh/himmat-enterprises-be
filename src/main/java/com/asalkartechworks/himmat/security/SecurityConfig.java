package com.asalkartechworks.himmat.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security configuration for Himmat Enterprises:
 * - Stateless Bearer-token authentication with signed JWTs.
 * - CSRF is intentionally disabled: the application uses header-based Bearer JWT authentication
 *   without session cookies, rendering cross-site request forgery attacks inapplicable.
 *   If cookie-based browser sessions or refresh tokens are ever introduced in future phases,
 *   CSRF protection (e.g. SameSite cookies and CSRF tokens) MUST be re-evaluated.
 * - Restrictive configurable CORS policy.
 * - Explicit endpoint authorization rules.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String allowedOrigins;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}")
    private String allowedMethods;

    @Value("${app.cors.allowed-headers:Authorization,Content-Type,X-Request-Id,Idempotency-Key}")
    private String allowedHeaders;

    @Value("${app.cors.allow-credentials:false}")
    private boolean allowCredentials;

    @Value("${app.docs.enabled:true}")
    private boolean docsEnabled;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF disabled because of stateless Bearer tokens in HTTP Authorization headers (no cookies used)
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                    .accessDeniedHandler(customAccessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> {
                // Actuator health check
                auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll();

                // OpenAPI / Swagger endpoints (conditional on docsEnabled)
                if (docsEnabled) {
                    auth.requestMatchers(
                            "/v3/api-docs",
                            "/v3/api-docs/**",
                            "/swagger-ui.html",
                            "/swagger-ui/**"
                    ).permitAll();
                }

                // Public Authentication Endpoints
                auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll();

                // All other /api/v1/** endpoints require authentication by default
                auth.requestMatchers("/api/v1/**").authenticated();

                // Fallback for any other request
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        configuration.setAllowedOrigins(origins);

        List<String> methods = Arrays.stream(allowedMethods.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        configuration.setAllowedMethods(methods);

        List<String> headers = Arrays.stream(allowedHeaders.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        configuration.setAllowedHeaders(headers);

        configuration.setAllowCredentials(allowCredentials);
        configuration.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
