package com.asalkartechworks.himmat.security;

import com.asalkartechworks.himmat.common.dto.ErrorDetails;
import com.asalkartechworks.himmat.common.dto.ErrorResponse;
import com.asalkartechworks.himmat.common.filter.CorrelationIdHolder;
import com.asalkartechworks.himmat.identity.model.AppUser;
import com.asalkartechworks.himmat.identity.repository.AppUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AppUserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        try {
            if (!jwtService.validateToken(token)) {
                sendUnauthorizedError(response, "Invalid or expired access token.");
                return;
            }

            Long userId = jwtService.extractUserId(token);
            Long tokenVersion = jwtService.extractUserVersion(token);

            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                Optional<AppUser> userOpt = userRepository.findById(userId);

                if (userOpt.isEmpty()) {
                    sendUnauthorizedError(response, "User account not found.");
                    return;
                }

                AppUser user = userOpt.get();

                if (!user.isActive()) {
                    sendUnauthorizedError(response, "User account is inactive.");
                    return;
                }

                // Invalidate tokens if userVersion has changed (due to password change, role change, deactivation)
                if (tokenVersion == null || !tokenVersion.equals(user.getVersion())) {
                    sendUnauthorizedError(response, "Token invalidated due to account status or security update. Please login again.");
                    return;
                }

                UserPrincipal principal = UserPrincipal.create(user);
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

            filterChain.doFilter(request, response);

        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT authentication failed for URI {}: {}", request.getRequestURI(), e.getMessage());
            sendUnauthorizedError(response, "Invalid or expired access token.");
        } catch (Exception e) {
            log.error("Authentication processing error: ", e);
            sendUnauthorizedError(response, "Authentication failed.");
        }
    }

    private void sendUnauthorizedError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(CorrelationIdHolder.HEADER_NAME, CorrelationIdHolder.get());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .error(ErrorDetails.builder()
                        .code("UNAUTHENTICATED")
                        .message(message)
                        .build())
                .requestId(CorrelationIdHolder.get())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
